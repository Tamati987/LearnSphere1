'use strict';

// "Générateur de demandes IA": turns a few form fields into a ready-to-paste
// prompt following the RCTF method (Rôle, Contexte, Tâche, Format) taught in
// the course. Free visitors get the `free` templates; buyers get all of them.

const MAX_FIELD = 1500;

function rctf({ role, context, task, format, extra }) {
  const parts = [
    `Rôle : ${role}`,
    `Contexte : ${context}`,
    `Tâche : ${task}`,
    `Format : ${format}`,
  ];
  if (extra) parts.push(extra);
  parts.push("Avant de répondre, si une information essentielle te manque, pose-moi d'abord tes questions.");
  return parts.join('\n\n');
}

const or = (value, fallback) => (value ? value : fallback);

const templates = [
  // --- Emails & clients -------------------------------------------------
  {
    id: 'email-reponse',
    category: 'Emails et clients',
    title: 'Répondre à un email',
    free: true,
    fields: [
      { name: 'metier', label: 'Votre activité', placeholder: 'ex : plombier indépendant à Lyon' },
      { name: 'email', label: "L'email reçu", type: 'textarea', placeholder: "Collez l'email (sans données sensibles)" },
      { name: 'notes', label: 'Ce que vous voulez répondre (en vrac)', type: 'textarea', placeholder: 'ex : oui pour jeudi, prix 1 450 €, prévoir accès cave' },
      { name: 'ton', label: 'Ton souhaité', placeholder: 'ex : chaleureux et professionnel' },
    ],
    build: (v) => rctf({
      role: 'tu es un assistant de rédaction professionnel, spécialiste de la relation client.',
      context: `je suis ${or(v.metier, 'un professionnel indépendant')}. Voici l'email reçu : """${or(v.email, '[email à coller]')}"""`,
      task: `rédige ma réponse à partir de ces notes : ${or(v.notes, '[mes notes]')}. Reprends tous les points de mes notes, sans rien inventer (ni prix, ni date, ni promesse).`,
      format: `150 mots maximum, ton ${or(v.ton, 'cordial et professionnel')}, vouvoiement, avec une ligne d'objet.`,
    }),
  },
  {
    id: 'relance-facture',
    category: 'Emails et clients',
    title: 'Relancer une facture impayée',
    free: true,
    fields: [
      { name: 'client', label: 'Type de client', placeholder: 'ex : cliente fidèle, entreprise' },
      { name: 'montant', label: 'Montant et n° de facture', placeholder: 'ex : 480 € — facture F-2026-031' },
      { name: 'retard', label: 'Retard', placeholder: 'ex : 25 jours' },
    ],
    build: (v) => rctf({
      role: 'tu es un assistant administratif diplomate et efficace.',
      context: `un ${or(v.client, 'client')} n'a pas réglé la ${or(v.montant, '[montant et n° de facture]')}, avec ${or(v.retard, '[N jours]')} de retard.`,
      task: "rédige 3 versions d'une relance : 1) cordiale, 2) ferme mais polie, 3) dernière relance avant mise en demeure, factuelle. Ne mentionne aucune pénalité chiffrée.",
      format: 'chaque version fait moins de 100 mots, avec un objet. Sépare-les clairement.',
    }),
  },
  {
    id: 'client-mecontent',
    category: 'Emails et clients',
    title: 'Répondre à un client mécontent',
    free: false,
    fields: [
      { name: 'message', label: 'Le message du client', type: 'textarea' },
      { name: 'geste', label: 'Ce que vous êtes prêt à proposer', placeholder: 'ex : réintervention gratuite sous 48 h' },
    ],
    build: (v) => rctf({
      role: 'tu es un responsable de la relation client expérimenté et empathique.',
      context: `un client m'a écrit : """${or(v.message, '[message]')}"""`,
      task: `1) résume en une phrase ce qu'il reproche vraiment ; 2) distingue ce qui est fondé de ce qui ne l'est pas ; 3) rédige une réponse qui reconnaît le problème, propose ${or(v.geste, 'une solution concrète')} et ne se justifie pas excessivement.`,
      format: 'trois parties titrées ; la réponse fait 150 mots maximum, vouvoiement.',
    }),
  },
  {
    id: 'devis',
    category: 'Emails et clients',
    title: 'Transformer des notes en devis clair',
    free: false,
    fields: [
      { name: 'metier', label: 'Votre activité' },
      { name: 'notes', label: 'Vos notes de devis', type: 'textarea', placeholder: 'prestations, quantités, prix unitaires, délais' },
    ],
    build: (v) => rctf({
      role: 'tu es un assistant commercial rigoureux.',
      context: `je suis ${or(v.metier, 'un professionnel indépendant')}. Mes notes : ${or(v.notes, '[notes]')}`,
      task: "transforme ces notes en devis structuré. N'ajoute aucune prestation ni aucun prix absent de mes notes ; signale les informations manquantes.",
      format: 'un tableau (désignation, quantité, prix unitaire, total) suivi des conditions (délai, validité, modalités de paiement) et de la liste des points à compléter.',
      extra: 'Ne fais pas les calculs de TVA : indique seulement où ils doivent figurer, je les ferai dans mon logiciel.',
    }),
  },
  {
    id: 'modeles-faq',
    category: 'Emails et clients',
    title: 'Créer mes réponses types',
    free: false,
    fields: [
      { name: 'questions', label: 'Les questions fréquentes de vos clients et vos réponses habituelles', type: 'textarea' },
    ],
    build: (v) => rctf({
      role: 'tu es un spécialiste du service client.',
      context: `voici les questions que mes clients me posent souvent, avec mes réponses habituelles : ${or(v.questions, '[liste]')}`,
      task: "transforme-les en modèles d'emails prêts à copier-coller, avec des [CHAMPS] à personnaliser entre crochets.",
      format: "un modèle par question, chacun avec un objet et 120 mots maximum.",
    }),
  },
  // --- Marketing --------------------------------------------------------
  {
    id: 'post-reseau',
    category: 'Marketing',
    title: 'Écrire une publication pour les réseaux',
    free: true,
    fields: [
      { name: 'activite', label: 'Votre activité' },
      { name: 'reseau', label: 'Réseau', placeholder: 'Instagram, LinkedIn, Facebook…' },
      { name: 'sujet', label: 'Sujet ou anecdote', type: 'textarea' },
      { name: 'objectif', label: 'Objectif', placeholder: 'notoriété, confiance, vente…' },
    ],
    build: (v) => rctf({
      role: 'tu es un community manager qui écrit de façon naturelle, sans jargon marketing.',
      context: `mon activité : ${or(v.activite, '[activité]')}. Sujet : ${or(v.sujet, '[sujet]')}`,
      task: `écris une publication pour ${or(v.reseau, 'Instagram')} dont l'objectif est : ${or(v.objectif, 'la confiance')}. N'invente aucun chiffre, avis ou témoignage.`,
      format: 'une accroche forte en première ligne, un texte adapté au réseau, un appel à l’action, 3 à 5 hashtags pertinents, puis une idée de visuel.',
    }),
  },
  {
    id: 'calendrier',
    category: 'Marketing',
    title: 'Planifier un mois de publications',
    free: false,
    fields: [
      { name: 'activite', label: 'Votre activité' },
      { name: 'cible', label: 'Vos clients idéaux' },
      { name: 'reseau', label: 'Réseau' },
      { name: 'evenements', label: 'Événements du mois (facultatif)', placeholder: 'ex : soldes, salon, nouveau produit' },
    ],
    build: (v) => rctf({
      role: 'tu es un stratège de contenu pour petites entreprises.',
      context: `mon activité : ${or(v.activite, '[activité]')}. Mes clients : ${or(v.cible, '[cible]')}. Temps forts du mois : ${or(v.evenements, 'aucun en particulier')}.`,
      task: `crée un calendrier de 8 publications (2 par semaine) pour ${or(v.reseau, 'Instagram')}, en alternant coulisses, conseils, preuves et questions fréquentes. N'invente aucun avis client.`,
      format: 'un tableau : semaine, jour conseillé, type, accroche, texte complet, idée de visuel.',
    }),
  },
  {
    id: 'fiche-produit',
    category: 'Marketing',
    title: 'Rédiger une fiche produit ou service',
    free: false,
    fields: [
      { name: 'produit', label: 'Produit / service (caractéristiques, prix)', type: 'textarea' },
      { name: 'cible', label: 'Client visé' },
    ],
    build: (v) => rctf({
      role: 'tu es un rédacteur web spécialisé en pages de vente honnêtes et efficaces.',
      context: `produit : ${or(v.produit, '[produit]')}. Client visé : ${or(v.cible, '[cible]')}.`,
      task: "rédige une fiche qui commence par le bénéfice principal, liste 5 avantages concrets, répond à 3 objections fréquentes et se termine par un appel à l'action. N'affirme rien qui ne figure pas dans la description.",
      format: '120 à 180 mots, intertitres courts.',
    }),
  },
  {
    id: 'mon-style',
    category: 'Marketing',
    title: 'Réécrire un texte dans mon style',
    free: false,
    fields: [
      { name: 'exemples', label: 'Deux ou trois textes écrits par vous', type: 'textarea' },
      { name: 'texte', label: 'Le texte à réécrire', type: 'textarea' },
    ],
    build: (v) => rctf({
      role: 'tu es un éditeur attentif au style de chaque auteur.',
      context: `voici des textes que j'ai écrits moi-même : """${or(v.exemples, '[exemples]')}"""`,
      task: `décris mon style en 5 points (ton, longueur des phrases, expressions…), puis réécris ce texte dans ce style : """${or(v.texte, '[texte]')}"""`,
      format: "d'abord les 5 points, puis le texte réécrit.",
    }),
  },
  {
    id: 'recyclage',
    category: 'Marketing',
    title: 'Recycler un contenu en 5 formats',
    free: false,
    fields: [{ name: 'contenu', label: 'Article, vidéo transcrite ou newsletter', type: 'textarea' }],
    build: (v) => rctf({
      role: 'tu es un spécialiste de la déclinaison de contenus.',
      context: `voici mon contenu d'origine : """${or(v.contenu, '[contenu]')}"""`,
      task: 'décline-le sans ajouter de faits nouveaux.',
      format: '1 post LinkedIn, 1 légende Instagram, 1 email à mes clients, 3 messages courts, 1 FAQ de 5 questions-réponses. Un titre par format.',
    }),
  },
  // --- Organisation -----------------------------------------------------
  {
    id: 'compte-rendu',
    category: 'Organisation',
    title: 'Compte-rendu de réunion',
    free: true,
    fields: [{ name: 'notes', label: 'Notes brutes ou transcription', type: 'textarea' }],
    build: (v) => rctf({
      role: 'tu es un assistant de direction organisé.',
      context: `voici mes notes de réunion : """${or(v.notes, '[notes]')}"""`,
      task: "produis un compte-rendu fidèle. Si une information (responsable, échéance) n'est pas dans les notes, écris « à préciser » au lieu de l'inventer.",
      format: 'participants, décisions, tableau des actions (qui / quoi / pour quand), points en suspens.',
    }),
  },
  {
    id: 'planning',
    category: 'Organisation',
    title: 'Planifier ma semaine',
    free: false,
    fields: [
      { name: 'taches', label: 'Tâches avec durée estimée', type: 'textarea' },
      { name: 'contraintes', label: 'Rendez-vous fixes et horaires' },
      { name: 'energie', label: 'Moment où vous êtes le plus concentré(e)', placeholder: 'matin, après-midi…' },
    ],
    build: (v) => rctf({
      role: 'tu es un coach en organisation pragmatique.',
      context: `mes tâches : ${or(v.taches, '[tâches]')}. Contraintes : ${or(v.contraintes, 'aucune')}. Je suis plus concentré(e) le ${or(v.energie, 'matin')}.`,
      task: 'propose un planning jour par jour : tâches exigeantes sur mes créneaux de concentration, petites tâches regroupées, 20 % de marge pour les imprévus.',
      format: 'un tableau par jour, puis 3 conseils courts.',
    }),
  },
  {
    id: 'procedure',
    category: 'Organisation',
    title: 'Écrire une procédure (pour déléguer)',
    free: false,
    fields: [
      { name: 'tache', label: 'Nom de la tâche' },
      { name: 'explication', label: 'Comment vous la faites, expliqué simplement', type: 'textarea' },
    ],
    build: (v) => rctf({
      role: 'tu es un expert en organisation qui rédige des procédures claires.',
      context: `voici comment je réalise « ${or(v.tache, '[tâche]')} » : ${or(v.explication, '[explication]')}`,
      task: 'transforme cette explication en procédure que quelqu’un de nouveau pourrait suivre seul. Signale les étapes floues.',
      format: 'étapes numérotées, outils nécessaires, erreurs fréquentes, checklist de contrôle finale.',
    }),
  },
  {
    id: 'decision',
    category: 'Organisation',
    title: 'Prendre une décision',
    free: false,
    fields: [
      { name: 'optionA', label: 'Option A' },
      { name: 'optionB', label: 'Option B' },
      { name: 'contexte', label: 'Contexte', type: 'textarea' },
    ],
    build: (v) => rctf({
      role: 'tu es un conseiller neutre et structuré.',
      context: `je dois choisir entre « ${or(v.optionA, '[A]')} » et « ${or(v.optionB, '[B]')} ». Contexte : ${or(v.contexte, '[contexte]')}`,
      task: 'compare les deux options sans trancher à ma place, puis pose-moi les 3 questions qui m’aideraient le plus à décider.',
      format: 'un tableau avantages / inconvénients / risques / coût, puis les 3 questions.',
    }),
  },
  // --- Chiffres ---------------------------------------------------------
  {
    id: 'formule',
    category: 'Tableurs et chiffres',
    title: 'Obtenir une formule Excel / Sheets',
    free: true,
    fields: [
      { name: 'logiciel', label: 'Logiciel et langue', placeholder: 'ex : Excel en français, Google Sheets' },
      { name: 'colonnes', label: 'Vos colonnes', type: 'textarea', placeholder: 'ex : A = dates, B = montants, C = statut' },
      { name: 'resultat', label: 'Résultat voulu', placeholder: 'ex : total des factures en attente de plus de 30 jours' },
    ],
    build: (v) => rctf({
      role: 'tu es un formateur Excel et Google Sheets patient.',
      context: `j'utilise ${or(v.logiciel, 'Excel en français')}. Mes données : ${or(v.colonnes, '[colonnes]')}`,
      task: `donne-moi la formule qui calcule : ${or(v.resultat, '[résultat voulu]')}. Utilise les noms de fonctions et le séparateur d'arguments correspondant à mon logiciel et à sa langue.`,
      format: 'la formule seule sur une ligne, puis l’explication de chaque partie, puis un exemple de vérification sur 2 lignes.',
    }),
  },
  {
    id: 'analyse-ventes',
    category: 'Tableurs et chiffres',
    title: 'Analyser des chiffres de ventes',
    free: false,
    fields: [{ name: 'donnees', label: 'Vos chiffres (tableau collé)', type: 'textarea' }],
    build: (v) => rctf({
      role: 'tu es un analyste qui explique simplement.',
      context: `voici mes chiffres : ${or(v.donnees, '[données]')}`,
      task: 'décris les tendances, repère les valeurs anormales et propose 3 hypothèses pour les expliquer. Ne fais pas de calcul complexe de tête : donne-moi plutôt les formules à utiliser dans mon tableur.',
      format: 'points clés, valeurs anormales, hypothèses, graphique conseillé.',
    }),
  },
  {
    id: 'prix-marge',
    category: 'Tableurs et chiffres',
    title: 'Calculer un prix de vente',
    free: false,
    fields: [
      { name: 'couts', label: 'Vos coûts', type: 'textarea' },
      { name: 'marge', label: 'Marge visée', placeholder: 'ex : 35 %' },
    ],
    build: (v) => rctf({
      role: 'tu es un conseiller en gestion pour petites entreprises.',
      context: `mes coûts : ${or(v.couts, '[coûts]')}. Marge visée : ${or(v.marge, '[marge]')}.`,
      task: 'explique la méthode pour calculer le prix de vente minimum, en distinguant marge et taux de marque, et donne les formules de tableur pour le faire moi-même.',
      format: 'étapes numérotées, formules, puis un tableau modèle à recopier.',
      extra: 'Rappelle que les résultats doivent être vérifiés avec un expert-comptable avant toute décision importante.',
    }),
  },
];

const byId = new Map(templates.map((t) => [t.id, t]));

// Public metadata (no prompt-building logic) for the browser.
function publicCatalog() {
  return templates.map(({ id, category, title, free, fields }) => ({ id, category, title, free, fields }));
}

function generate(templateId, rawValues, { pro }) {
  const tpl = byId.get(templateId);
  if (!tpl) return { error: 'Modèle inconnu.', status: 404 };
  if (!tpl.free && !pro) return { error: 'Ce modèle fait partie de la version Pro.', status: 402 };
  const values = {};
  for (const f of tpl.fields) {
    const raw = rawValues && typeof rawValues[f.name] === 'string' ? rawValues[f.name] : '';
    values[f.name] = raw.trim().slice(0, MAX_FIELD);
  }
  return { prompt: tpl.build(values) };
}

module.exports = { templates, publicCatalog, generate, FREE_DAILY_LIMIT: 5 };
