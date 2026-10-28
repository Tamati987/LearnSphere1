# Jarvis pour Android

Assistant vocal personnel inspiré de J.A.R.V.I.S. Dites « Hey Jarvis », posez votre question ou donnez un ordre : il répond à voix haute et agit sur le téléphone.

## Ce qu'il sait faire

- Se réveiller quand vous dites **« Hey Jarvis »**, même écran éteint. La détection tourne sur le téléphone avec les modèles libres openWakeWord, sans compte ni clé. Sa sensibilité se règle dans l'écran des clés (normale, élevée, très élevée) ; le journal note « presque reconnu » quand Jarvis vous a entendu de justesse, signe qu'il faut l'augmenter.
- Répondre à toutes vos questions avec Claude, et chercher sur le web l'actualité, la météo ou les faits récents.
- Agir sur le téléphone :
  - appeler un contact ou un numéro ;
  - annoncer qui vous appelle (« Monsieur, Paul vous appelle. Voulez-vous répondre ? ») et décrocher ou refuser l'appel à la voix (« oui », « non ») ; rien n'est annoncé si le téléphone est en silencieux ou en vibreur ;
  - envoyer un SMS, après vous l'avoir relu et avoir eu votre « oui ». Avec deux SIM, le SMS part toujours de la SIM 1 ;
  - lire vos SMS, ceux d'un contact précis ou seulement les non lus ;
  - lire les messages WhatsApp et Messenger reçus et y répondre, grâce à leurs notifications (seulement les messages arrivés depuis que Jarvis a l'accès ; l'historique plus ancien reste inaccessible) ;
  - annoncer à voix haute les nouveaux SMS, messages WhatsApp et Messenger dès qu'ils arrivent, puis proposer d'y répondre (désactivable dans l'écran des clés) ;
  - vous réveiller ou vous rappeler un moment de la journée lui-même : à l'heure dite, un carillon sonne et Jarvis dit sa phrase de sa voix, au volume d'alarme, même téléphone verrouillé. L'écran du réveil s'allume, et la notification propose aussi « Arrêter » ou « Encore 10 min ». La sonnerie vient d'un service à part, comme une vraie appli d'alarme : elle part même si le téléphone bloque l'ouverture de l'écran. Les réveils peuvent se répéter certains jours. Deux sont installés au premier lancement :
    - 6 h, du lundi au vendredi : « Bonjour, Monsieur. Il est six heures. C'est l'heure de vous préparer pour le travail. Je vous souhaite une excellente journée. » Puis le programme du jour lu dans l'agenda, par exemple « Au programme aujourd'hui : réunion à 9 heures et déjeuner avec Paul à 12 heures 30. »
    - 12 h, tous les jours : « Monsieur, il est midi. C'est l'heure du déjeuner. Je vous suggère de faire une pause, vous l'avez bien mérité. »
  - lire votre agenda (« qu'est-ce que j'ai demain ? ») et y ajouter des rendez-vous, avec un rappel 30 minutes avant ;
  - régler une alarme classique ou un minuteur ;
  - ouvrir une application, un jeu ou un site web ;
  - passer en **mode jeu** (« Hey Jarvis… mode jeu et lance Free Fire ») : ne pas déranger (les appels prioritaires et les alarmes passent encore), luminosité au maximum, son multimédia à 80 %, et Jarvis n'annonce plus les messages pendant la partie (ils restent notés dans le journal). « Mode normal » remet tout comme avant ;
  - allumer ou éteindre la lampe torche ;
  - régler le volume ;
  - mettre de la musique dans NewPipe en une seule étape : Jarvis cherche sur YouTube (de préférence une playlist, pour que la musique continue) et lance aussitôt le premier résultat dans NewPipe. Pour une demande vague, il choisit lui-même. Il peut aussi ouvrir la recherche dans NewPipe ;
  - mettre en pause, reprendre, passer au morceau suivant ou précédent ;
  - démarrer un itinéraire GPS ;
  - afficher le déverrouillage quand le téléphone est verrouillé (« Jarvis, déverrouille ») : vous déverrouillez avec votre empreinte, votre visage ou votre code, puis Jarvis termine l'action (ouvrir NewPipe, Maps…). Jarvis ne connaît ni ne tape jamais votre code ;
  - donner la météo actuelle (température, ressenti, pluie, vent) et les prévisions, à votre position ou pour une ville, via Open-Meteo (gratuit, sans clé) ;
  - lire les notifications affichées par vos autres applis (météo, banque, livraison, mails…), sans déverrouiller ;
  - donner l'état de la batterie et du réseau, avec la température de la batterie : au-delà de 40 °C, Jarvis signale que le téléphone chauffe et conseille une pause.
- Parler avec une voix réaliste de majordome britannique si vous ajoutez une clé ElevenLabs : la voix originale « Jarvis » créée pour l'appli (elle doit se trouver dans le même compte ElevenLabs que la clé), sinon la voix « Daniel ». Sans clé, Jarvis utilise la voix de synthèse d'Android.
- S'ouvrir avec une séquence de démarrage animée façon interface holographique, des sons synthétisés sur le téléphone et un message de bienvenue en français. Touchez l'écran pour la passer.
- Rouvrir le micro tout seul, avec un bip, quand il vous pose une question, même si une courte phrase suit la question. Sans réponse, il repose la question une fois (« Je me permets d'insister, Monsieur… »), puis attend « Hey Jarvis ».
- Vous laisser le temps de parler : s'il coupe pendant une pause au milieu d'une phrase (« mets-moi de la… »), il réécoute la suite avant de répondre. Si vous ne dites rien juste après « Hey Jarvis », il vous laisse une seconde chance.
- Se souvenir de vous d'une conversation à l'autre, avec une mémoire gardée sur le téléphone :
  - il retient de lui-même ce qui est durable (vos proches, votre travail, vos goûts, vos habitudes), ou ce que vous lui demandez de retenir (« Jarvis, retiens que ma fille s'appelle Léa ») ;
  - il évolue : quand vous le corrigez (« parle moins », « ne m'appelle pas Monsieur »), il en fait une consigne qu'il applique ensuite ;
  - à la fin de chaque conversation (10 minutes sans échange), il la résume et garde ce résumé pour s'en souvenir plus tard ;
  - « Jarvis, qu'est-ce que tu sais de moi ? », « oublie ma fille », « oublie tout » ;
  - chaque souvenir ajouté s'affiche dans le journal.

## Installation

1. **Récupérer l'APK.** Dans l'onglet *Actions* du dépôt GitHub, ouvrez le dernier passage réussi de « Jarvis Android APK » et téléchargez l'artefact `jarvis-apk`. Dézippez-le pour obtenir `app-debug.apk`.
2. **Installer.** Copiez le fichier sur le téléphone et ouvrez-le. Android demande d'autoriser l'installation d'applis de source inconnue : acceptez pour cette fois.
3. **Clé Anthropic** (obligatoire, payante à l'usage) : créez-la sur https://console.anthropic.com → *API Keys*, et ajoutez quelques euros de crédit.
4. **Clé ElevenLabs** (facultative, pour la voix réaliste) : créez un compte gratuit sur https://elevenlabs.io, puis copiez une clé dans *Developers → API Keys*.
5. Ouvrez Jarvis, collez les clés, puis touchez **Enregistrer et démarrer**. Acceptez les autorisations : micro, appels (passer, voir qui appelle, décrocher), SMS (envoi, lecture et réception), contacts, agenda, position approximative (pour la météo) et notifications.
6. Touchez **Autoriser Jarvis à ouvrir des applis**. Sans cette autorisation, Android l'empêche d'ouvrir Spotify, Maps ou l'Horloge quand son écran est fermé.
7. Touchez **Autoriser l'écoute écran verrouillé** et acceptez : Android cesse alors de couper Jarvis quand l'écran est éteint. Jarvis garde aussi le processeur éveillé pendant qu'il écoute, ce qui consomme un peu plus de batterie. Sur certaines marques (Xiaomi, Oppo, Realme, Huawei…), autorisez aussi le démarrage automatique et l'activité en arrière-plan de Jarvis dans les réglages du téléphone. Sur Xiaomi (HyperOS, MIUI), dans *Paramètres → Applis → Gérer les applis → Jarvis* :
   - **Démarrage automatique** : activé ;
   - **Économiseur de batterie** : *Aucune restriction* ;
   - **Autres autorisations** : *Afficher sur l'écran de verrouillage* et *Ouvrir de nouvelles fenêtres en arrière-plan* autorisés ;
   - dans les applis récentes, verrouillez Jarvis (cadenas) : sinon « Tout effacer » ferme Jarvis de force et efface ses réveils jusqu'à sa prochaine ouverture.
8. Pour WhatsApp et Messenger : touchez **Autoriser la lecture de WhatsApp et Messenger**, puis activez Jarvis dans la liste. Si Android affiche « Paramètre restreint », allez dans *Paramètres → Applis → Jarvis*, menu **⋮** en haut à droite → **Autoriser les paramètres restreints**, puis recommencez.
9. Pour le mode jeu : touchez **Autoriser le mode jeu (luminosité)** et activez *Modifier les paramètres système* pour Jarvis. Le ne pas déranger fonctionne dès que l'accès aux notifications (étape 8) est accordé. Sans ces accès, le mode jeu fait le reste et Jarvis dit ce qui manque.
10. Pour la musique dans NewPipe : dans NewPipe, *Paramètres → Vidéo et audio → Action par défaut à l'ouverture du contenu*, choisissez **Lecture en arrière-plan**. Sinon NewPipe demande à chaque fois comment ouvrir le morceau et la musique ne démarre pas toute seule.

Vous pouvez aussi toucher **Parler**, dans l'appli ou dans la notification, au lieu de dire « Hey Jarvis ».

## Exemples

- « Hey Jarvis… quelle température fait-il ? »
- « Hey Jarvis… quel temps fera-t-il demain ? »
- « Hey Jarvis… qu'est-ce qu'il y a dans mes notifications ? »
- « Hey Jarvis… réveille-moi à 6 h 30. »
- « Hey Jarvis… réveille-moi à 7 h et rappelle-moi le rendez-vous chez le dentiste. »
- « Hey Jarvis… quels sont mes réveils ? »
- « Hey Jarvis… mets aussi le réveil de 6 h le samedi. »
- « Hey Jarvis… envoie un SMS à Maman : j'arrive dans dix minutes. »
- « Hey Jarvis… lis mes derniers messages. »
- « Hey Jarvis… est-ce que Maman m'a écrit ? »
- « Hey Jarvis… lis mes messages WhatsApp. »
- « Hey Jarvis… qu'est-ce que Paul m'a écrit sur Messenger ? »
- « Hey Jarvis… qu'est-ce que j'ai de prévu demain ? »
- « Hey Jarvis… ajoute un rendez-vous chez le médecin jeudi à 14 h 30. »
- « Hey Jarvis… mets-moi de la bonne musique. »
- « Hey Jarvis… mets du Stromae. »
- « Hey Jarvis… mets une playlist de jazz pour travailler. »
- « Hey Jarvis… morceau suivant. »
- « Hey Jarvis… ouvre WhatsApp. »
- « Hey Jarvis… mode jeu et lance Free Fire. »
- « Hey Jarvis… j'ai fini de jouer. »
- « Hey Jarvis… est-ce que le téléphone chauffe ? »
- « Hey Jarvis… ouvre youtube.com. »
- « Hey Jarvis… emmène-moi à l'aéroport. »

## Coûts et confidentialité

- « Hey Jarvis » est détecté sur le téléphone : rien n'est envoyé tant que vous ne l'avez pas dit.
- Ce que vous dites ensuite est transcrit par la reconnaissance vocale Google, puis envoyé à Claude (modèle `claude-opus-5-5`, effort bas, mode rapide quand le compte y a accès).
- Pour répondre vite, Claude répond en flux : Jarvis commence à parler dès la première phrase écrite, et la voix de chaque phrase est générée pendant que Claude écrit la suite. La conversation est mise en cache entre deux questions.
- Le mode rapide de Claude coûte environ deux fois plus cher par mot que le mode normal. S'il est refusé, Jarvis repasse tout seul en mode normal.
- La voix ElevenLabs consomme des caractères : l'offre gratuite en donne environ 10 000 par mois, soit une centaine de réponses.
- Comptez quelques centimes par jour en usage normal. Les recherches web sont facturées en plus par Anthropic.
- Quand Jarvis lit vos SMS, leur contenu est envoyé à Claude pour qu'il vous les résume ou y réponde. Les SMS annoncés automatiquement sont envoyés à ElevenLabs pour être lus à voix haute, et gardés dans la conversation avec Claude pour que vous puissiez y répondre.
- Les messages WhatsApp et Messenger lus dans les notifications restent en mémoire sur le téléphone (les 100 derniers). Quand Jarvis les lit ou les annonce, leur contenu est envoyé à Claude et, pour la voix, à ElevenLabs.
- Quand Jarvis lit votre agenda, les événements de la période demandée sont envoyés à Claude.
- Les clés restent dans les préférences privées de l'appli, sur le téléphone.
- La mémoire de Jarvis (souvenirs, consignes, résumés des conversations) est un fichier privé de l'appli, sur le téléphone. Elle est envoyée à Claude au début de chaque conversation pour qu'il s'en serve, et à la fin de chaque conversation, celle-ci est envoyée à Claude pour être résumée. Elle survit aux mises à jour ; elle est effacée si vous désinstallez l'appli.

## Code

| Fichier | Rôle |
|---|---|
| `JarvisBrain.kt` | Conversation avec Claude : prompt de Jarvis, outils du téléphone, recherche web |
| `JarvisMemory.kt` | Mémoire à long terme : souvenirs, consignes apprises, résumés des conversations |
| `PhoneActions.kt` | Exécution des outils : appels, SMS, agenda, alarmes, applis, sites, mode jeu, lampe, volume, musique et NewPipe, GPS |
| `JarvisService.kt` | Service au premier plan : mot d'activation, reconnaissance vocale, synthèse vocale |
| `WakeWordDetector.kt`, `WakeWordListener.kt` | Détection de « Hey Jarvis » avec les modèles openWakeWord (ONNX) |
| `ElevenLabsVoice.kt` | Voix réaliste ElevenLabs, avec retour à la voix Android en cas d'échec |
| `BootSequenceView.kt`, `BootSound.kt` | Animation et sons du démarrage |
| `JarvisAlarm.kt`, `AlarmRingService.kt`, `WakeUpActivity.kt` | Réveil de Jarvis : programmation, sonnerie (carillon et voix), écran de réveil |
| `MessageListener.kt` | Lecture des notifications WhatsApp et Messenger, annonce et réponse |
| `GameMode.kt` | Mode jeu : ne pas déranger, luminosité, son, pause des annonces, puis retour aux réglages d'avant |
| `Weather.kt` | Météo Open-Meteo à la position mémorisée ou pour une ville |
| `MainActivity.kt` | Écran des clés, journal de conversation, saisie au clavier |

Compilation locale : `gradle assembleDebug` dans ce dossier (Android SDK 35, JDK 17, Gradle 8.11).

## Licences des modèles

Les modèles de `app/src/main/assets/wakeword/` viennent d'openWakeWord (https://github.com/dscripka/openWakeWord). `melspectrogram.onnx` et `embedding_model.onnx` sont sous licence Apache 2.0. `hey_jarvis_v0.1.onnx` est sous licence CC BY-NC-SA 4.0 : usage personnel et non commercial uniquement.
