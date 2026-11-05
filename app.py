"""Interface web locale et gratuite pour les deux outils LearnSphere.

Lancement :  python app.py   → s'ouvre dans le navigateur (http://127.0.0.1:7860)
"""

import time
from pathlib import Path

import gradio as gr

from outils.video import FORMATS, THEMES, generer_video
from outils.voix import VOIX_PAR_DEFAUT, VOIX_POPULAIRES, generer_voix

SORTIES = Path("sorties")
SORTIES.mkdir(exist_ok=True)
CHOIX_VOIX = [f"{nom} ({vid})" for nom, vid in VOIX_POPULAIRES.items()]
MOTEURS = {"Auto (en ligne, sinon hors-ligne)": "auto",
           "En ligne (voix naturelles)": "en-ligne",
           "Hors-ligne (sans Internet)": "hors-ligne"}


def _voix(choix: str) -> str:
    return choix.split(" (")[0]


def outil_voix(texte, voix, vitesse, hauteur, volume, moteur):
    if not texte or not texte.strip():
        raise gr.Error("Écris un texte d'abord 🙂")
    sortie = SORTIES / f"voix_{int(time.time())}.mp3"
    srt = sortie.with_suffix(".srt")
    try:
        generer_voix(texte, sortie, _voix(voix), int(vitesse), int(hauteur), int(volume),
                     moteur=MOTEURS[moteur], srt=srt)
    except Exception as e:
        raise gr.Error(f"Échec de la génération : {e}")
    fichiers = [str(sortie)] + ([str(srt)] if srt.exists() and srt.stat().st_size else [])
    return str(sortie), fichiers


def outil_video(texte, format_video, theme, voix, sans_voix, vitesse, moteur,
                image, musique, volume_musique, progress=gr.Progress()):
    if not texte or not texte.strip():
        raise gr.Error("Écris un script d'abord 🙂")
    sortie = SORTIES / f"video_{int(time.time())}.mp4"
    try:
        generer_video(
            texte, sortie, format_video, theme,
            voix=None if sans_voix else _voix(voix), vitesse=int(vitesse),
            image_fond=image, musique=musique, volume_musique=float(volume_musique),
            moteur_voix=MOTEURS[moteur],
            progression=lambda i, n, m: progress(i / n, desc=m),
        )
    except Exception as e:
        raise gr.Error(f"Échec de la génération : {e}")
    return str(sortie), [str(sortie), str(sortie.with_suffix(".srt"))]


EXEMPLE = """# La photosynthèse

Les plantes fabriquent leur propre nourriture grâce à la lumière du soleil.

Elles absorbent de l'eau et du dioxyde de carbone, puis rejettent de l'oxygène.

Sans elles, aucune vie ne serait possible sur Terre !"""

with gr.Blocks(title="LearnSphere — Studio gratuit") as demo:
    gr.Markdown("# 🎙️🎬 LearnSphere — Studio gratuit\nTexte → Voix et Texte → Vidéo, sans abonnement ni clé API.")

    with gr.Tab("🎙️ Texte → Voix"):
        with gr.Row():
            with gr.Column():
                t_texte = gr.Textbox(label="Texte", lines=8, placeholder="Écris ou colle ton texte ici…")
                t_voix = gr.Dropdown(CHOIX_VOIX, value=CHOIX_VOIX[0], label="Voix")
                with gr.Row():
                    t_vitesse = gr.Slider(-50, 100, 0, step=5, label="Vitesse (%)")
                    t_hauteur = gr.Slider(-20, 20, 0, step=1, label="Hauteur (Hz)")
                    t_volume = gr.Slider(-50, 50, 0, step=5, label="Volume (%)")
                t_moteur = gr.Radio(list(MOTEURS), value=list(MOTEURS)[0], label="Moteur")
                t_bouton = gr.Button("Générer la voix", variant="primary")
            with gr.Column():
                t_audio = gr.Audio(label="Résultat", type="filepath")
                t_fichiers = gr.File(label="Télécharger (mp3 + sous-titres)", file_count="multiple")
        t_bouton.click(outil_voix, [t_texte, t_voix, t_vitesse, t_hauteur, t_volume, t_moteur],
                       [t_audio, t_fichiers])

    with gr.Tab("🎬 Texte → Vidéo"):
        with gr.Row():
            with gr.Column():
                v_texte = gr.Textbox(
                    label="Script (un paragraphe = une diapositive, « # » = titre)",
                    lines=10, value=EXEMPLE)
                with gr.Row():
                    v_format = gr.Radio(list(FORMATS), value="paysage", label="Format")
                    v_theme = gr.Dropdown(list(THEMES), value="nuit", label="Thème")
                with gr.Row():
                    v_voix = gr.Dropdown(CHOIX_VOIX, value=CHOIX_VOIX[0], label="Voix off")
                    v_sans_voix = gr.Checkbox(label="Sans voix")
                v_vitesse = gr.Slider(-50, 100, 0, step=5, label="Vitesse de la voix (%)")
                v_moteur = gr.Radio(list(MOTEURS), value=list(MOTEURS)[0], label="Moteur de voix")
                with gr.Accordion("Options : image et musique de fond", open=False):
                    v_image = gr.Image(label="Image de fond (facultatif)", type="filepath")
                    v_musique = gr.Audio(label="Musique de fond (facultatif)", type="filepath")
                    v_vol_musique = gr.Slider(0, 1, 0.12, step=0.02, label="Volume de la musique")
                v_bouton = gr.Button("Générer la vidéo", variant="primary")
            with gr.Column():
                v_video = gr.Video(label="Résultat")
                v_fichiers = gr.File(label="Télécharger (mp4 + sous-titres)", file_count="multiple")
        v_bouton.click(outil_video,
                       [v_texte, v_format, v_theme, v_voix, v_sans_voix, v_vitesse, v_moteur,
                        v_image, v_musique, v_vol_musique],
                       [v_video, v_fichiers])

if __name__ == "__main__":
    demo.launch(inbrowser=True)
