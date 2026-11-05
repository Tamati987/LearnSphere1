"""Mini-studio d'animé — 100 % gratuit.

À partir d'un script JSON (personnages + scènes), fabrique un court animé :
  * une image IA par scène (Pollinations.ai), avec description des
    personnages réinjectée à chaque scène pour plus de cohérence ;
  * une voix différente par personnage (edge-tts) ;
  * mouvements de caméra : zoom_in, zoom_out, pan_gauche, pan_droite,
    pan_haut, tremblement ;
  * rendu « film » : grain, vignette, couleurs renforcées ;
  * sous-titres avec le nom du personnage qui parle ;
  * musique originale générée par le programme + bruitages (whoosh, tonnerre).

Utilisation :
    python -m outils.anime script.json -o anime.mp4 [--cache dossier_images]
Voir exemples/hina.json pour le format du script.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import shutil
import subprocess
import tempfile
import textwrap
import wave
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from .images_ia import generer_image
from .video import FORMATS, FPS, _police, _srt_temps
from .voix import duree_audio, ffmpeg_exe, generer_voix

SR = 44100
STYLE_ANIME = ("anime key visual, cel shading, vibrant colors, detailed painted background, "
               "dramatic cinematic lighting, beautiful anime film style, no text, no letters")


# --------------------------------------------------------------------------- #
# Audio généré : musique et bruitages
# --------------------------------------------------------------------------- #
def _note(n: int) -> float:
    """Fréquence d'une note MIDI."""
    return 440.0 * 2 ** ((n - 69) / 12)


def composer_musique(duree: float, bpm: int = 78, graine: int = 7) -> np.ndarray:
    """Musique d'ambiance épique et douce (la mineur), en stéréo float32."""
    rng = np.random.default_rng(graine)
    n = int(duree * SR)
    out = np.zeros((n, 2), dtype=np.float32)
    temps = 60 / bpm
    mesure = 4 * temps
    accords = [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]]  # Am F C G
    t_mes = np.arange(int(mesure * SR)) / SR

    def placer(signal, debut, pan=0.0):
        i = int(debut * SR)
        if i >= n:
            return
        s = signal[: n - i]
        out[i:i + len(s), 0] += s * (1 - pan) * 0.5
        out[i:i + len(s), 1] += s * (1 + pan) * 0.5

    m = 0
    while m * mesure < duree:
        acc = accords[m % 4]
        # Nappe : sinus + harmonique douce, attaque et relâche lentes
        env = np.minimum(1, t_mes / 1.2) * np.minimum(1, (mesure - t_mes) / 0.8)
        nappe = sum(
            np.sin(2 * np.pi * _note(k) * t_mes) + 0.3 * np.sin(2 * np.pi * _note(k) * 2.003 * t_mes)
            for k in acc
        ) * env * 0.05
        placer(nappe, m * mesure)
        # Basse
        basse = np.sin(2 * np.pi * _note(acc[0] - 12) * t_mes) * env * 0.09
        placer(basse, m * mesure)
        # Arpège pincé en croches (à partir de la 2e mesure)
        if m >= 1:
            motif = [acc[0] + 12, acc[1] + 12, acc[2] + 12, acc[1] + 12]
            for k in range(8):
                tn = np.arange(int(temps * 1.5 * SR)) / SR
                hauteur = motif[k % 4] + (12 if k == 6 else 0)
                pince = np.sin(2 * np.pi * _note(hauteur) * tn) * np.exp(-tn * 5) * 0.045
                placer(pince, m * mesure + k * temps / 2, pan=0.4 if k % 2 else -0.4)
        # Timbale douce sur les temps 1 et 3 (à partir de la 3e mesure)
        if m >= 2:
            for b in (0, 2):
                tk = np.arange(int(0.4 * SR)) / SR
                f = 70 * np.exp(-tk * 8) + 40
                kick = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tk * 9) * 0.12
                placer(kick, m * mesure + b * temps)
        m += 1
    # Légère « respiration » stéréo et fondus
    out += rng.normal(0, 0.0015, out.shape).astype(np.float32)
    fade = min(n, int(2.5 * SR))
    out[:int(1.5 * SR)] *= np.linspace(0, 1, min(n, int(1.5 * SR)))[:, None]
    out[n - fade:] *= np.linspace(1, 0, fade)[:, None]
    return out


def bruit_whoosh(duree: float = 0.7) -> np.ndarray:
    t = np.arange(int(duree * SR)) / SR
    bruit = np.random.default_rng(1).normal(0, 1, len(t))
    # filtre passe-bas glissant (moyenne mobile de taille variable)
    lisse = np.convolve(bruit, np.ones(12) / 12, mode="same")
    env = np.sin(np.pi * t / duree) ** 2
    return (lisse * env * 0.35).astype(np.float32)


def bruit_tonnerre(duree: float = 2.5) -> np.ndarray:
    t = np.arange(int(duree * SR)) / SR
    bruit = np.random.default_rng(3).normal(0, 1, len(t))
    grave = np.convolve(bruit, np.ones(120) / 120, mode="same")
    env = np.exp(-t * 1.6) * (1 + 0.6 * np.sin(2 * np.pi * 3 * t))
    craquement = np.convolve(bruit, np.ones(4) / 4, mode="same") * np.exp(-t * 18) * 0.5
    return ((grave * 4 + craquement) * env * 0.5).astype(np.float32)


def ecrire_wav(chemin: Path, signal: np.ndarray):
    if signal.ndim == 1:
        signal = np.stack([signal, signal], axis=1)
    data = (np.clip(signal, -1, 1) * 32767).astype(np.int16)
    with wave.open(str(chemin), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


# --------------------------------------------------------------------------- #
# Image : calque de texte façon animé
# --------------------------------------------------------------------------- #
def _hex(c: str) -> tuple:
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def calque_texte(chemin: Path, taille, texte: str = "", nom: str = "", couleur="#ffffff",
                 titre: str = "", sous_titre: str = ""):
    W, H = taille
    base = min(W, H)
    img = Image.new("RGBA", taille, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if titre:
        f = _police(int(base * 0.12))
        lignes = textwrap.wrap(titre, width=12)
        lh = int(base * 0.14)
        y = int(H * 0.36) - lh * len(lignes) // 2
        for l in lignes:
            x = (W - f.getlength(l)) // 2
            d.text((x, y), l, font=f, fill=(255, 255, 255, 255),
                   stroke_width=max(4, base // 110), stroke_fill=(10, 20, 40, 255))
            y += lh
        if sous_titre:
            f2 = _police(int(base * 0.05))
            x = (W - f2.getlength(sous_titre)) // 2
            d.text((x, y + base * 0.02), sous_titre, font=f2, fill=_hex(couleur) + (255,),
                   stroke_width=max(3, base // 180), stroke_fill=(10, 20, 40, 255))
    if texte:
        fs = int(base * 0.052)
        f = _police(fs)
        lignes = textwrap.wrap(texte, width=max(10, int(W * 0.84 / (fs * 0.55))))
        lh = int(fs * 1.28)
        y = int(H * 0.9) - lh * len(lignes)
        # dégradé sombre discret derrière les sous-titres
        haut = y - int(fs * (2.2 if nom else 1.2))
        for k in range(H - haut):
            a = int(150 * min(1, k / max(1, (H - haut) * 0.5)))
            d.line([(0, haut + k), (W, haut + k)], fill=(0, 0, 0, a))
        if nom:
            fn = _police(int(fs * 0.85))
            d.text(((W - fn.getlength(nom)) // 2, y - int(fs * 1.25)), nom, font=fn,
                   fill=_hex(couleur) + (255,), stroke_width=max(2, fs // 14), stroke_fill=(0, 0, 0, 255))
        for l in lignes:
            d.text(((W - f.getlength(l)) // 2, y), l, font=f, fill=(255, 255, 255, 255),
                   stroke_width=max(3, fs // 11), stroke_fill=(0, 0, 0, 255))
            y += lh
    img.save(chemin)


# --------------------------------------------------------------------------- #
# Caméra
# --------------------------------------------------------------------------- #
def expr_camera(camera: str, n: int) -> tuple[str, str, str]:
    c = "iw/2-(iw/zoom/2)", "ih/2-(ih/zoom/2)"
    p = f"on/{n}"
    if camera == "zoom_out":
        return f"1.2-0.2*{p}", *c
    if camera == "pan_droite":
        return "1.2", f"(iw-iw/zoom)*{p}", c[1]
    if camera == "pan_gauche":
        return "1.2", f"(iw-iw/zoom)*(1-{p})", c[1]
    if camera == "pan_haut":
        return "1.2", c[0], f"(ih-ih/zoom)*(1-{p})"
    if camera == "tremblement":
        return (f"1.15+0.05*{p}", f"iw/2-(iw/zoom/2)+18*sin(on*1.9)", f"ih/2-(ih/zoom/2)+14*cos(on*2.7)")
    return f"1+0.2*{p}", *c  # zoom_in par défaut


# --------------------------------------------------------------------------- #
# Fabrication
# --------------------------------------------------------------------------- #
def _ff(*args):
    subprocess.run([ffmpeg_exe(), "-y", "-hide_banner", "-loglevel", "error", *args], check=True)


def creer_anime(script: dict, sortie: str | Path, cache: str | Path | None = None, journal=print) -> Path:
    persos = script.get("personnages", {})
    style = script.get("style", STYLE_ANIME)
    W, H = FORMATS.get(script.get("format", "portrait"), FORMATS["portrait"])
    iw, ih = (576, 1024) if W < H else (1024, 576) if W > H else (768, 768)
    sortie = Path(sortie)
    sortie.parent.mkdir(parents=True, exist_ok=True)
    cache = Path(cache) if cache else None
    if cache:
        cache.mkdir(parents=True, exist_ok=True)
    scenes = script["scenes"]

    def decrire(prompt: str) -> str:
        for nom, p in persos.items():
            if p.get("description"):
                prompt = re.sub(r"\{" + re.escape(nom) + r"\}", p["description"], prompt)
        return f"{prompt}, {style}"

    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        clips, srt, debuts, t = [], [], [], 0.0
        for i, sc in enumerate(scenes, 1):
            prompt = decrire(sc["image"])
            graine = sc.get("graine", 100 + i)
            cle = hashlib.sha1(f"{prompt}|{graine}|{iw}x{ih}".encode()).hexdigest()[:16]
            photo = (cache or tmp) / f"{cle}.jpg"
            if not photo.exists():
                journal(f"[{i}/{len(scenes)}] image IA…")
                generer_image(prompt, photo, iw, ih, seed=graine)
            # Voix
            perso = persos.get(sc.get("personnage", ""), {})
            texte = sc.get("texte", "")
            audio_in = ["-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo"]
            if texte and perso.get("voix"):
                mp3 = tmp / f"v{i:03}.mp3"
                generer_voix(texte, mp3, perso["voix"], vitesse=perso.get("vitesse", 0),
                             hauteur=perso.get("hauteur", 0))
                duree = duree_audio(mp3) + sc.get("pause", 0.45)
                audio_in = ["-i", str(mp3)]
            else:
                duree = float(sc.get("duree", 3))
            duree = max(duree, float(sc.get("duree_min", 0)))
            # Texte
            calque = tmp / f"t{i:03}.png"
            nom = "" if sc.get("personnage") in (None, "", "Narrateur") else sc["personnage"]
            calque_texte(calque, (W, H), texte if script.get("sous_titres", True) else "", nom,
                         perso.get("couleur", "#ffffff"), sc.get("titre", ""), sc.get("sous_titre", ""))
            # Clip
            n = max(1, int(duree * FPS))
            z, x, y = expr_camera(sc.get("camera", "zoom_in"), n)
            fondu = min(0.3, duree / 5)
            clip = tmp / f"c{i:03}.mp4"
            _ff(
                "-loop", "1", "-framerate", str(FPS), "-i", str(photo),
                "-loop", "1", "-framerate", str(FPS), "-i", str(calque), *audio_in,
                "-t", f"{duree:.3f}",
                "-filter_complex",
                f"[0:v]scale={2 * W}:{2 * H}:force_original_aspect_ratio=increase,crop={2 * W}:{2 * H},"
                f"zoompan=z='{z}':x='{x}':y='{y}':d=1:s={W}x{H}:fps={FPS},setsar=1,"
                f"eq=saturation=1.18:contrast=1.06,vignette=PI/5,noise=alls=4:allf=t[bg];"
                f"[bg][1:v]overlay=format=auto,"
                f"fade=t=in:st=0:d={fondu},fade=t=out:st={duree - fondu:.3f}:d={fondu},format=yuv420p[v];"
                f"[2:a]apad[a]",
                "-map", "[v]", "-map", "[a]",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-maxrate", "6M", "-bufsize", "12M", "-r", str(FPS),
                "-pix_fmt", "yuv420p", "-color_range", "tv",
                "-c:a", "aac", "-b:a", "160k", "-ar", "44100", "-ac", "2",
                str(clip),
            )
            clips.append(clip)
            debuts.append(t)
            if texte:
                qui = f"{nom} : " if nom else ""
                srt.append(f"{len(srt) + 1}\n{_srt_temps(t)} --> {_srt_temps(t + duree - 0.3)}\n{qui}{texte}\n")
            t += duree
        total = t

        journal("Musique et bruitages…")
        fond = composer_musique(total) * float(script.get("volume_musique", 0.55))
        whoosh = bruit_whoosh()
        tonnerre = bruit_tonnerre()
        for k, sc in enumerate(scenes):
            if k > 0:
                i0 = max(0, int((debuts[k] - 0.35) * SR))
                fond[i0:i0 + len(whoosh)] += whoosh[: len(fond) - i0, None][: len(whoosh)]
            if sc.get("bruitage") == "tonnerre":
                i0 = int((debuts[k] + 0.2) * SR)
                seg = tonnerre[: max(0, len(fond) - i0)]
                fond[i0:i0 + len(seg)] += seg[:, None]
        wav = tmp / "fond.wav"
        ecrire_wav(wav, fond)

        journal("Assemblage…")
        liste = tmp / "liste.txt"
        liste.write_text("".join(f"file '{c.as_posix()}'\n" for c in clips), encoding="utf-8")
        assemble = tmp / "assemble.mp4"
        _ff("-f", "concat", "-safe", "0", "-i", str(liste), "-c", "copy", str(assemble))
        _ff("-i", str(assemble), "-i", str(wav),
            "-filter_complex", "[0:a][1:a]amix=inputs=2:duration=first:normalize=0,alimiter=limit=0.95[a]",
            "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
            "-movflags", "+faststart", str(sortie))
    sortie.with_suffix(".srt").write_text("\n".join(srt), encoding="utf-8")
    return sortie


def main(argv=None):
    p = argparse.ArgumentParser(description="Crée un court animé à partir d'un script JSON (gratuit).")
    p.add_argument("script", help="Fichier JSON du script")
    p.add_argument("-o", "--sortie", default="anime.mp4")
    p.add_argument("--cache", help="Dossier où garder les images IA (pour refaire la vidéo sans les régénérer)")
    a = p.parse_args(argv)
    script = json.loads(Path(a.script).read_text(encoding="utf-8"))
    out = creer_anime(script, a.sortie, a.cache)
    print(f"✅ Animé créé : {out}")


if __name__ == "__main__":
    main()
