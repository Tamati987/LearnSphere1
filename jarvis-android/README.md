# Jarvis pour Android

Assistant vocal personnel inspiré de J.A.R.V.I.S. Dites « Jarvis », posez votre question ou donnez un ordre : il répond à voix haute et agit sur le téléphone.

## Ce qu'il sait faire

- Se réveiller quand vous dites **« Jarvis »**, même écran éteint (moteur Picovoice Porcupine, qui tourne sur le téléphone).
- Répondre à toutes vos questions avec Claude, et chercher sur le web l'actualité, la météo ou les faits récents.
- Agir sur le téléphone :
  - appeler un contact ou un numéro ;
  - envoyer un SMS, après vous l'avoir relu et avoir eu votre « oui » ;
  - régler une alarme ou un minuteur ;
  - ouvrir une application ;
  - allumer ou éteindre la lampe torche ;
  - régler le volume ;
  - lancer de la musique ;
  - démarrer un itinéraire GPS ;
  - donner l'état de la batterie et du réseau.
- Rouvrir le micro tout seul quand il vous pose une question.
- Se souvenir de la conversation pendant 10 minutes.

## Installation

1. **Récupérer l'APK.** Dans l'onglet *Actions* du dépôt GitHub, ouvrez le dernier passage réussi de « Jarvis Android APK » et téléchargez l'artefact `jarvis-apk`. Dézippez-le pour obtenir `app-debug.apk`.
2. **Installer.** Copiez le fichier sur le téléphone et ouvrez-le. Android demande d'autoriser l'installation d'applis de source inconnue : acceptez pour cette fois.
3. **Clé Anthropic** (obligatoire, payante à l'usage) : créez-la sur https://console.anthropic.com → *API Keys*, et ajoutez quelques euros de crédit.
4. **Clé Picovoice** (gratuite, pour le mot « Jarvis ») : créez un compte sur https://console.picovoice.ai et copiez votre *AccessKey*.
5. Ouvrez Jarvis, collez les deux clés, puis touchez **Enregistrer et démarrer**. Acceptez les autorisations : micro, appels, SMS, contacts et notifications.
6. Touchez **Autoriser Jarvis à ouvrir des applis**. Sans cette autorisation, Android l'empêche d'ouvrir Spotify, Maps ou l'Horloge quand son écran est fermé.
7. Conseillé : dans *Paramètres → Applis → Jarvis → Batterie*, choisissez **Non restreinte** pour qu'Android ne coupe pas l'écoute.

Sans clé Picovoice, Jarvis fonctionne quand même : touchez **Parler** dans l'appli ou dans la notification.

## Exemples

- « Jarvis… quel temps fera-t-il demain ? »
- « Jarvis… réveille-moi à 6 h 30. »
- « Jarvis… envoie un SMS à Maman : j'arrive dans dix minutes. »
- « Jarvis… ouvre WhatsApp. »
- « Jarvis… emmène-moi à l'aéroport. »

## Coûts et confidentialité

- Le mot « Jarvis » est détecté sur le téléphone : rien n'est envoyé tant que vous ne l'avez pas dit.
- Ce que vous dites ensuite est transcrit par la reconnaissance vocale Google, puis envoyé à Claude (modèle `claude-opus-5-5`, effort bas pour aller vite).
- Comptez quelques centimes par jour en usage normal. Les recherches web sont facturées en plus par Anthropic.
- Les clés restent dans les préférences privées de l'appli, sur le téléphone.

## Code

| Fichier | Rôle |
|---|---|
| `JarvisBrain.kt` | Conversation avec Claude : prompt de Jarvis, outils du téléphone, recherche web |
| `PhoneActions.kt` | Exécution des outils : appels, SMS, alarmes, applis, lampe, volume, musique, GPS |
| `JarvisService.kt` | Service au premier plan : mot d'activation, reconnaissance vocale, synthèse vocale |
| `MainActivity.kt` | Écran des clés, journal de conversation, saisie au clavier |

Compilation locale : `gradle assembleDebug` dans ce dossier (Android SDK 35, JDK 17, Gradle 8.11).
