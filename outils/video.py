"""Générateur de vidéo à partir de texte — 100 % gratuit.

Le texte est découpé en « diapositives » (une par paragraphe, ou par groupe de
phrases si le paragraphe est long). Pour chaque diapositive :
  1. une image est dessinée (fond dégradé ou image perso + texte centré) ;
  2. une narration est générée avec outils.voix (facultatif) ;
  3. un clip est assemblé avec ffmpeg (durée = durée de la voix).
Les clips sont ensuite mis bout à bout, avec musique de fond en option, et un
fichier de sous-titres .srt est créé à côté de la vidéo.

Astuce : une ligne qui commence par « # » devient une diapositive de titre.

Utilisation :
    python -m outils.video --fichier script.txt -o video.mp4
    python -m outils.video "# Mon titre\n\nPremier point.\n\nDeuxième point." \
        --format portrait --theme ocean --voix henri --musique fond.mp3
"""

from __future__ import annotations

import argparse
import re
import subprocess
import tempfile
import textwrap
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from .images_ia import STYLES, construire_prompt, generer_image
from .voix import VOIX_PAR_DEFAUT, duree_audio, ffmpeg_exe, generer_voix

FORMATS = {
    "paysage": (1920, 1080),   # YouTube
    "portrait": (1080, 1920),  # TikTok, Reels, Shorts
    "carre": (1080, 1080),     # Instagram
}

# (couleur haut, couleur bas, couleur du texte, couleur d'accent)
THEMES = {
    "nuit": ((15, 23, 42), (49, 46, 129), (248, 250, 252), (129, 140, 248)),
    "ocean": ((8, 47, 73), (14, 116, 144), (240, 249, 255), (103, 232, 249)),
    "coucher": ((76, 5, 25), (234, 88, 12), (255, 247, 237), (253, 186, 116)),
    "foret": ((5, 46, 22), (21, 128, 61), (240, 253, 244), (134, 239, 172)),
    "clair": ((248, 250, 252), (226, 232, 240), (15, 23, 42), (79, 70, 229)),
}

POLICES = [
    # Linux
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    # Windows
    "C:/Windows/Fonts/arialbd.ttf",
    "C:/Windows/Fonts/segoeuib.ttf",
    # macOS
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/Library/Fonts/Arial Bold.ttf",
]

FPS = 30
PAUSE = 0.6  # secondes de silence après chaque diapositive


@dataclass
class Diapo:
    texte: str
    titre: bool = False
    prompt: str | None = None  # description de l'image IA ([image: ...])


# --------------------------------------------------------------------------- #
# Découpage du texte
# --------------------------------------------------------------------------- #
def decouper_texte(texte: str, max_caracteres: int = 220) -> list[Diapo]:
    diapos: list[Diapo] = []
    for bloc in re.split(r"\n\s*\n", texte.strip()):
        prompt = None
        m = re.search(r"\[image\s*:\s*([^\]]+)\]", bloc, re.I)
        if m:
            prompt = m.group(1).strip()
            bloc = bloc[: m.start()] + bloc[m.end():]
        bloc = " ".join(bloc.split())
        if not bloc:
            continue
        if bloc.startswith("#"):
            diapos.append(Diapo(bloc.lstrip("#").strip(), titre=True, prompt=prompt))
            continue
        phrases = re.split(r"(?<=[.!?…])\s+", bloc)
        courant = ""
        for phrase in phrases:
            if courant and len(courant) + 1 + len(phrase) > max_caracteres:
                diapos.append(Diapo(courant, prompt=prompt))
                courant = phrase
            else:
                courant = f"{courant} {phrase}".strip()
        if courant:
            diapos.append(Diapo(courant, prompt=prompt))
    return diapos


# --------------------------------------------------------------------------- #
# Dessin des diapositives
# --------------------------------------------------------------------------- #
def _police(taille: int, chemin: str | None = None):
    for p in ([chemin] if chemin else []) + POLICES:
        if p and Path(p).exists():
            return ImageFont.truetype(p, taille)
    return ImageFont.load_default(size=taille)


def _fond(largeur, hauteur, theme, image_fond):
    haut, bas, _, _ = theme
    if image_fond:
        img = Image.open(image_fond).convert("RGB")
        # Recadrage « cover » puis assombrissement pour la lisibilité
        ratio = max(largeur / img.width, hauteur / img.height)
        img = img.resize((int(img.width * ratio) + 1, int(img.height * ratio) + 1))
        x, y = (img.width - largeur) // 2, (img.height - hauteur) // 2
        img = img.crop((x, y, x + largeur, y + hauteur))
        img = img.filter(ImageFilter.GaussianBlur(2))
        voile = Image.new("RGB", img.size, (0, 0, 0))
        return Image.blend(img, voile, 0.55)
    # Dégradé vertical
    degrade = Image.new("RGB", (1, hauteur))
    for y in range(hauteur):
        t = y / max(1, hauteur - 1)
        degrade.putpixel((0, y), tuple(int(haut[i] + (bas[i] - haut[i]) * t) for i in range(3)))
    return degrade.resize((largeur, hauteur))


def dessiner_diapo(
    diapo: Diapo,
    chemin: Path,
    taille=(1920, 1080),
    theme_nom="nuit",
    image_fond=None,
    numero=1,
    total=1,
    police=None,
):
    largeur, hauteur = taille
    theme = THEMES.get(theme_nom, THEMES["nuit"])
    couleur_texte = (255, 255, 255) if image_fond else theme[2]
    accent = theme[3]
    img = _fond(largeur, hauteur, theme, image_fond)
    draw = ImageDraw.Draw(img)

    marge = int(largeur * 0.1)
    zone_l, zone_h = largeur - 2 * marge, int(hauteur * 0.7)
    base = min(largeur, hauteur)
    taille_police = int(base * (0.11 if diapo.titre else 0.065))

    # Réduit la police jusqu'à ce que le texte tienne dans la zone
    while True:
        f = _police(taille_police, police)
        largeur_car = max(1, f.getlength("abcdefghijklmnopqrstuvwxyz") / 26)
        lignes = textwrap.wrap(diapo.texte, width=max(8, int(zone_l / largeur_car)))
        interligne = int(taille_police * 1.35)
        h_total = interligne * len(lignes)
        l_max = max((f.getlength(l) for l in lignes), default=0)
        if (h_total <= zone_h and l_max <= zone_l) or taille_police <= 18:
            break
        taille_police -= 4

    y = (hauteur - h_total) // 2
    ombre = (0, 0, 0) if sum(couleur_texte) > 380 else (255, 255, 255)
    decalage = max(2, taille_police // 25)
    for ligne in lignes:
        x = (largeur - f.getlength(ligne)) // 2
        draw.text((x + decalage, y + decalage), ligne, font=f, fill=ombre)
        draw.text((x, y), ligne, font=f, fill=couleur_texte)
        y += interligne

    if diapo.titre:  # petit trait d'accent sous le titre
        lt = int(largeur * 0.12)
        draw.rounded_rectangle(
            ((largeur - lt) // 2, y + 10, (largeur + lt) // 2, y + 10 + max(6, base // 120)),
            radius=4, fill=accent,
        )

    # Barre de progression en bas
    hb = max(6, base // 150)
    draw.rectangle((0, hauteur - hb, int(largeur * numero / total), hauteur), fill=accent)
    img.save(chemin)


def dessiner_calque(diapo: Diapo, chemin: Path, taille=(1080, 1920), theme_nom="nuit", police=None):
    """Calque transparent posé sur une image IA : sous-titre ou titre."""
    largeur, hauteur = taille
    accent = THEMES.get(theme_nom, THEMES["nuit"])[3]
    img = Image.new("RGBA", taille, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    base = min(largeur, hauteur)
    if diapo.titre:
        draw.rectangle((0, 0, largeur, hauteur), fill=(0, 0, 0, 110))
        taille_police = int(base * 0.1)
        zone = int(largeur * 0.84)
    else:
        taille_police = int(base * 0.05)
        zone = int(largeur * 0.82)
    f = _police(taille_police, police)
    largeur_car = max(1, f.getlength("abcdefghijklmnopqrstuvwxyz") / 26)
    lignes = textwrap.wrap(diapo.texte, width=max(8, int(zone / largeur_car)))
    interligne = int(taille_police * 1.3)
    if diapo.titre:
        y = (hauteur - interligne * len(lignes)) // 2
        for ligne in lignes:
            x = (largeur - f.getlength(ligne)) // 2
            draw.text((x + 4, y + 4), ligne, font=f, fill=(0, 0, 0, 170))
            draw.text((x, y), ligne, font=f, fill=(255, 255, 255, 255))
            y += interligne
        lt = int(largeur * 0.12)
        draw.rounded_rectangle(((largeur - lt) // 2, y + 12, (largeur + lt) // 2, y + 12 + max(6, base // 120)),
                               radius=4, fill=accent + (255,))
    else:
        marge = int(taille_police * 0.6)
        l_max = max(f.getlength(l) for l in lignes)
        h_box = interligne * len(lignes) + 2 * marge
        y0 = int(hauteur * 0.92) - h_box
        x0 = int((largeur - l_max) / 2 - marge * 1.2)
        draw.rounded_rectangle((x0, y0, largeur - x0, y0 + h_box), radius=int(taille_police * 0.5),
                               fill=(0, 0, 0, 160))
        y = y0 + marge
        for ligne in lignes:
            draw.text(((largeur - f.getlength(ligne)) // 2, y), ligne, font=f, fill=(255, 255, 255, 255))
            y += interligne
    img.save(chemin)


# --------------------------------------------------------------------------- #
# Assemblage vidéo
# --------------------------------------------------------------------------- #
def _ffmpeg(*args):
    subprocess.run([ffmpeg_exe(), "-y", "-hide_banner", "-loglevel", "error", *args], check=True)


def _srt_temps(s: float) -> str:
    ms = int(round(s * 1000))
    h, ms = divmod(ms, 3_600_000)
    m, ms = divmod(ms, 60_000)
    sec, ms = divmod(ms, 1000)
    return f"{h:02}:{m:02}:{sec:02},{ms:03}"


def generer_video(
    texte: str,
    sortie: str | Path = "video.mp4",
    format_video: str = "paysage",
    theme: str = "nuit",
    voix: str | None = VOIX_PAR_DEFAUT,
    vitesse: int = 0,
    image_fond: str | None = None,
    musique: str | None = None,
    volume_musique: float = 0.12,
    moteur_voix: str = "auto",
    police: str | None = None,
    images_ia: str | None = None,
    progression=None,
) -> Path:
    """Crée une vidéo MP4 à partir de `texte`.

    voix=None → vidéo sans narration (durée calculée selon la longueur du texte).
    images_ia : style d'images IA (« cinema », « 3d », « jeunesse »…) pour une image
                générée par scène avec un zoom lent ; None = diapositives classiques.
    progression : fonction optionnelle appelée avec (étape, total, message).
    """
    diapos = decouper_texte(texte)
    if not diapos:
        raise ValueError("Le texte est vide.")
    taille = FORMATS.get(format_video, FORMATS["paysage"])
    sortie = Path(sortie)
    sortie.parent.mkdir(parents=True, exist_ok=True)
    total = len(diapos)

    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        clips, srt, t = [], [], 0.0
        for i, d in enumerate(diapos, 1):
            if progression:
                progression(i - 1, total + 1, f"Diapositive {i}/{total}")
            png, clip = tmp / f"d{i:03}.png", tmp / f"c{i:03}.mp4"
            if images_ia:
                photo = tmp / f"ia{i:03}.jpg"
                largeur, hauteur = taille
                ratio = largeur / hauteur
                iw, ih = (576, 1024) if ratio < 0.9 else (1024, 576) if ratio > 1.1 else (768, 768)
                if progression:
                    progression(i - 1, total + 1, f"Image IA {i}/{total}")
                generer_image(construire_prompt(d.prompt or d.texte, images_ia), photo, iw, ih, seed=1000 + i)
                dessiner_calque(d, png, taille, theme, police)
            else:
                dessiner_diapo(d, png, taille, theme, image_fond, i, total, police)

            if voix:
                mp3 = tmp / f"v{i:03}.mp3"
                generer_voix(d.texte, mp3, voix, vitesse=vitesse, moteur=moteur_voix)
                duree = duree_audio(mp3) + PAUSE
                audio = ["-i", str(mp3)]
                filtre_audio = ["-af", f"apad=pad_dur={PAUSE}"]
            else:
                duree = max(3.0, len(d.texte.split()) / 2.5) + PAUSE
                audio = ["-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo"]
                filtre_audio = []

            fondu = min(0.35, duree / 4)
            if images_ia:
                largeur, hauteur = taille
                n = max(1, int(duree * FPS))
                zoom = f"1+0.15*on/{n}" if i % 2 else f"1.15-0.15*on/{n}"
                pad = f",apad=pad_dur={PAUSE}" if voix else ""
                _ffmpeg(
                    "-loop", "1", "-framerate", str(FPS), "-i", str(photo),
                    "-loop", "1", "-framerate", str(FPS), "-i", str(png), *audio,
                    "-t", f"{duree:.3f}",
                    "-filter_complex",
                    f"[0:v]scale={2 * largeur}:{2 * hauteur}:force_original_aspect_ratio=increase,"
                    f"crop={2 * largeur}:{2 * hauteur},"
                    f"zoompan=z='{zoom}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s={largeur}x{hauteur}:fps={FPS},"
                    f"setsar=1[bg];[bg][1:v]overlay=format=auto,"
                    f"fade=t=in:st=0:d={fondu},fade=t=out:st={duree - fondu:.3f}:d={fondu},format=yuv420p[v];"
                    f"[2:a]anull{pad}[a]",
                    "-map", "[v]", "-map", "[a]",
                    "-c:v", "libx264", "-preset", "veryfast", "-r", str(FPS), "-pix_fmt", "yuv420p",
                    "-color_range", "tv",
                    "-c:a", "aac", "-b:a", "160k", "-ar", "44100", "-ac", "2",
                    str(clip),
                )
                clips.append(clip)
                srt.append(f"{i}\n{_srt_temps(t)} --> {_srt_temps(t + duree - PAUSE)}\n{d.texte}\n")
                t += duree
                continue
            _ffmpeg(
                "-loop", "1", "-framerate", str(FPS), "-i", str(png), *audio,
                "-t", f"{duree:.3f}",
                "-vf", f"fade=t=in:st=0:d={fondu},fade=t=out:st={duree - fondu:.3f}:d={fondu},format=yuv420p",
                *filtre_audio,
                "-c:v", "libx264", "-preset", "veryfast", "-tune", "stillimage", "-r", str(FPS),
                "-c:a", "aac", "-b:a", "160k", "-ar", "44100", "-ac", "2",
                str(clip),
            )
            clips.append(clip)
            srt.append(f"{i}\n{_srt_temps(t)} --> {_srt_temps(t + duree - PAUSE)}\n{d.texte}\n")
            t += duree

        if progression:
            progression(total, total + 1, "Assemblage final")
        liste = tmp / "liste.txt"
        liste.write_text("".join(f"file '{c.as_posix()}'\n" for c in clips), encoding="utf-8")

        if musique:
            assemble = tmp / "assemble.mp4"
            _ffmpeg("-f", "concat", "-safe", "0", "-i", str(liste), "-c", "copy", str(assemble))
            _ffmpeg(
                "-i", str(assemble), "-stream_loop", "-1", "-i", str(musique),
                "-filter_complex",
                f"[1:a]volume={volume_musique}[m];[0:a][m]amix=inputs=2:duration=first:normalize=0[a]",
                "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                "-movflags", "+faststart", str(sortie),
            )
        else:
            _ffmpeg("-f", "concat", "-safe", "0", "-i", str(liste), "-c", "copy",
                    "-movflags", "+faststart", str(sortie))

    sortie.with_suffix(".srt").write_text("\n".join(srt), encoding="utf-8")
    if progression:
        progression(total + 1, total + 1, "Terminé")
    return sortie


def main(argv=None):
    p = argparse.ArgumentParser(description="Génère une vidéo à partir d'un texte (gratuit).")
    p.add_argument("texte", nargs="?", help="Texte du script (ou --fichier)")
    p.add_argument("-f", "--fichier", help="Fichier .txt contenant le script")
    p.add_argument("-o", "--sortie", default="video.mp4")
    p.add_argument("--format", choices=FORMATS, default="paysage")
    p.add_argument("--theme", choices=THEMES, default="nuit")
    p.add_argument("-v", "--voix", default=VOIX_PAR_DEFAUT, help="Voix de narration")
    p.add_argument("--sans-voix", action="store_true", help="Vidéo sans narration")
    p.add_argument("--vitesse", type=int, default=0, help="Vitesse de la voix en %%")
    p.add_argument("--image", help="Image de fond (jpg/png)")
    p.add_argument("--musique", help="Musique de fond (mp3/wav), jouée en boucle")
    p.add_argument("--volume-musique", type=float, default=0.12)
    p.add_argument("--hors-ligne", action="store_true", help="Voix hors-ligne")
    p.add_argument("--police", help="Chemin d'une police .ttf personnalisée")
    p.add_argument("--ia", choices=STYLES, metavar="STYLE",
                   help=f"Images générées par IA, une par scène ({', '.join(STYLES)})")
    a = p.parse_args(argv)

    texte = Path(a.fichier).read_text(encoding="utf-8") if a.fichier else a.texte
    if not texte:
        p.error("Donne un texte ou --fichier")
    texte = texte.replace("\\n", "\n")
    out = generer_video(
        texte, a.sortie, a.format, a.theme,
        voix=None if a.sans_voix else a.voix, vitesse=a.vitesse,
        image_fond=a.image, musique=a.musique, volume_musique=a.volume_musique,
        moteur_voix="hors-ligne" if a.hors_ligne else "auto", police=a.police, images_ia=a.ia,
        progression=lambda i, n, m: print(f"[{i}/{n}] {m}"),
    )
    print(f"✅ Vidéo créée : {out}  (+ sous-titres {out.with_suffix('.srt')})")


if __name__ == "__main__":
    main()
