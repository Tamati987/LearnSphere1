"""Mini-studio d'animé — 100 % gratuit.

À partir d'un script JSON (personnages + scènes), fabrique un court animé :
  * une image IA par scène (Pollinations.ai), avec description des
    personnages réinjectée à chaque scène pour plus de cohérence ;
  * une voix différente par personnage (edge-tts) ;
  * mouvements de caméra : zoom_in, zoom_out, pan_gauche, pan_droite,
    pan_haut, tremblement ;
  * effets animés par scène ("effets": [...]) : pluie, eclairs, particules,
    lueur, respiration ;
  * rendu « film » : grain, vignette, couleurs renforcées ;
  * sous-titres avec le nom du personnage qui parle ;
  * musique originale générée par le programme (option "musique": {"epique": true}) ;
  * ambiances et bruitages synthétisés (voir outils/bruitages.py) :
    "ambiance": "ocean" et "bruitages": ["tonnerre", {"son": "impact", "a": 1.5}] ;
  * effet sur la voix d'un personnage ("effet_voix": "esprit").

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

from .bruitages import AMBIANCES, ambiance as _ambiance, bruitage as _bruitage
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


def composer_musique(duree: float, bpm: int = 78, graine: int = 7, epique: bool = False) -> np.ndarray:
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
        # Mode épique : percussions façon pahu en croches (à partir de la 3e mesure)
        if epique and m >= 2:
            for b in range(8):
                tk = np.arange(int(0.35 * SR)) / SR
                f = 120 * np.exp(-tk * 10) + 70
                force = 0.11 if b in (0, 3, 6) else 0.05
                tam = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tk * 12) * force
                placer(tam, m * mesure + b * temps / 2, pan=-0.2 if b % 2 else 0.2)
        m += 1
    # Légère « respiration » stéréo et fondus
    out += rng.normal(0, 0.0015, out.shape).astype(np.float32)
    fade = min(n, int(2.5 * SR))
    out[:int(1.5 * SR)] *= np.linspace(0, 1, min(n, int(1.5 * SR)))[:, None]
    out[n - fade:] *= np.linspace(1, 0, fade)[:, None]
    return out


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
# Effets animés (gratuits) : pluie, particules de lumière
# --------------------------------------------------------------------------- #
def couche_effets(dossier: Path, taille, duree: float, effets: list[str]) -> bool:
    """Écrit une suite d'images PNG transparentes (demi-résolution) avec
    pluie et/ou particules lumineuses. Retourne False s'il n'y a rien à dessiner."""
    if not {"pluie", "particules"} & set(effets):
        return False
    W, H = taille[0] // 2, taille[1] // 2
    rng = np.random.default_rng(11)
    n = max(1, int(duree * FPS))
    gouttes = [(rng.uniform(-W * 0.3, W), rng.uniform(0, H), rng.uniform(900, 1500), rng.uniform(14, 34))
               for _ in range(260 if "pluie" in effets else 0)]
    parts = [(rng.uniform(0, W), rng.uniform(0, H), rng.uniform(25, 70), rng.uniform(1.5, 4.5),
              rng.uniform(0, 6.28)) for _ in range(70 if "particules" in effets else 0)]
    dossier.mkdir(parents=True, exist_ok=True)
    for k in range(n):
        t = k / FPS
        img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        for x0, y0, v, lg in gouttes:
            y = (y0 + v * t) % (H + 60) - 30
            x = x0 + 0.25 * (y - y0 + v * t) * 0.3
            d.line([(x, y), (x - lg * 0.25, y - lg)], fill=(200, 215, 255, 90), width=1)
        for x0, y0, v, r, ph in parts:
            y = (y0 - v * t) % (H + 20)
            x = x0 + 12 * math.sin(t * 1.3 + ph)
            a = int(150 + 100 * math.sin(t * 3 + ph))
            for rr, aa in ((r * 3, a // 6), (r * 1.8, a // 3), (r, a)):
                d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(140, 255, 240, max(0, aa)))
        img.save(dossier / f"fx_{k:04d}.png")
    return True


# --------------------------------------------------------------------------- #
# Caméra
# --------------------------------------------------------------------------- #
def expr_camera(camera: str, n: int, respiration: bool = False) -> tuple[str, str, str]:
    z, x, y = _expr_camera(camera, n)
    if respiration:
        z = f"({z})+0.012*sin(on*0.21)"
    return z, x, y


def _expr_camera(camera: str, n: int) -> tuple[str, str, str]:
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


# --------------------------------------------------------------------------- #
# Voix expressives (gratuites) : émotions par phrase avec [étiquettes]
# --------------------------------------------------------------------------- #
# vitesse (%), hauteur (Hz), volume (%), filtre ffmpeg appliqué au segment
EMOTIONS = {
    "neutre": (0, 0, 0, "anull"),
    "joie": (8, 10, 5, "anull"),
    "excite": (14, 14, 10, "anull"),
    "cri": (10, 16, 25, "acompressor=threshold=-20dB:ratio=6:makeup=4,treble=g=4"),
    "colere": (6, -4, 20, "acompressor=threshold=-20dB:ratio=6:makeup=4,bass=g=3,treble=g=3"),
    "determine": (-4, -3, 12, "bass=g=2"),
    "peur": (12, 10, 0, "vibrato=f=6.5:d=0.22"),
    "panique": (22, 16, 12, "vibrato=f=7.5:d=0.3"),
    "triste": (-14, -8, -8, "vibrato=f=4:d=0.08,lowpass=f=6000"),
    "chuchote": (-6, -4, -35, "highpass=f=350,treble=g=6,volume=1.6"),
    "mysterieux": (-14, -6, -5, "aecho=0.8:0.6:90:0.25"),
    "menace": (-20, -14, 10, "bass=g=5,aecho=0.8:0.5:60:0.2"),
    "moqueur": (4, 6, 5, "anull"),
    "surpris": (10, 18, 15, "anull"),
}
_ETIQUETTE = re.compile(r"\[([a-zA-Zéèêàçù_ ]+)\]")


def sans_etiquettes(texte: str) -> str:
    return " ".join(_ETIQUETTE.sub(" ", texte).split())


def voix_expressive(texte: str, sortie: Path, perso: dict, emotion_scene: str = "neutre") -> Path:
    """Génère la réplique segment par segment, chacun avec son émotion."""
    morceaux, emotion = [], emotion_scene
    pos = 0
    for m in _ETIQUETTE.finditer(texte):
        if texte[pos:m.start()].strip():
            morceaux.append((emotion, texte[pos:m.start()].strip()))
        nom = m.group(1).strip().lower().replace("é", "e").replace("è", "e")
        emotion = nom if nom in EMOTIONS else emotion
        pos = m.end()
    if texte[pos:].strip():
        morceaux.append((emotion, texte[pos:].strip()))
    # Découpe aussi par phrase pour donner plus d'énergie aux exclamations
    segments = []
    for emo, bout in morceaux:
        for phrase in re.split(r"(?<=[.!?…])\s+", bout):
            if phrase.strip():
                segments.append((emo, phrase.strip()))
    dossier = sortie.parent / (sortie.stem + "_seg")
    dossier.mkdir(exist_ok=True)
    wavs = []
    for k, (emo, phrase) in enumerate(segments):
        dv, dh, dvol, filtre = EMOTIONS.get(emo, EMOTIONS["neutre"])
        if phrase.endswith("!") and emo in ("neutre", "determine", "joie"):
            dv, dh, dvol = dv + 6, dh + 6, dvol + 8
        mp3 = dossier / f"{k:02d}.mp3"
        generer_voix(phrase, mp3, perso["voix"], vitesse=perso.get("vitesse", 0) + dv,
                     hauteur=perso.get("hauteur", 0) + dh, volume=max(-90, min(100, dvol)))
        wav = dossier / f"{k:02d}.wav"
        _ff("-i", str(mp3), "-af", f"{filtre},aresample=44100", "-ac", "2", str(wav))
        wavs.append(wav)
    entrees, parts = [], []
    for k, w in enumerate(wavs):
        entrees += ["-i", str(w)]
        parts.append(f"[{k}:a]apad=pad_dur=0.12[s{k}];")
    graphe = "".join(parts) + "".join(f"[s{k}]" for k in range(len(wavs))) + f"concat=n={len(wavs)}:v=0:a=1[a]"
    _ff(*entrees, "-filter_complex", graphe, "-map", "[a]", str(sortie))
    return sortie


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

    def decrire(prompt: str, style_scene: str | None = None) -> str:
        for nom, p in persos.items():
            if p.get("description"):
                prompt = re.sub(r"\{" + re.escape(nom) + r"\}", p["description"], prompt)
        return f"{prompt}, {style_scene or style}"

    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        clips, srt, debuts, t = [], [], [], 0.0
        for i, sc in enumerate(scenes, 1):
            prompt = decrire(sc["image"], sc.get("style"))
            graine = sc.get("graine", 100 + i)
            cle = hashlib.sha1(f"{prompt}|{graine}|{iw}x{ih}".encode()).hexdigest()[:16]
            photo = (cache or tmp) / f"{cle}.jpg"
            if not photo.exists():
                journal(f"[{i}/{len(scenes)}] image IA…")
                generer_image(prompt, photo, iw, ih, seed=graine)
            # Voix
            perso = persos.get(sc.get("personnage", ""), {})
            texte_brut = sc.get("texte", "")
            texte = sans_etiquettes(texte_brut)
            audio_in = ["-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo"]
            effet_voix = {"esprit": "aecho=0.8:0.75:70|140:0.45|0.3,lowpass=f=3800,volume=1.4",
                          "radio": "highpass=f=400,lowpass=f=3000"}.get(perso.get("effet_voix", ""), "anull")
            if texte and perso.get("voix"):
                mp3 = tmp / f"v{i:03}.wav"
                voix_expressive(texte_brut, mp3, perso, sc.get("emotion", "neutre"))
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
            effets = sc.get("effets", [])
            z, x, y = expr_camera(sc.get("camera", "zoom_in"), n, "respiration" in effets)
            lumiere = ""
            if "eclairs" in effets:
                lumiere = ":brightness='0.45*lt(mod(t+0.3,2.1),0.07)+0.3*lt(mod(t+1.1,3.4),0.05)':eval=frame"
            elif "lueur" in effets:
                lumiere = ":brightness='0.05*sin(t*3)':eval=frame"
            dossier_fx = tmp / f"fx{i:03}"
            a_fx = couche_effets(dossier_fx, (W, H), duree, effets)
            entree_fx = ["-framerate", str(FPS), "-i", str(dossier_fx / "fx_%04d.png")] if a_fx else []
            filtre_fx = f"[3:v]scale={W}:{H}[fx];[bg][fx]overlay=format=auto[bg2];[bg2]" if a_fx else "[bg]"
            fondu = min(0.3, duree / 5)
            clip = tmp / f"c{i:03}.mp4"
            _ff(
                "-loop", "1", "-framerate", str(FPS), "-i", str(photo),
                "-loop", "1", "-framerate", str(FPS), "-i", str(calque), *audio_in, *entree_fx,
                "-t", f"{duree:.3f}",
                "-filter_complex",
                f"[0:v]scale={2 * W}:{2 * H}:force_original_aspect_ratio=increase,crop={2 * W}:{2 * H},"
                f"zoompan=z='{z}':x='{x}':y='{y}':d=1:s={W}x{H}:fps={FPS},setsar=1,"
                f"eq=saturation=1.18:contrast=1.06{lumiere},vignette=PI/5,noise=alls=4:allf=t[bg];"
                f"{filtre_fx}[1:v]overlay=format=auto,"
                f"fade=t=in:st=0:d={fondu},fade=t=out:st={duree - fondu:.3f}:d={fondu},format=yuv420p[v];"
                f"[2:a]{effet_voix},apad[a]",
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
        musique = script.get("musique", {})
        fond = composer_musique(total, bpm=int(musique.get("bpm", 78)), epique=bool(musique.get("epique"))) \
            * float(script.get("volume_musique", 0.55))
        n_tot = len(fond)

        def ajouter(son: np.ndarray, debut: float, volume: float = 1.0):
            i0 = max(0, int(debut * SR))
            seg = son[: max(0, n_tot - i0)] * volume
            fond[i0:i0 + len(seg)] += seg[:, None]

        whoosh = _bruitage("whoosh")
        for k, sc in enumerate(scenes):
            fin = debuts[k + 1] if k + 1 < len(scenes) else total
            if k > 0:
                ajouter(whoosh, debuts[k] - 0.4, 0.35)
            amb = sc.get("ambiance")
            if amb in AMBIANCES:
                ajouter(_ambiance(amb, fin - debuts[k] + 0.6), debuts[k] - 0.3, float(sc.get("volume_ambiance", 0.55)))
            sons = sc.get("bruitages", [])
            if sc.get("bruitage"):
                sons = [sc["bruitage"], *sons]
            for son in sons:
                son = {"son": son} if isinstance(son, str) else son
                ajouter(_bruitage(son["son"]), debuts[k] + float(son.get("a", 0.2)), float(son.get("volume", 0.7)))
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
