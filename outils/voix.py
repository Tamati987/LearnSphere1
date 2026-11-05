"""Générateur de voix à partir de texte (Text-to-Speech) — 100 % gratuit.

Deux moteurs :
  * « en-ligne »  : voix neuronales Microsoft Edge via la bibliothèque edge-tts
                    (gratuit, sans clé API, très naturel, des centaines de voix).
  * « hors-ligne »: voix du système via pyttsx3 (eSpeak sous Linux, SAPI sous
                    Windows, NSSpeech sous macOS). Moins naturel mais fonctionne
                    sans Internet.
  * « auto »      : essaie en-ligne puis bascule en hors-ligne en cas d'échec.

Utilisation en ligne de commande :
    python -m outils.voix "Bonjour tout le monde" -o bonjour.mp3
    python -m outils.voix --fichier texte.txt -v henri --vitesse 10 --srt
    python -m outils.voix --liste-voix fr
"""

from __future__ import annotations

import argparse
import asyncio
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

# Raccourcis faciles à retenir -> identifiants de voix edge-tts
VOIX_POPULAIRES: dict[str, str] = {
    "denise": "fr-FR-DeniseNeural",            # France, femme
    "henri": "fr-FR-HenriNeural",              # France, homme
    "eloise": "fr-FR-EloiseNeural",            # France, enfant/fille
    "vivienne": "fr-FR-VivienneMultilingualNeural",  # France, femme, multilingue
    "remy": "fr-FR-RemyMultilingualNeural",    # France, homme, multilingue
    "sylvie": "fr-CA-SylvieNeural",            # Québec, femme
    "antoine": "fr-CA-AntoineNeural",          # Québec, homme
    "charline": "fr-BE-CharlineNeural",        # Belgique, femme
    "gerard": "fr-BE-GerardNeural",            # Belgique, homme
    "ariane": "fr-CH-ArianeNeural",            # Suisse, femme
    "aria": "en-US-AriaNeural",                # Anglais US, femme
    "guy": "en-US-GuyNeural",                  # Anglais US, homme
    "sonia": "en-GB-SoniaNeural",              # Anglais UK, femme
    "elvira": "es-ES-ElviraNeural",            # Espagnol, femme
    "katja": "de-DE-KatjaNeural",              # Allemand, femme
}
VOIX_PAR_DEFAUT = "denise"


# --------------------------------------------------------------------------- #
# Utilitaires
# --------------------------------------------------------------------------- #
def ffmpeg_exe() -> str:
    """Retourne le chemin d'ffmpeg (fourni par imageio-ffmpeg, sinon système)."""
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:  # pragma: no cover - dépend de l'installation
        exe = shutil.which("ffmpeg")
        if not exe:
            raise RuntimeError(
                "ffmpeg introuvable. Installe-le avec : pip install imageio-ffmpeg"
            )
        return exe


def duree_audio(chemin: str | Path) -> float:
    """Durée d'un fichier audio/vidéo en secondes (lue via ffmpeg)."""
    proc = subprocess.run(
        [ffmpeg_exe(), "-hide_banner", "-i", str(chemin), "-f", "null", "-"],
        capture_output=True,
        text=True,
    )
    # La dernière ligne "time=HH:MM:SS.xx" donne la durée réelle décodée.
    derniere = None
    for morceau in proc.stderr.replace("\r", "\n").split("\n"):
        if "time=" in morceau:
            derniere = morceau.split("time=")[1].split()[0]
    if not derniere:
        raise RuntimeError(f"Impossible de lire la durée de {chemin}")
    h, m, s = derniere.split(":")
    return int(h) * 3600 + int(m) * 60 + float(s)


def resoudre_voix(voix: str | None) -> str:
    voix = (voix or VOIX_PAR_DEFAUT).strip()
    return VOIX_POPULAIRES.get(voix.lower(), voix)


def _pourcent(valeur: int) -> str:
    return f"{'+' if valeur >= 0 else ''}{int(valeur)}%"


def _hertz(valeur: int) -> str:
    return f"{'+' if valeur >= 0 else ''}{int(valeur)}Hz"


# --------------------------------------------------------------------------- #
# Moteur en ligne : edge-tts
# --------------------------------------------------------------------------- #
async def _edge_async(texte, sortie, voix, vitesse, hauteur, volume, srt):
    import edge_tts

    proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy") or None
    communicate = edge_tts.Communicate(
        texte,
        voix,
        rate=_pourcent(vitesse),
        pitch=_hertz(hauteur),
        volume=_pourcent(volume),
        proxy=proxy,
    )
    sous_titres = edge_tts.SubMaker()
    with open(sortie, "wb") as f:
        async for morceau in communicate.stream():
            if morceau["type"] == "audio":
                f.write(morceau["data"])
            elif morceau["type"] in ("WordBoundary", "SentenceBoundary"):
                sous_titres.feed(morceau)
    if srt:
        Path(srt).write_text(sous_titres.get_srt(), encoding="utf-8")


def _generer_edge(texte, sortie, voix, vitesse, hauteur, volume, srt):
    asyncio.run(_edge_async(texte, sortie, voix, vitesse, hauteur, volume, srt))
    if Path(sortie).stat().st_size == 0:
        raise RuntimeError("edge-tts n'a renvoyé aucun audio")


async def lister_voix_edge(filtre: str = "") -> list[dict]:
    import edge_tts

    proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy") or None
    voix = await edge_tts.list_voices(proxy=proxy)
    filtre = filtre.lower()
    return [v for v in voix if filtre in v["ShortName"].lower()]


# --------------------------------------------------------------------------- #
# Moteur hors ligne : pyttsx3
# --------------------------------------------------------------------------- #
_SCRIPT_PYTTSX3 = r"""
import sys, pyttsx3
texte_fichier, wav, langue, debit, volume = sys.argv[1:6]
texte = open(texte_fichier, encoding="utf-8").read()
moteur = pyttsx3.init()
for v in moteur.getProperty("voices"):
    infos = (v.id + " " + (v.name or "") + " " + " ".join(
        l.decode(errors="ignore") if isinstance(l, bytes) else str(l)
        for l in (v.languages or []))).lower()
    if langue in infos:
        moteur.setProperty("voice", v.id)
        break
moteur.setProperty("rate", int(debit))
moteur.setProperty("volume", float(volume))
moteur.save_to_file(texte, wav)
moteur.runAndWait()
"""


def _generer_pyttsx3(texte, sortie, voix, vitesse, volume):
    langue = voix.split("-")[0].lower() if "-" in voix else "fr"
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "voix.wav")
        fichier_texte = os.path.join(tmp, "texte.txt")
        # Le retour à la ligne final évite un bug d'eSpeak (fichier vide).
        Path(fichier_texte).write_text(texte + "\n", encoding="utf-8")
        debit = int(175 * (1 + vitesse / 100))
        espeak = shutil.which("espeak-ng") or shutil.which("espeak")
        if espeak:  # Linux : appel direct, le plus fiable
            subprocess.run(
                [espeak, "-v", langue, "-s", str(debit),
                 "-a", str(int(100 * (1 + volume / 100))), "-w", wav, "-f", fichier_texte],
                check=True, capture_output=True,
            )
        else:
            # pyttsx3 est instable s'il est réutilisé dans un même processus :
            # on le lance dans un processus séparé à chaque génération.
            subprocess.run(
                [sys.executable, "-c", _SCRIPT_PYTTSX3, fichier_texte, wav, langue,
                 str(debit), str(max(0.0, min(1.0, 1 + volume / 100)))],
                check=True, capture_output=True,
            )
        if not os.path.exists(wav) or os.path.getsize(wav) == 0:
            raise RuntimeError("Le moteur hors-ligne n'a produit aucun audio")
        # Conversion vers le format demandé (mp3, wav, ...)
        subprocess.run(
            [ffmpeg_exe(), "-y", "-loglevel", "error", "-i", wav, str(sortie)],
            check=True,
        )


# --------------------------------------------------------------------------- #
# API principale
# --------------------------------------------------------------------------- #
def generer_voix(
    texte: str,
    sortie: str | Path = "voix.mp3",
    voix: str | None = None,
    vitesse: int = 0,
    hauteur: int = 0,
    volume: int = 0,
    moteur: str = "auto",
    srt: str | Path | None = None,
) -> Path:
    """Transforme `texte` en fichier audio.

    vitesse : en %, ex. -20 (plus lent) à +50 (plus rapide)
    hauteur : en Hz, ex. -10 (plus grave) à +10 (plus aigu) — moteur en-ligne
    volume  : en %, ex. -50 à +50
    moteur  : "auto", "en-ligne" ou "hors-ligne"
    srt     : chemin optionnel d'un fichier de sous-titres (moteur en-ligne)
    """
    texte = (texte or "").strip()
    if not texte:
        raise ValueError("Le texte est vide.")
    sortie = Path(sortie)
    sortie.parent.mkdir(parents=True, exist_ok=True)
    voix_id = resoudre_voix(voix)

    if moteur in ("auto", "en-ligne"):
        try:
            _generer_edge(texte, sortie, voix_id, vitesse, hauteur, volume, srt)
            return sortie
        except Exception as e:
            if moteur == "en-ligne":
                raise
            print(
                f"[voix] Moteur en ligne indisponible ({type(e).__name__}) "
                "→ bascule sur le moteur hors-ligne.",
                file=sys.stderr,
            )
    _generer_pyttsx3(texte, sortie, voix_id, vitesse, volume)
    return sortie


def main(argv=None):
    p = argparse.ArgumentParser(description="Génère une voix à partir d'un texte (gratuit).")
    p.add_argument("texte", nargs="?", help="Texte à lire (ou utiliser --fichier)")
    p.add_argument("-f", "--fichier", help="Fichier .txt contenant le texte")
    p.add_argument("-o", "--sortie", default="voix.mp3", help="Fichier audio de sortie")
    p.add_argument("-v", "--voix", default=VOIX_PAR_DEFAUT,
                   help=f"Raccourci ({', '.join(VOIX_POPULAIRES)}) ou identifiant edge-tts")
    p.add_argument("--vitesse", type=int, default=0, help="Vitesse en %% (ex. -20, 15)")
    p.add_argument("--hauteur", type=int, default=0, help="Hauteur en Hz (ex. -5, 5)")
    p.add_argument("--volume", type=int, default=0, help="Volume en %% (ex. -30, 20)")
    p.add_argument("--hors-ligne", action="store_true", help="Forcer le moteur hors-ligne")
    p.add_argument("--srt", action="store_true", help="Créer aussi un fichier de sous-titres .srt")
    p.add_argument("--liste-voix", nargs="?", const="", metavar="FILTRE",
                   help="Lister les voix en ligne (ex. --liste-voix fr-)")
    a = p.parse_args(argv)

    if a.liste_voix is not None:
        for v in asyncio.run(lister_voix_edge(a.liste_voix)):
            print(f"{v['ShortName']:<40} {v['Gender']:<7} {v['Locale']}")
        return

    texte = Path(a.fichier).read_text(encoding="utf-8") if a.fichier else a.texte
    if not texte:
        p.error("Donne un texte ou --fichier")
    srt = Path(a.sortie).with_suffix(".srt") if a.srt else None
    out = generer_voix(
        texte, a.sortie, a.voix, a.vitesse, a.hauteur, a.volume,
        moteur="hors-ligne" if a.hors_ligne else "auto", srt=srt,
    )
    print(f"✅ Audio créé : {out}" + (f"  (+ sous-titres {srt})" if srt else ""))


if __name__ == "__main__":
    main()
