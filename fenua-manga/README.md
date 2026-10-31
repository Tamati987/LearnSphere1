# FENUA MANGA

Code source du site https://fenuamanga.up.railway.app (service Railway `fare-aai`, projet `boutique-numerique`).

## Déploiement

Railway déploie ce dossier automatiquement à chaque push sur la branche suivie
(dossier racine `fenua-manga`, commande `bun run index.ts`).

Les images, PDF et visuels de t-shirts vivent sur le volume Railway monté sur `/data`.

## Lancer en local

```sh
bun install
DATA_DIR=./data bun run start
```
