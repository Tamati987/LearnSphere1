'use strict';

// All configuration comes from environment variables so that no secret or
// personal information ever lives in the repository.

const LEGAL_KEYS = ['SELLER_NAME', 'SELLER_STATUS', 'SELLER_ADDRESS', 'SELLER_EMAIL', 'SELLER_SIRET', 'SELLER_MEDIATOR'];
const PAYPAL_KEYS = ['PAYPAL_CLIENT_ID', 'PAYPAL_CLIENT_SECRET'];

function parsePrice(raw) {
  const value = String(raw ?? '27.00').trim();
  if (!/^\d{1,5}(\.\d{2})?$/.test(value)) {
    throw new Error(`PRODUCT_PRICE invalide: "${value}" (format attendu: 27.00)`);
  }
  return Number(value).toFixed(2);
}

function loadConfig(env = process.env) {
  const paypalEnv = (env.PAYPAL_ENV || 'sandbox').toLowerCase();
  if (!['sandbox', 'live'].includes(paypalEnv)) {
    throw new Error('PAYPAL_ENV doit valoir "sandbox" ou "live"');
  }

  const missingLegal = LEGAL_KEYS.filter((k) => !env[k] || !env[k].trim());
  const missingPaypal = PAYPAL_KEYS.filter((k) => !env[k] || !env[k].trim());

  return {
    port: Number(env.PORT) || 3000,
    production: env.NODE_ENV === 'production',
    databaseUrl: env.DATABASE_URL || '',
    siteUrl: (env.SITE_URL || '').replace(/\/+$/, ''),
    product: {
      name: env.PRODUCT_NAME || "Kit IA Pro — L'IA au travail en 7 modules",
      price: parsePrice(env.PRODUCT_PRICE),
      currency: 'EUR',
    },
    paypal: {
      env: paypalEnv,
      clientId: env.PAYPAL_CLIENT_ID || '',
      clientSecret: env.PAYPAL_CLIENT_SECRET || '',
      apiBase: paypalEnv === 'live' ? 'https://api-m.paypal.com' : 'https://api-m.sandbox.paypal.com',
    },
    seller: {
      name: env.SELLER_NAME || '',
      status: env.SELLER_STATUS || '',
      address: env.SELLER_ADDRESS || '',
      email: env.SELLER_EMAIL || '',
      siret: env.SELLER_SIRET || '',
      mediator: env.SELLER_MEDIATOR || '',
      vatMention: env.SELLER_VAT_MENTION || 'TVA non applicable, art. 293 B du CGI',
    },
    missingLegal,
    missingPaypal,
    // Selling is only switched on once the seller is legally identified and
    // PayPal is configured; otherwise the shop shows a "bientôt disponible" state.
    salesEnabled: missingLegal.length === 0 && missingPaypal.length === 0,
  };
}

module.exports = { loadConfig };
