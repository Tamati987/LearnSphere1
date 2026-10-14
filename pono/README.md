# PONO — site + assistant

## Structure
```
server.js          serveur (site, formulaire, assistant IA, admin)
public/            pages du site (index, confidentialité, mentions légales, admin)
public/en/         version anglaise (/en, /en/privacy, /en/legal) — prix en USD
.env.example       variables à configurer
```

## Lancer en local
```
npm install
cp .env.example .env   # puis remplir
npm start              # http://localhost:3000
```

## Mise en ligne sur Railway
1. Pousser le dossier sur GitHub, puis créer le service Railway depuis le dépôt.
2. **Ajouter un volume** au service (clic droit sur le service → *Attach volume*, chemin de montage `/data`).
   Sans volume, les demandes de contact sont effacées à chaque déploiement.
3. Dans *Variables*, ajouter au minimum :
   - `ADMIN_TOKEN` (long secret, 24 caractères minimum)
   - `SITE_URL` (l'adresse publique du site)
   - `BUSINESS_EMAIL`, `BUSINESS_OWNER`, `BUSINESS_LEGAL_ID`, `BUSINESS_ADDRESS`
   - `OPENAI_API_KEY` si vous voulez la vraie IA (sinon : mode démo)
4. Vérifier : `https://votre-site/api/health` doit afficher `"storage":"persistent"`.

## Lire les demandes
Ouvrir `https://votre-site/admin` et saisir le `ADMIN_TOKEN`.
Vous pouvez répondre, appeler, supprimer une demande ou tout exporter en CSV.
Les demandes de plus de `RETENTION_DAYS` jours (365 par défaut) sont supprimées automatiquement.

## Protections en place
- Limite par visiteur : 30 messages de chat / 15 min, 8 demandes de contact / heure.
- Plafond IA quotidien (`AI_DAILY_LIMIT`). Au-delà, réponses de démo, sans coût.
- Anti-robot (honeypot), consentement vérifié côté serveur, en-têtes de sécurité (Helmet/CSP).

## Paiement par carte (Visa / Mastercard)
Le site n'enregistre jamais de numéro de carte : chaque bouton « Payer par carte »
ouvre la page sécurisée d'un prestataire de paiement, qui verse ensuite l'argent
sur votre IBAN.
1. Créez un lien de paiement par forfait chez votre prestataire.
2. Dans Railway → Variables, collez-les dans `PAY_LINK_STARTER`, `PAY_LINK_PRO`,
   `PAY_LINK_CUSTOM` (site français) et `PAY_LINK_*_EN` (site anglais, en USD).
3. Les boutons apparaissent automatiquement ; un lien vide (ou non https) reste masqué.
   `/api/health` indique combien de liens sont actifs.

## Espace client
Après un paiement, ouvrez `/admin` :
- **Créer son espace client** sur la demande du client (ou le formulaire « Espaces clients »).
- Copiez le lien affiché (`/espace/…`) et envoyez-le au client. Il n'est montré qu'une fois ;
  « Nouveau lien » en crée un autre et désactive l'ancien. « Suspendre » coupe l'accès.

Le client y trouve : son assistant (configuré avec ses informations), un outil pour préparer
ses réponses, le suivi de ses demandes, et pour Pro / Sur mesure les relances datées et
les modèles de messages. Les données sont dans `clients.json` (même volume que les demandes) ;
seule l'empreinte des liens est enregistrée.
