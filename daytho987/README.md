# DAYTHO 987 — boutique

Next.js 14 · Tailwind 3 · Stripe Checkout · Printful

## Parcours d'une commande

1. Le client choisit une taille → **Ajouter au panier** (panier gardé dans le navigateur).
2. **Payer** → `/api/checkout` recalcule les prix depuis `lib/products.ts` et ouvre **Stripe Checkout** (paiement + adresse + téléphone).
3. Paiement réussi → Stripe appelle `/api/webhooks/stripe` (signature vérifiée).
4. Le webhook crée la commande **Printful** avec la vraie adresse
   (brouillon si `PRINTFUL_AUTO_CONFIRM=false`, production sinon).
5. Le client arrive sur `/merci`.

## Mise en route

```bash
npm install
cp .env.example .env.local   # remplir les clés
npm run dev
```

### Relier tes produits Printful

1. Dans Printful, crée les produits du store DAYTHO 987 (design + tailles).
2. Récupère leurs identifiants :
   ```bash
   curl -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:3000/api/admin/printful-products
   ```
3. Recopie chaque `sync_variant_id` dans `lib/products.ts` (champ `printfulSyncVariantId`).
   Une taille à `0` s'affiche « Bientôt disponible ».

### Tester le webhook en local

```bash
stripe listen --forward-to localhost:3000/api/webhooks/stripe   # donne le whsec_... à mettre dans .env.local
```
Carte de test : `4242 4242 4242 4242`, date future, CVC quelconque.

### Railway

Root directory : `daytho987` · ajouter toutes les variables de `.env.example` dans le service.
