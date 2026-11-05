# LearnSphere — Studio gratuit 🎙️🎬

Deux outils **100 % gratuits** (pas d'abonnement, pas de clé API) :

| Outil | Ce qu'il fait |
|---|---|
| 🎙️ **Texte → Voix** | Transforme un texte en fichier audio MP3 (+ sous-titres `.srt`). Voix neuronales naturelles en français (France, Québec, Belgique, Suisse) et dans d'autres langues. |
| 🎬 **Texte → Vidéo** | Transforme un script en vidéo MP4 : une diapositive animée par paragraphe, voix off automatique, image et musique de fond en option, sous-titres `.srt`. Formats YouTube (16:9), TikTok/Reels/Shorts (9:16) et Instagram (1:1). |

## Comment c'est gratuit ?

- **Voix en ligne** : [edge-tts](https://github.com/rany2/edge-tts) utilise les voix neuronales de Microsoft Edge, gratuites et sans compte. Il faut une connexion Internet.
- **Voix hors-ligne** (secours automatique) : les voix intégrées à ton système (Windows, macOS ou eSpeak sous Linux). Moins naturelles, mais elles fonctionnent sans Internet.
- **Vidéo** : tout est fabriqué sur ton ordinateur avec Pillow et ffmpeg (ffmpeg s'installe tout seul via `imageio-ffmpeg`).

## Installation (une seule fois)

Il faut [Python 3.10 ou plus](https://www.python.org/downloads/).

```bash
git clone https://github.com/tamati987/LearnSphere1.git
cd LearnSphere1
python -m venv .venv
# Windows : .venv\Scripts\activate     macOS/Linux : source .venv/bin/activate
pip install -r requirements.txt
```

Sous Linux, pour la voix hors-ligne : `sudo apt install espeak-ng`.

## Utilisation facile : l'interface web

```bash
python app.py
```

Ton navigateur s'ouvre sur `http://127.0.0.1:7860` avec deux onglets : **Texte → Voix** et **Texte → Vidéo**.
Les fichiers créés sont aussi enregistrés dans le dossier `sorties/`.

## Utilisation en ligne de commande

### Voix

```bash
python -m outils.voix "Bonjour et bienvenue sur LearnSphere !" -o bonjour.mp3
python -m outils.voix --fichier cours.txt -v henri --vitesse 10 --srt -o cours.mp3
python -m outils.voix --liste-voix fr-        # toutes les voix françaises disponibles
python -m outils.voix "Sans Internet" --hors-ligne -o test.mp3
```

Raccourcis de voix : `denise`, `henri`, `eloise`, `vivienne`, `remy` (France), `sylvie`, `antoine` (Québec), `charline`, `gerard` (Belgique), `ariane` (Suisse), `aria`, `guy`, `sonia` (anglais), `elvira` (espagnol), `katja` (allemand). Tu peux aussi donner n'importe quel identifiant de `--liste-voix`, par ex. `fr-FR-VivienneMultilingualNeural`.

### Vidéo

Écris ton script dans un fichier texte. **Un paragraphe = une diapositive** ; une ligne qui commence par `#` devient un **titre** :

```text
# La photosynthèse

Les plantes fabriquent leur propre nourriture grâce à la lumière du soleil.

Elles absorbent de l'eau et du dioxyde de carbone, puis rejettent de l'oxygène.
```

```bash
python -m outils.video --fichier script.txt -o video.mp4
python -m outils.video --fichier script.txt --format portrait --theme coucher --voix henri
python -m outils.video --fichier script.txt --image fond.jpg --musique musique.mp3
python -m outils.video --fichier script.txt --sans-voix
```

| Option | Valeurs |
|---|---|
| `--format` | `paysage` (1920×1080), `portrait` (1080×1920), `carre` (1080×1080) |
| `--theme` | `nuit`, `ocean`, `coucher`, `foret`, `clair` |
| `--voix` / `--sans-voix` | voix de narration, ou aucune |
| `--vitesse` | vitesse de la voix en %, ex. `-10` ou `15` |
| `--image` | image de fond (assombrie automatiquement pour la lisibilité) |
| `--musique` / `--volume-musique` | musique de fond en boucle, volume de 0 à 1 (défaut 0,12) |
| `--hors-ligne` | forcer la voix hors-ligne |
| `--police` | police `.ttf` personnalisée |

## Utiliser les outils dans ton propre code

```python
from outils.voix import generer_voix
from outils.video import generer_video

generer_voix("Bonjour !", "bonjour.mp3", voix="sylvie")
generer_video(open("script.txt", encoding="utf-8").read(), "video.mp4",
              format_video="portrait", theme="ocean", voix="henri")
```

## Bon à savoir

- Pour une musique de fond, utilise une musique libre de droits (par ex. la bibliothèque audio de YouTube ou Pixabay Music).
- edge-tts dépend d'un service de Microsoft non officiel : s'il ne répond plus, l'outil bascule automatiquement sur la voix hors-ligne.
