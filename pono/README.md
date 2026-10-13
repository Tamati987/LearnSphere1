# PONO — site + assistant

## Structure
```
server.js          serveur (site, formulaire, assistant IA, admin)
public/            pages du site (index, confidentialité, mentions légales, admin)
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
