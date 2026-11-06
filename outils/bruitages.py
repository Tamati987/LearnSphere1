"""Bruitages et ambiances fabriqués par le programme — 100 % gratuits et libres.

Tous les sons sont synthétisés (bruit filtré, oscillateurs, réverbération) :
aucun fichier à télécharger, aucun droit d'auteur.

Ambiances (sons continus, en boucle sur une scène) :
    ocean, vent, pluie, nuit, foule, sous_marin, ville
Bruitages ponctuels :
    tonnerre, whoosh, impact, rugissement, magie, vague, coeur, pahu, avion

    from outils.bruitages import ambiance, bruitage
    son = ambiance("ocean", 6.0)       # tableau numpy mono, 44,1 kHz
    boum = bruitage("tonnerre")
"""

from __future__ import annotations

import numpy as np
from scipy import signal

SR = 44100


# --------------------------------------------------------------------------- #
# Briques de base
# --------------------------------------------------------------------------- #
def _bruit(n: int, graine: int = 0) -> np.ndarray:
    return np.random.default_rng(graine).normal(0, 1, n)


def _filtre(x: np.ndarray, kind: str, f, ordre: int = 4) -> np.ndarray:
    sos = signal.butter(ordre, f, btype=kind, fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def _lisse(n: int, vitesse: float, graine: int) -> np.ndarray:
    """Courbe aléatoire lente entre 0 et 1 (pour moduler un son)."""
    pts = max(4, int(n / SR * vitesse) + 4)
    v = np.random.default_rng(graine).random(pts)
    c = np.interp(np.linspace(0, pts - 3, n), np.arange(pts), v)
    return c


def _norm(x: np.ndarray, crete: float = 0.9) -> np.ndarray:
    m = np.max(np.abs(x)) or 1.0
    return (x / m * crete).astype(np.float32)


def reverb(x: np.ndarray, duree: float = 1.6, mix: float = 0.3, graine: int = 5) -> np.ndarray:
    n_ir = int(duree * SR)
    t = np.arange(n_ir) / SR
    ir = _bruit(n_ir, graine) * np.exp(-t * 6.0 / duree)
    ir = _filtre(ir, "lowpass", 5000, 2)
    ir /= np.sqrt(np.sum(ir ** 2)) or 1
    humide = np.zeros(len(x) + n_ir)
    conv = signal.fftconvolve(x, ir)
    humide[: len(conv)] = conv[: len(humide)]
    sec = np.concatenate([x, np.zeros(n_ir)])
    return (sec * (1 - mix) + humide * mix * 2.2).astype(np.float32)


def _filtre_glissant(x: np.ndarray, centres: np.ndarray, largeur: float = 0.6) -> np.ndarray:
    """Passe-bande dont la fréquence centrale évolue (traitement par blocs)."""
    bloc = 1024
    out = np.zeros_like(x)
    fen = np.hanning(bloc * 2)
    for d in range(0, len(x) - bloc, bloc):
        seg = x[d:d + bloc * 2]
        if len(seg) < bloc * 2:
            break
        fc = float(np.clip(centres[min(d + bloc, len(centres) - 1)], 80, 15000))
        lo, hi = fc * (1 - largeur / 2), min(fc * (1 + largeur / 2), SR / 2 - 100)
        y = _filtre(seg, "bandpass", [lo, hi], 2)
        out[d:d + bloc * 2] += y * fen
    return out


def _env(n: int, attaque: float, relache: float) -> np.ndarray:
    t = np.arange(n) / SR
    a = np.minimum(1, t / max(attaque, 1e-3))
    r = np.minimum(1, (n / SR - t) / max(relache, 1e-3))
    return a * np.clip(r, 0, 1)


# --------------------------------------------------------------------------- #
# Ambiances
# --------------------------------------------------------------------------- #
def _ocean(n):
    t = np.arange(n) / SR
    houle = ((np.sin(2 * np.pi * t / 6.5) + 1) / 2) ** 2 * 0.7 + 0.3 * _lisse(n, 0.5, 2)
    grave = _filtre(_bruit(n, 1), "lowpass", 700) * houle
    ecume = _filtre(_bruit(n, 2), "bandpass", [2500, 9000]) * houle ** 3 * 0.35
    return grave + ecume


def _vent(n):
    mod = 0.3 + 0.7 * _lisse(n, 0.4, 3)
    souffle = _filtre(_bruit(n, 3), "bandpass", [250, 1400]) * mod
    sifflement = _filtre(_bruit(n, 4), "bandpass", [880, 960], 2) * mod ** 2 * 3
    return souffle + sifflement


def _pluie(n):
    fond = _filtre(_bruit(n, 5), "bandpass", [900, 9000]) * 0.5
    rng = np.random.default_rng(6)
    gouttes = np.zeros(n)
    for pos in rng.integers(0, max(1, n - 2000), int(n / SR * 60)):
        d = int(SR * 0.012)
        tt = np.arange(d) / SR
        f = rng.uniform(2500, 6000)
        gouttes[pos:pos + d] += np.sin(2 * np.pi * f * tt) * np.exp(-tt * 400) * rng.uniform(0.3, 1)
    return fond + gouttes * 0.5


def _nuit(n):
    t = np.arange(n) / SR
    out = _ocean(n) * 0.25
    rng = np.random.default_rng(7)
    for k in range(3):  # trois grillons
        f = 4300 + 350 * k
        periode = rng.uniform(0.7, 1.1)
        phase = rng.uniform(0, periode)
        groupe = ((t + phase) % periode) < 0.18
        pulses = (np.sin(2 * np.pi * 32 * t) > 0.3)
        out += np.sin(2 * np.pi * f * t) * groupe * pulses * 0.12 * (0.6 + 0.4 * _lisse(n, 0.2, 8 + k))
    return out


def _foule(n):
    out = np.zeros(n)
    for k in range(7):
        voix = _filtre(_bruit(n, 20 + k), "bandpass", [250 + 60 * k, 900 + 80 * k], 2)
        out += voix * (0.3 + 0.7 * _lisse(n, 3 + k, 30 + k))
    clameur = _filtre(_bruit(n, 40), "bandpass", [400, 2500]) * _lisse(n, 0.3, 41) ** 4 * 1.5
    return _filtre(out, "lowpass", 2200) + clameur


def _sous_marin(n):
    grave = _filtre(_bruit(n, 50), "lowpass", 180) * (0.6 + 0.4 * _lisse(n, 0.3, 51)) * 3
    rng = np.random.default_rng(52)
    bulles = np.zeros(n)
    for pos in rng.integers(0, max(1, n - 4000), int(n / SR * 4)):
        d = int(SR * rng.uniform(0.03, 0.07))
        tt = np.arange(d) / SR
        f = np.linspace(rng.uniform(250, 500), rng.uniform(700, 1400), d)
        bulles[pos:pos + d] += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 40) * 0.5
    return grave + bulles


def _ville(n):
    trafic = _filtre(_bruit(n, 60), "lowpass", 350) * (0.5 + 0.5 * _lisse(n, 0.5, 61)) * 1.5
    return trafic + _avion(n) * 0.6


AMBIANCES = {"ocean": _ocean, "vent": _vent, "pluie": _pluie, "nuit": _nuit,
             "foule": _foule, "sous_marin": _sous_marin, "ville": _ville}


def ambiance(nom: str, duree: float) -> np.ndarray:
    n = max(1, int(duree * SR))
    x = AMBIANCES[nom](n)
    return _norm(x, 0.5) * _env(n, 0.6, 0.6).astype(np.float32)


# --------------------------------------------------------------------------- #
# Bruitages ponctuels
# --------------------------------------------------------------------------- #
def _tonnerre():
    n = int(4.0 * SR)
    t = np.arange(n) / SR
    crac = _filtre(_bruit(n, 70), "highpass", 1500) * np.exp(-t * 25) * 0.8
    roulement = np.zeros(n)
    for k, (debut, force) in enumerate([(0.05, 1.0), (0.6, 0.7), (1.3, 0.5), (2.1, 0.35)]):
        env = np.where(t > debut, np.exp(-(t - debut) * 2.2) * np.minimum(1, (t - debut) / 0.08), 0)
        roulement += _filtre(_bruit(n, 71 + k), "lowpass", 160) * env * force * 4
    return reverb(crac + roulement, 2.5, 0.35)


def _whoosh():
    n = int(0.8 * SR)
    t = np.linspace(0, 1, n)
    centres = 350 + 2600 * np.sin(np.pi * t) ** 2
    x = _filtre_glissant(_bruit(n, 80), centres, 0.8) * np.sin(np.pi * t) ** 2
    return x


def _impact():
    n = int(0.6 * SR)
    t = np.arange(n) / SR
    f = 110 * np.exp(-t * 18) + 45
    coup = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)
    claque = _filtre(_bruit(n, 90), "lowpass", 3500) * np.exp(-t * 60) * 0.6
    return reverb(coup + claque, 0.8, 0.2)


def _rugissement():
    n = int(2.6 * SR)
    t = np.arange(n) / SR
    f = 52 + 8 * np.sin(2 * np.pi * 0.7 * t) + 3 * _lisse(n, 6, 100)
    phase = np.cumsum(f) / SR
    dent_scie = 2 * (phase % 1) - 1
    grain = _filtre(_bruit(n, 101), "bandpass", [80, 900]) * 0.6
    tremolo = 0.65 + 0.35 * np.sin(2 * np.pi * 17 * t)
    x = _filtre((dent_scie + grain) * tremolo, "lowpass", 1100) * _env(n, 0.35, 1.0)
    return reverb(x, 2.8, 0.45)


def _magie():
    n = int(3.0 * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    notes = [69, 71, 73, 76, 78, 81, 83, 85, 88]  # pentatonique aiguë, montante
    for k, m in enumerate(notes):
        d0 = int(k * 0.11 * SR)
        tt = t[: n - d0]
        f0 = 440 * 2 ** ((m - 69) / 12)
        cloche = sum(np.sin(2 * np.pi * f0 * r * tt) * a for r, a in ((1, 1), (2.76, 0.4), (5.4, 0.2)))
        out[d0:] += cloche * np.exp(-tt * 3.5) * 0.25
    scintillement = _filtre(_bruit(n, 110), "highpass", 6000) * _env(n, 0.4, 1.5) * 0.25
    return reverb(out + scintillement, 3.0, 0.5)


def _vague():
    n = int(3.5 * SR)
    t = np.linspace(0, 1, n)
    centres = 250 + 4500 * np.clip(t / 0.3, 0, 1) ** 1.5 * np.exp(-np.clip(t - 0.3, 0, 1) * 2.5)
    x = _filtre_glissant(_bruit(n, 120), centres, 1.4)
    env = np.minimum(1, t / 0.25) ** 2 * np.exp(-np.clip(t - 0.25, 0, 1) * 3)
    grave = _filtre(_bruit(n, 121), "lowpass", 200) * env * 2
    return x * env + grave


def _coeur():
    n = int(2.6 * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for b in range(3):
        for decal, force in ((0.0, 1.0), (0.22, 0.7)):
            d0 = int((b * 0.85 + decal) * SR)
            tt = t[: n - d0]
            out[d0:] += np.sin(2 * np.pi * 48 * tt) * np.exp(-tt * 22) * force
    return _filtre(out, "lowpass", 150)


def _pahu():
    """Trois frappes de tambour pahu, grave et résonnant."""
    n = int(2.4 * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for k, d in enumerate((0.0, 0.45, 0.9)):
        d0 = int(d * SR)
        tt = t[: n - d0]
        f = 95 * np.exp(-tt * 6) + 58
        peau = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 4.5)
        frappe = _filtre(_bruit(len(tt), 130 + k), "bandpass", [150, 1500]) * np.exp(-tt * 45) * 0.4
        out[d0:] += (peau + frappe) * (1.0 if k < 2 else 1.3)
    return reverb(out, 1.8, 0.3)


def _avion(n=None):
    n = n or int(6 * SR)
    t = np.linspace(0, 1, n)
    centres = 1800 - 1300 * t  # effet Doppler
    reacteur = _filtre_glissant(_bruit(n, 140), centres, 1.2)
    grondement = _filtre(_bruit(n, 141), "lowpass", 250) * 2
    passage = np.exp(-((t - 0.45) / 0.22) ** 2)
    return (reacteur + grondement) * passage


BRUITAGES = {"tonnerre": _tonnerre, "whoosh": _whoosh, "impact": _impact,
             "rugissement": _rugissement, "magie": _magie, "vague": _vague,
             "coeur": _coeur, "pahu": _pahu, "avion": _avion}


def bruitage(nom: str) -> np.ndarray:
    return _norm(BRUITAGES[nom](), 0.85)
