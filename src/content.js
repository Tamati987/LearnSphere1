'use strict';

// Course content (HTML fragments, authored here — never user input).

const modules = [
  {
    slug: 'comprendre-ia',
    title: "1. Comprendre l'IA générative en 15 minutes",
    duration: '15 min',
    summary: "Ce que l'IA fait très bien, ce qu'elle fait mal, et comment choisir un outil.",
    html: `
<p>Les assistants d'IA générative (ChatGPT, Claude, Gemini, Microsoft Copilot, Mistral Le Chat…) sont des <strong>modèles de langage</strong> : ils prédisent la suite la plus probable d'un texte à partir de ce que vous leur donnez. Ce n'est ni une base de données, ni un moteur de recherche, ni une personne. C'est un <strong>collaborateur rapide et infatigable, qui peut se tromper avec aplomb</strong>.</p>

<h2>Ce que l'IA fait très bien</h2>
<ul>
  <li><strong>Rédiger un premier jet</strong> : emails, posts, descriptions de produits, comptes-rendus.</li>
  <li><strong>Reformuler</strong> : changer le ton, raccourcir, simplifier, traduire.</li>
  <li><strong>Résumer</strong> un long document ou des notes en vrac.</li>
  <li><strong>Structurer</strong> : transformer un brouillon en plan, en tableau, en checklist.</li>
  <li><strong>Trouver des idées</strong> : titres, angles, objections clients, questions à poser.</li>
  <li><strong>Expliquer</strong> une notion, une formule Excel, un contrat, pas à pas.</li>
</ul>

<h2>Ce que l'IA fait mal</h2>
<ul>
  <li><strong>Les faits précis</strong> (chiffres, dates, lois, citations) : elle peut les inventer. C'est ce qu'on appelle une <em>hallucination</em>.</li>
  <li><strong>L'actualité</strong> : selon l'outil, ses connaissances s'arrêtent à une date donnée, sauf si la recherche web est activée.</li>
  <li><strong>Les calculs longs</strong> : elle peut se tromper. Faites vérifier par un tableur.</li>
  <li><strong>Connaître votre entreprise</strong> : elle ne sait rien de vous tant que vous ne lui avez rien dit.</li>
</ul>

<div class="callout"><strong>Règle d'or :</strong> l'IA produit, <em>vous</em> validez. Tout ce qui part chez un client, un fournisseur ou l'administration doit être relu par un humain.</div>

<h2>Quel outil choisir ?</h2>
<p>Les grands assistants se valent pour 90&nbsp;% des tâches de bureau. Choisissez selon trois critères :</p>
<ol>
  <li><strong>Ce que vous utilisez déjà</strong> : Microsoft 365 → Copilot ; Google Workspace → Gemini.</li>
  <li><strong>La confidentialité</strong> : vérifiez dans les paramètres si vos conversations servent à entraîner le modèle, et désactivez-le si possible (voir module 7).</li>
  <li><strong>Le confort</strong> : testez la même demande sur deux outils gratuits pendant une semaine et gardez celui dont les réponses vous conviennent le mieux.</li>
</ol>
<p>La version gratuite suffit pour commencer. Passez à une offre payante seulement quand vous atteignez les limites d'usage chaque semaine.</p>

<h2>Exercice (5 min)</h2>
<p>Ouvrez l'assistant de votre choix et tapez :</p>
<pre>Je suis [votre métier] et je travaille seul(e) / dans une équipe de [N] personnes.
Liste les 10 tâches répétitives de mon métier où un assistant IA pourrait me faire
gagner du temps, classées de la plus rentable à la moins rentable.
Pour chacune, donne un exemple concret de demande à t'adresser.</pre>
<p>Gardez cette liste : c'est votre feuille de route pour la suite du kit.</p>
`,
  },
  {
    slug: 'methode-prompt',
    title: '2. La méthode RCTF : écrire des demandes qui marchent',
    duration: '20 min',
    summary: 'Une structure simple en 4 blocs pour obtenir une bonne réponse dès le premier essai.',
    html: `
<p>90&nbsp;% des réponses décevantes viennent d'une demande trop vague. « Écris un email à un client » donne un texte générique. La méthode <strong>RCTF</strong> règle ce problème.</p>

<h2>Les 4 blocs</h2>
<table>
  <thead><tr><th>Bloc</th><th>Question</th><th>Exemple</th></tr></thead>
  <tbody>
    <tr><td><strong>R</strong>ôle</td><td>Qui doit-elle être ?</td><td>« Tu es un assistant commercial expérimenté. »</td></tr>
    <tr><td><strong>C</strong>ontexte</td><td>Que doit-elle savoir ?</td><td>« Je suis plombier à Lyon. Le client attend un devis depuis 10 jours. »</td></tr>
    <tr><td><strong>T</strong>âche</td><td>Que doit-elle faire, précisément ?</td><td>« Rédige un email d'excuse qui propose un rendez-vous jeudi ou vendredi. »</td></tr>
    <tr><td><strong>F</strong>ormat</td><td>Sous quelle forme ?</td><td>« 120 mots max, ton chaleureux, vouvoiement, objet inclus. »</td></tr>
  </tbody>
</table>

<h2>Exemple complet</h2>
<pre>Rôle : tu es un assistant commercial expérimenté pour un artisan.
Contexte : je suis plombier indépendant à Lyon. Mme Durand m'a demandé un devis
pour remplacer son chauffe-eau il y a 10 jours ; je ne lui ai pas encore répondu.
Le devis est prêt : 1 450 € TTC, pose comprise.
Tâche : rédige un email qui s'excuse du délai sans se justifier longuement,
annonce le montant, et propose une intervention jeudi ou vendredi prochain.
Format : 120 mots maximum, ton chaleureux et professionnel, vouvoiement,
avec une ligne d'objet.</pre>

<h2>Les 5 réflexes qui changent tout</h2>
<ol>
  <li><strong>Itérez au lieu de recommencer.</strong> « Plus court », « plus direct », « enlève le jargon », « propose 3 variantes ».</li>
  <li><strong>Donnez un exemple.</strong> Collez un de vos anciens emails : « Écris dans ce style. »</li>
  <li><strong>Demandez-lui de poser des questions.</strong> Terminez par « Avant de répondre, pose-moi les questions nécessaires. »</li>
  <li><strong>Imposez des contraintes.</strong> Longueur, mots interdits, public visé, niveau de langage.</li>
  <li><strong>Demandez une auto-critique.</strong> « Relis ta réponse comme un client exigeant et améliore-la. »</li>
</ol>

<div class="callout"><strong>Astuce gain de temps :</strong> créez un document « Mes prompts » et collez-y chaque demande qui a bien marché. En un mois, vous aurez votre propre bibliothèque. Le bonus de ce kit vous en donne déjà 40 pour démarrer.</div>

<h2>Exercice (10 min)</h2>
<p>Reprenez une tâche de votre liste du module 1. Écrivez-la d'abord en une phrase, puis en version RCTF. Comparez les deux réponses.</p>
`,
  },
  {
    slug: 'emails-clients',
    title: '3. Emails et relation client',
    duration: '25 min',
    summary: 'Répondre plus vite, relancer sans gêne, gérer les clients mécontents.',
    html: `
<p>Les emails sont souvent le premier gisement de temps. L'objectif n'est pas de laisser l'IA écrire à votre place sans contrôle, mais de passer de 10 minutes à 2 minutes par message délicat.</p>

<h2>1. Répondre à partir de notes en vrac</h2>
<pre>Voici l'email reçu : """[collez l'email]"""
Voici ce que je veux répondre, en vrac : [vos notes, même mal écrites].
Rédige une réponse claire et polie, en vouvoyant, en 150 mots maximum.</pre>

<h2>2. La relance qui ne braque pas</h2>
<pre>Rédige 3 versions d'une relance pour une facture impayée depuis [N] jours
(montant : [X] €, n° [Y]). Version 1 : cordiale. Version 2 : ferme mais polie.
Version 3 : dernière relance avant mise en demeure, factuelle.
Chaque version fait moins de 100 mots.</pre>

<h2>3. Le client mécontent</h2>
<pre>Un client m'a envoyé ce message : """[collez]""".
1) Résume en une phrase ce qu'il reproche vraiment.
2) Identifie ce qui est fondé et ce qui ne l'est pas.
3) Propose une réponse qui reconnaît le problème, propose une solution concrète
   [indiquez ce que vous êtes prêt à offrir] et ne se justifie pas excessivement.</pre>

<h2>4. Vos modèles de réponses récurrentes</h2>
<p>Listez les 5 questions que l'on vous pose le plus souvent (délais, tarifs, garanties…), puis :</p>
<pre>Voici les 5 questions que mes clients me posent le plus, avec mes réponses
habituelles : [liste]. Transforme-les en 5 modèles d'email prêts à copier-coller,
avec des [CHAMPS] à personnaliser entre crochets.</pre>
<p>Enregistrez-les comme « réponses types » dans Gmail ou comme « blocs de texte » dans Outlook. Vous gagnerez du temps tous les jours, même sans ouvrir l'IA.</p>

<div class="callout"><strong>Attention :</strong> avant de coller un email client dans un outil d'IA, retirez les données sensibles (numéros de carte, de sécurité sociale, informations de santé). Voir module 7.</div>
`,
  },
  {
    slug: 'contenu-marketing',
    title: '4. Contenu marketing et réseaux sociaux',
    duration: '25 min',
    summary: "Un mois de publications en une heure, sans sonner comme un robot.",
    html: `
<p>L'IA est excellente pour produire du volume. Le risque, c'est le contenu fade que tout le monde publie. La solution : <strong>donnez-lui votre matière première</strong> (anecdotes, chiffres, avis clients) et gardez la décision finale.</p>

<h2>1. Trouver vos angles</h2>
<pre>Mon activité : [description]. Mes clients idéaux : [description].
Liste 20 idées de publications réparties en 4 catégories :
coulisses, conseils pratiques, preuves (avis, résultats), questions fréquentes.
Pour chaque idée : un titre accrocheur et l'objectif (notoriété, confiance, vente).</pre>

<h2>2. Un mois de calendrier</h2>
<pre>À partir des idées 1, 4, 7, 9, 12, 15, 18 et 20, crée un calendrier de 8 publications
(2 par semaine) pour [Instagram / LinkedIn / Facebook].
Pour chacune : jour conseillé, texte complet adapté au réseau,
idée de visuel, 3 à 5 hashtags pertinents.</pre>

<h2>3. Garder votre voix</h2>
<pre>Voici 3 textes que j'ai écrits moi-même : """[collez]""".
Décris mon style en 5 points (ton, longueur des phrases, expressions, etc.).
Ensuite, réécris la publication suivante dans ce style : """[texte de l'IA]""".</pre>

<h2>4. Recycler un contenu en cinq</h2>
<pre>Voici un article / une vidéo transcrite : """[collez]""".
Transforme-le en : 1 post LinkedIn, 1 légende Instagram, 1 email à mes clients,
3 tweets, 1 FAQ de 5 questions-réponses.</pre>

<h2>5. Fiches produits et pages de vente</h2>
<pre>Produit : [nom, caractéristiques, prix]. Client cible : [qui].
Écris une fiche produit qui commence par le bénéfice principal, liste 5 avantages
concrets (pas des caractéristiques), répond à 3 objections et finit par un appel à l'action.
120 à 180 mots.</pre>

<div class="callout"><strong>Honnêteté obligatoire :</strong> ne publiez jamais un faux avis client ni une statistique inventée par l'IA. C'est trompeur, et c'est sanctionné en France (pratiques commerciales trompeuses).</div>
`,
  },
  {
    slug: 'organisation',
    title: '5. Organisation : réunions, résumés et planning',
    duration: '20 min',
    summary: 'Comptes-rendus en 2 minutes, semaines planifiées, documents digérés.',
    html: `
<h2>1. Compte-rendu de réunion</h2>
<pre>Voici mes notes brutes de réunion : """[collez]""".
Produis un compte-rendu avec : participants, décisions prises,
actions à mener (qui / quoi / pour quand) sous forme de tableau, points en suspens.</pre>
<p>Si votre outil de visioconférence fournit une transcription automatique, collez-la directement, après avoir prévenu les participants que la réunion est transcrite.</p>

<h2>2. Résumer un long document</h2>
<pre>Résume ce document en 10 lignes maximum pour un lecteur pressé.
Puis liste : les 3 points qui me concernent en tant que [rôle],
les dates ou échéances mentionnées, et ce qui me semble ambigu.</pre>
<p>Pour un contrat ou un document juridique, demandez les clauses à vérifier, puis faites-les valider par un professionnel. L'IA ne remplace pas un avocat ni un expert-comptable.</p>

<h2>3. Planifier sa semaine</h2>
<pre>Voici mes tâches de la semaine avec une estimation de durée : [liste].
Mes contraintes : [rendez-vous fixes, horaires]. Je suis plus concentré(e) le [matin/soir].
Propose un planning jour par jour qui place les tâches exigeantes
sur mes créneaux de concentration et regroupe les petites tâches.</pre>

<h2>4. Démêler une situation compliquée</h2>
<pre>Je dois choisir entre [option A] et [option B]. Contexte : [détails].
Fais un tableau avantages / inconvénients / risques / coût,
puis pose-moi les 3 questions qui m'aideraient le plus à trancher.</pre>

<h2>5. Créer une procédure (SOP)</h2>
<pre>Voici comment je fais [tâche], expliqué à l'oral : """[collez]""".
Transforme-le en procédure numérotée qu'un nouvel employé ou un remplaçant
pourrait suivre seul, avec une checklist de contrôle à la fin.</pre>
<div class="callout">Une procédure écrite, c'est ce qui vous permet un jour de déléguer, ou de partir en vacances l'esprit tranquille.</div>
`,
  },
  {
    slug: 'tableurs-donnees',
    title: '6. Excel, Google Sheets et chiffres',
    duration: '25 min',
    summary: 'Formules, nettoyage de données et analyses sans être expert.',
    html: `
<p>Vous n'avez plus besoin de connaître les formules par cœur : décrivez le résultat voulu, l'IA écrit la formule et vous l'explique.</p>

<h2>1. Obtenir une formule</h2>
<pre>Dans Google Sheets [ou Excel en français], j'ai en colonne A des dates de facture,
en B des montants et en C le statut ("payée" / "en attente").
Donne-moi la formule qui calcule le total des factures en attente
de plus de 30 jours. Explique chaque partie de la formule.</pre>
<div class="callout"><strong>Précisez toujours</strong> Excel ou Google Sheets, et la langue de votre logiciel : les noms de fonctions changent (<code>SUMIF</code> / <code>SOMME.SI</code>) et le séparateur aussi (virgule ou point-virgule).</div>

<h2>2. Comprendre une formule existante</h2>
<pre>Explique en français simple ce que fait cette formule, étape par étape :
=RECHERCHEV(A2;Tarifs!A:C;3;FAUX)</pre>

<h2>3. Nettoyer des données</h2>
<pre>Voici 10 lignes d'exemple de mon fichier : [collez, sans données personnelles].
Les noms sont tantôt en majuscules, tantôt non, et les téléphones ont
des formats différents. Donne-moi les étapes et les formules pour tout uniformiser.</pre>

<h2>4. Analyser des ventes</h2>
<pre>Voici mes ventes mensuelles sur 12 mois : [tableau].
Quelles tendances vois-tu ? Quel mois est anormal ?
Propose 3 hypothèses et le graphique le plus adapté pour présenter ces chiffres.</pre>

<h2>5. Vérifier, toujours</h2>
<p>L'IA peut se tromper dans un calcul. La bonne pratique : laissez l'IA <strong>écrire la formule</strong>, laissez le <strong>tableur faire le calcul</strong>, et vérifiez sur 2 ou 3 lignes à la main.</p>
`,
  },
  {
    slug: 'securite-plan-action',
    title: '7. Confidentialité, RGPD et votre plan sur 30 jours',
    duration: '20 min',
    summary: "Utiliser l'IA sans mettre en danger vos clients ni votre entreprise.",
    html: `
<h2>Les 6 règles de sécurité</h2>
<ol>
  <li><strong>Pas de données sensibles</strong> dans un outil grand public : santé, coordonnées bancaires, numéros d'identité, mots de passe.</li>
  <li><strong>Anonymisez</strong> : remplacez « Mme Durand, 12 rue… » par « la cliente A ». L'IA n'a pas besoin du vrai nom pour rédiger.</li>
  <li><strong>Vérifiez les paramètres</strong> : la plupart des outils permettent de refuser que vos conversations servent à l'entraînement. Faites-le.</li>
  <li><strong>Préférez les offres professionnelles</strong> si vous traitez régulièrement des données clients : elles offrent en général des garanties contractuelles plus fortes. Lisez les conditions.</li>
  <li><strong>Relisez tout</strong> ce qui engage votre responsabilité : devis, contrats, conseils, chiffres.</li>
  <li><strong>Soyez transparent</strong> quand c'est pertinent, par exemple si un chatbot répond à vos clients.</li>
</ol>

<h2>RGPD : l'essentiel</h2>
<p>Coller des données personnelles de clients dans un service d'IA, c'est un <strong>traitement de données</strong> au sens du RGPD. Vous en restez responsable. En pratique, pour une petite entreprise : minimisez les données, anonymisez, et informez vos clients dans votre politique de confidentialité si vous utilisez ces outils sur leurs données. La CNIL publie des fiches pratiques gratuites sur l'IA sur <em>cnil.fr</em>.</p>

<h2>Votre plan d'action sur 30 jours</h2>
<table>
  <thead><tr><th>Semaine</th><th>Objectif</th><th>Action concrète</th></tr></thead>
  <tbody>
    <tr><td>1</td><td>Prendre l'habitude</td><td>Utiliser l'IA une fois par jour pour un email ou un résumé. Noter le temps gagné.</td></tr>
    <tr><td>2</td><td>Standardiser</td><td>Créer vos 5 modèles d'emails (module 3) et votre document « Mes prompts ».</td></tr>
    <tr><td>3</td><td>Communiquer</td><td>Produire un mois de publications en une séance (module 4).</td></tr>
    <tr><td>4</td><td>Structurer</td><td>Écrire 2 procédures (module 5) et automatiser un tableau de suivi (module 6).</td></tr>
  </tbody>
</table>

<div class="callout"><strong>Mesurez :</strong> à la fin du mois, comparez le temps passé sur ces tâches avant et après. La plupart des utilisateurs réguliers gagnent plusieurs heures par semaine, à condition de pratiquer chaque jour.</div>

<p>Bravo, vous avez terminé le kit. Le bonus contient 40 demandes prêtes à copier pour continuer.</p>
`,
  },
  {
    slug: 'bonus-40-prompts',
    title: 'Bonus — 40 demandes prêtes à copier',
    duration: 'référence',
    summary: 'Votre bibliothèque de départ, classée par usage.',
    html: `
<p>Remplacez les [crochets] par vos informations. Ajoutez toujours votre contexte (métier, client, objectif) pour de meilleurs résultats.</p>

<h2>Emails et clients</h2>
<ol>
  <li>Réponds à cet email en acceptant la demande, mais en décalant la date au [date] : """[email]"""</li>
  <li>Refuse poliment cette demande en proposant une alternative : [alternative].</li>
  <li>Rédige un email de bienvenue pour un nouveau client de [activité], avec les 3 prochaines étapes.</li>
  <li>Écris un email pour annoncer une hausse de tarifs de [X] % à partir du [date], en expliquant la valeur apportée.</li>
  <li>Transforme ces notes en devis clair et structuré : [notes].</li>
  <li>Rédige une demande d'avis client courte, à envoyer une semaine après la prestation.</li>
  <li>Écris un message pour reprendre contact avec un client inactif depuis 6 mois.</li>
  <li>Reformule ce message pour qu'il soit plus court et plus clair : """[texte]"""</li>
  <li>Traduis cet email en anglais professionnel en gardant le ton : """[texte]"""</li>
  <li>Liste les objections qu'un client pourrait avoir sur [offre] et une réponse pour chacune.</li>
</ol>

<h2>Marketing</h2>
<ol start="11">
  <li>Donne-moi 10 titres accrocheurs pour un article sur [sujet], pour [cible].</li>
  <li>Écris une bio Instagram de 150 caractères pour [activité], avec un appel à l'action.</li>
  <li>Rédige la page « À propos » de mon site à partir de mon parcours : [parcours].</li>
  <li>Propose 5 idées d'offres promotionnelles pour [période] qui ne dévalorisent pas mon service.</li>
  <li>Écris un script de vidéo de 45 secondes qui présente [produit] : accroche, problème, solution, appel à l'action.</li>
  <li>Analyse ce texte de vente et dis-moi ce qui manque pour convaincre : """[texte]"""</li>
  <li>Crée une newsletter mensuelle : 1 actualité, 1 conseil, 1 offre, à partir de : [éléments].</li>
  <li>Propose 10 questions à poser à mes clients pour mieux comprendre leurs besoins.</li>
  <li>Résume les points forts et faibles de ces avis clients : """[avis]"""</li>
  <li>Écris 3 variantes de publicité Facebook (texte principal + titre) pour [offre].</li>
</ol>

<h2>Organisation</h2>
<ol start="21">
  <li>Transforme cette liste en tâches classées par urgence et importance (matrice d'Eisenhower) : [liste].</li>
  <li>Prépare l'ordre du jour d'une réunion de 30 minutes sur [sujet].</li>
  <li>Résume cette transcription en décisions et actions (qui / quoi / quand) : """[texte]"""</li>
  <li>Rédige une procédure pas à pas pour [tâche] à partir de mon explication : [explication].</li>
  <li>Crée une checklist pour [événement / ouverture / livraison].</li>
  <li>Aide-moi à préparer un entretien d'embauche pour un poste de [poste] : 10 questions et ce qu'il faut écouter.</li>
  <li>Rédige une annonce d'emploi attractive pour [poste], [conditions].</li>
  <li>Propose une routine hebdomadaire pour gérer ma comptabilité en moins d'une heure.</li>
  <li>Explique-moi [notion] comme si j'étais débutant, avec un exemple concret.</li>
  <li>Prépare-moi à une négociation avec [fournisseur] : mes arguments, les siens, ma marge de manœuvre.</li>
</ol>

<h2>Chiffres et tableurs</h2>
<ol start="31">
  <li>Donne-moi la formule [Excel/Sheets] pour [résultat souhaité] avec mes colonnes : [description].</li>
  <li>Explique cette formule étape par étape : [formule].</li>
  <li>Propose la structure d'un tableau de suivi de [factures / stocks / prospects].</li>
  <li>Comment créer un tableau croisé dynamique qui montre [analyse] ? Étapes détaillées.</li>
  <li>Quels indicateurs suivre chaque mois pour une activité de [type] ? Comment les calculer ?</li>
  <li>Écris une formule de mise en forme conditionnelle qui colore en rouge [condition].</li>
  <li>Calcule le prix de vente minimum pour une marge de [X] % avec ces coûts : [coûts]. Montre le calcul.</li>
  <li>Aide-moi à construire un prévisionnel simple sur 12 mois : [hypothèses].</li>
  <li>Compare ces 3 devis fournisseurs dans un tableau et signale les différences cachées : [devis].</li>
  <li>Vérifie la cohérence de ces chiffres et signale ce qui paraît anormal : [données].</li>
</ol>
`,
  },
];

module.exports = { modules };
