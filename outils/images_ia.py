"""Images générées par IA, gratuitement, via Pollinations.ai.

Sans clé : service gratuit limité à environ 1 image toutes les 45 secondes
(par adresse IP) et un petit logo en bas de l'image.
Avec une clé secrète gratuite (sk_…, créée sur enter.pollinations.ai) dans la
variable d'environnement POLLINATIONS_KEY : plus rapide et sans logo.
"""

from __future__ import annotations

import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

STYLES = {
    "cinema": "cinematic photograph, dramatic natural light, highly detailed, 35mm",
    "3d": "3D animated film style, soft lighting, colorful, cute, expressive",
    "jeunesse": "children's book illustration, vibrant colors, soft shapes",
    "aquarelle": "watercolor painting, soft pastel colors, paper texture",
    "anime": "anime style, detailed background, vivid colors",
    "infographie": "flat vector illustration, clean shapes, bright colors, educational",
}

ECART_GRATUIT = 46.0  # secondes entre deux images sans clé
_dernier_appel = 0.0


def _attendre(ecart: float):
    global _dernier_appel
    reste = ecart - (time.monotonic() - _dernier_appel)
    if reste > 0:
        print(f"[images] attente {reste:.0f} s (limite du service gratuit)…", file=sys.stderr)
        time.sleep(reste)
    _dernier_appel = time.monotonic()


def construire_prompt(description: str, style: str = "cinema") -> str:
    style_txt = STYLES.get(style, style)
    return f"{description}, {style_txt}, no text, no letters, no watermark"


def generer_image(
    prompt: str,
    chemin: str | Path,
    largeur: int = 576,
    hauteur: int = 1024,
    seed: int = 42,
    cle: str | None = None,
    essais: int = 4,
) -> Path:
    """Télécharge une image IA correspondant à `prompt` dans `chemin`."""
    cle = cle or os.environ.get("POLLINATIONS_KEY") or None
    p = urllib.parse.quote(prompt, safe="")
    q = f"width={largeur}&height={hauteur}&seed={seed}&nologo=true"
    urls = []
    if cle:
        urls.append(f"https://gen.pollinations.ai/image/{p}?{q}&model=flux&key={urllib.parse.quote(cle)}")
    urls.append(f"https://image.pollinations.ai/prompt/{p}?{q}")

    derniere_erreur = None
    for _ in range(essais):
        _attendre(1.5 if cle else ECART_GRATUIT)
        for url in urls:
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "LearnSphere/1.0"})
                with urllib.request.urlopen(req, timeout=180) as r:
                    if not r.headers.get("Content-Type", "").startswith("image/"):
                        raise RuntimeError("réponse qui n'est pas une image")
                    Path(chemin).write_bytes(r.read())
                    return Path(chemin)
            except urllib.error.HTTPError as e:
                derniere_erreur = e
                if e.code in (401, 403) and "gen.pollinations" in url:
                    print("[images] clé refusée, je continue sans clé.", file=sys.stderr)
            except Exception as e:  # réseau, délai dépassé…
                derniere_erreur = e
    raise RuntimeError(f"Image IA impossible à créer : {derniere_erreur}")
