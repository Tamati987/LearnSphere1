# LearnSphere

Boutique en ligne d'une formation numérique (« Kit IA Pro — L'IA au travail en 7 modules »),
avec paiement PayPal et accès instantané au contenu.

## Fonctionnement

1. Le visiteur coche la case CGV + renonciation au droit de rétractation (obligatoire en France pour un contenu numérique livré immédiatement).
2. Il paie via PayPal (compte PayPal ou carte bancaire).
3. Le serveur **capture et vérifie** le paiement auprès de PayPal (statut, montant, devise, référence) avant de délivrer quoi que ce soit.
4. L'acheteur reçoit un lien d'accès personnel `/cours/<jeton>` (le jeton est stocké haché en base).
5. Lien perdu ? Page `/retrouver` : email PayPal + n° de commande ou de transaction → nouveau lien (l'ancien est révoqué). Cette page finalise aussi une commande payée dont le navigateur a été fermé avant la confirmation.

**Sécurité :** tant que l'identité légale du vendeur et les clés PayPal ne sont pas toutes renseignées, la boutique affiche « Bientôt disponible » et refuse toute commande.

## Lancer en local

```bash
npm install
npm test          # 13 tests (parcours d'achat complet avec PayPal simulé)
npm start         # http://localhost:3000 (stockage en mémoire sans DATABASE_URL)
```

## Mise en ligne (Railway)

1. Créer un service depuis ce dépôt GitHub + ajouter une base **PostgreSQL** (la table est créée automatiquement).
2. Variables : copier `.env.example` ; `DATABASE_URL=${{Postgres.DATABASE_URL}}`.
3. Tester d'abord avec `PAYPAL_ENV=sandbox` et un compte acheteur sandbox, puis passer en `live` avec les clés live.

## Structure

- `src/app.js` — routes, paiement, accès, récupération
- `src/paypal.js` — client PayPal Orders v2 + vérification du paiement
- `src/store.js` — stockage Postgres / mémoire
- `src/content.js` — contenu de la formation
- `src/views.js` — pages HTML (accueil, cours, CGV, mentions légales, confidentialité)
