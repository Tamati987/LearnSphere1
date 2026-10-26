# Jarvis pour Android

Assistant vocal personnel inspiré de J.A.R.V.I.S. Dites « Hey Jarvis », posez votre question ou donnez un ordre : il répond à voix haute et agit sur le téléphone.

## Ce qu'il sait faire

- Se réveiller quand vous dites **« Hey Jarvis »**, même écran éteint. La détection tourne sur le téléphone avec les modèles libres openWakeWord, sans compte ni clé.
- Répondre à toutes vos questions avec Claude, et chercher sur le web l'actualité, la météo ou les faits récents.
- Agir sur le téléphone :
  - appeler un contact ou un numéro ;
  - envoyer un SMS, après vous l'avoir relu et avoir eu votre « oui » ;
  - lire vos SMS, ceux d'un contact précis ou seulement les non lus ;
  - annoncer à voix haute les nouveaux SMS dès qu'ils arrivent, puis proposer d'y répondre (désactivable dans l'écran des clés) ;
  - régler une alarme ou un minuteur ;
  - ouvrir une application ;
  - allumer ou éteindre la lampe torche ;
  - régler le volume ;
  - lancer de la musique ;
  - démarrer un itinéraire GPS ;
  - donner l'état de la batterie et du réseau.
- Parler avec une voix réaliste de majordome britannique si vous ajoutez une clé ElevenLabs : la voix originale « Jarvis » créée pour l'appli (elle doit se trouver dans le même compte ElevenLabs que la clé), sinon la voix « Daniel ». Sans clé, Jarvis utilise la voix de synthèse d'Android.
- S'ouvrir avec une séquence de démarrage animée façon interface holographique, des sons synthétisés sur le téléphone et un message de bienvenue en français. Touchez l'écran pour la passer.
- Rouvrir le micro tout seul quand il vous pose une question.
- Se souvenir de la conversation pendant 10 minutes.

## Installation

1. **Récupérer l'APK.** Dans l'onglet *Actions* du dépôt GitHub, ouvrez le dernier passage réussi de « Jarvis Android APK » et téléchargez l'artefact `jarvis-apk`. Dézippez-le pour obtenir `app-debug.apk`.
2. **Installer.** Copiez le fichier sur le téléphone et ouvrez-le. Android demande d'autoriser l'installation d'applis de source inconnue : acceptez pour cette fois.
3. **Clé Anthropic** (obligatoire, payante à l'usage) : créez-la sur https://console.anthropic.com → *API Keys*, et ajoutez quelques euros de crédit.
4. **Clé ElevenLabs** (facultative, pour la voix réaliste) : créez un compte gratuit sur https://elevenlabs.io, puis copiez une clé dans *Developers → API Keys*.
5. Ouvrez Jarvis, collez les clés, puis touchez **Enregistrer et démarrer**. Acceptez les autorisations : micro, appels, SMS (envoi, lecture et réception), contacts et notifications.
6. Touchez **Autoriser Jarvis à ouvrir des applis**. Sans cette autorisation, Android l'empêche d'ouvrir Spotify, Maps ou l'Horloge quand son écran est fermé.
7. Conseillé : dans *Paramètres → Applis → Jarvis → Batterie*, choisissez **Non restreinte** pour qu'Android ne coupe pas l'écoute.

Vous pouvez aussi toucher **Parler**, dans l'appli ou dans la notification, au lieu de dire « Hey Jarvis ».

## Exemples

- « Hey Jarvis… quel temps fera-t-il demain ? »
- « Hey Jarvis… réveille-moi à 6 h 30. »
- « Hey Jarvis… envoie un SMS à Maman : j'arrive dans dix minutes. »
- « Hey Jarvis… lis mes derniers messages. »
- « Hey Jarvis… est-ce que Maman m'a écrit ? »
- « Hey Jarvis… ouvre WhatsApp. »
- « Hey Jarvis… emmène-moi à l'aéroport. »

## Coûts et confidentialité

- « Hey Jarvis » est détecté sur le téléphone : rien n'est envoyé tant que vous ne l'avez pas dit.
- Ce que vous dites ensuite est transcrit par la reconnaissance vocale Google, puis envoyé à Claude (modèle `claude-opus-5-5`, effort bas, mode rapide quand le compte y a accès).
- Pour répondre vite, Claude répond en flux : Jarvis commence à parler dès la première phrase écrite, et la voix de chaque phrase est générée pendant que Claude écrit la suite. La conversation est mise en cache entre deux questions.
- Le mode rapide de Claude coûte environ deux fois plus cher par mot que le mode normal. S'il est refusé, Jarvis repasse tout seul en mode normal.
- La voix ElevenLabs consomme des caractères : l'offre gratuite en donne environ 10 000 par mois, soit une centaine de réponses.
- Comptez quelques centimes par jour en usage normal. Les recherches web sont facturées en plus par Anthropic.
- Quand Jarvis lit vos SMS, leur contenu est envoyé à Claude pour qu'il vous les résume ou y réponde. Les SMS annoncés automatiquement sont envoyés à ElevenLabs pour être lus à voix haute, et gardés dans la conversation avec Claude pour que vous puissiez y répondre.
- Les clés restent dans les préférences privées de l'appli, sur le téléphone.

## Code

| Fichier | Rôle |
|---|---|
| `JarvisBrain.kt` | Conversation avec Claude : prompt de Jarvis, outils du téléphone, recherche web |
| `PhoneActions.kt` | Exécution des outils : appels, SMS, alarmes, applis, lampe, volume, musique, GPS |
| `JarvisService.kt` | Service au premier plan : mot d'activation, reconnaissance vocale, synthèse vocale |
| `WakeWordDetector.kt`, `WakeWordListener.kt` | Détection de « Hey Jarvis » avec les modèles openWakeWord (ONNX) |
| `ElevenLabsVoice.kt` | Voix réaliste ElevenLabs, avec retour à la voix Android en cas d'échec |
| `BootSequenceView.kt`, `BootSound.kt` | Animation et sons du démarrage |
| `MainActivity.kt` | Écran des clés, journal de conversation, saisie au clavier |

Compilation locale : `gradle assembleDebug` dans ce dossier (Android SDK 35, JDK 17, Gradle 8.11).

## Licences des modèles

Les modèles de `app/src/main/assets/wakeword/` viennent d'openWakeWord (https://github.com/dscripka/openWakeWord). `melspectrogram.onnx` et `embedding_model.onnx` sont sous licence Apache 2.0. `hey_jarvis_v0.1.onnx` est sous licence CC BY-NC-SA 4.0 : usage personnel et non commercial uniquement.
