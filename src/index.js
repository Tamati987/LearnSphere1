'use strict';

const { loadConfig } = require('./config');
const { createApp } = require('./app');
const { createMemoryStore, createPgStore } = require('./store');
const { createPayPalClient } = require('./paypal');

async function main() {
  const config = loadConfig();

  if (config.production && !config.databaseUrl) {
    throw new Error('DATABASE_URL est obligatoire en production (sinon les commandes seraient perdues au redémarrage).');
  }
  const store = config.databaseUrl ? createPgStore(config.databaseUrl) : createMemoryStore();
  await store.init();

  const paypal = createPayPalClient(config.paypal);
  const app = createApp({ config, store, paypal });

  if (!config.salesEnabled) {
    console.warn(
      'Ventes désactivées. Variables manquantes :',
      [...config.missingLegal, ...config.missingPaypal].join(', '),
    );
  }
  console.log(`Stockage : ${store.kind} · PayPal : ${config.paypal.env}`);

  const server = app.listen(config.port, () => console.log(`LearnSphere en écoute sur le port ${config.port}`));

  const shutdown = () => {
    server.close(() => store.close().finally(() => process.exit(0)));
  };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
