'use strict';

const crypto = require('node:crypto');
const path = require('node:path');
const express = require('express');
const helmet = require('helmet');
const { rateLimit } = require('express-rate-limit');
const views = require('./views');
const { verifyPaidOrder, PayPalError } = require('./paypal');
const tool = require('./tool');

const TOKEN_RE = /^[a-f0-9]{64}$/;
const PAYPAL_ID_RE = /^[A-Z0-9]{8,32}$/;

function newToken() {
  return crypto.randomBytes(32).toString('hex');
}

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

function createApp({ config, store, paypal, logger = console }) {
  const app = express();
  app.set('trust proxy', 1);
  app.disable('x-powered-by');

  app.use(
    helmet({
      contentSecurityPolicy: {
        directives: {
          defaultSrc: ["'self'"],
          scriptSrc: ["'self'", 'https://www.paypal.com', 'https://*.paypal.com'],
          styleSrc: ["'self'", "'unsafe-inline'"],
          imgSrc: ["'self'", 'data:', 'https://images.unsplash.com', 'https://*.paypal.com', 'https://*.paypalobjects.com'],
          connectSrc: ["'self'", 'https://*.paypal.com'],
          frameSrc: ['https://*.paypal.com'],
          formAction: ["'self'"],
          upgradeInsecureRequests: config.production ? [] : null,
        },
      },
      // PayPal's popup checkout needs to talk back to the opener window.
      crossOriginOpenerPolicy: { policy: 'same-origin-allow-popups' },
      referrerPolicy: { policy: 'no-referrer' },
    }),
  );

  app.use(express.static(path.join(__dirname, '..', 'public'), { maxAge: '1h' }));
  app.use(express.json({ limit: '16kb' }));
  app.use(express.urlencoded({ extended: false, limit: '2kb' }));

  const apiLimiter = rateLimit({ windowMs: 15 * 60 * 1000, limit: 60, standardHeaders: 'draft-7', legacyHeaders: false });
  const recoverLimiter = rateLimit({ windowMs: 15 * 60 * 1000, limit: 10, standardHeaders: 'draft-7', legacyHeaders: false });

  const toolLimiter = rateLimit({
    windowMs: 24 * 60 * 60 * 1000,
    limit: tool.FREE_DAILY_LIMIT,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: (req) => req.isPro === true,
    handler: (req, res) => res.status(429).json({
      error: `Limite gratuite atteinte (${tool.FREE_DAILY_LIMIT} demandes par jour). Passez à la version Pro, incluse avec le Kit IA Pro, pour un usage illimité.`,
      upsell: true,
    }),
  });

  const baseUrl = (req) => config.siteUrl || `${req.protocol}://${req.get('host')}`;
  const html = (res, status, body) => res.status(status).type('html').send(body);

  app.get('/health', (req, res) => res.json({ ok: true }));

  app.get('/', (req, res) => html(res, 200, views.home(config)));

  for (const page of ['mentions-legales', 'cgv', 'confidentialite']) {
    app.get(`/${page}`, (req, res) => html(res, 200, views.legal(config, page)));
  }

  // --- Checkout -----------------------------------------------------------

  app.post('/api/orders', apiLimiter, async (req, res) => {
    if (!config.salesEnabled) return res.status(503).json({ error: 'Les ventes ne sont pas encore ouvertes.' });
    if (!req.body || req.body.waiver !== true) {
      return res.status(400).json({ error: 'Vous devez accepter les CGV et la renonciation au droit de rétractation.' });
    }
    try {
      const referenceId = crypto.randomUUID();
      const order = await paypal.createOrder({
        referenceId,
        description: config.product.name,
        value: config.product.price,
        currency: config.product.currency,
      });
      await store.createPending({
        paypalOrderId: order.id,
        referenceId,
        amount: config.product.price,
        currency: config.product.currency,
        waiverAt: new Date(),
      });
      res.json({ id: order.id });
    } catch (err) {
      logger.error('create order failed', err.message, err.details || '');
      res.status(502).json({ error: 'Impossible de créer la commande. Réessayez dans un instant.' });
    }
  });

  app.post('/api/orders/:id/capture', apiLimiter, async (req, res) => {
    const id = req.params.id;
    if (!PAYPAL_ID_RE.test(id)) return res.status(400).json({ error: 'Commande invalide.' });
    try {
      const row = await store.getByOrderId(id);
      if (!row) return res.status(404).json({ error: 'Commande inconnue.' });
      if (row.status === 'paid') {
        return res.status(409).json({ error: 'Commande déjà payée : utilisez « Retrouver mon accès ».', recover: true });
      }

      let order;
      try {
        order = await paypal.captureOrder(id);
      } catch (err) {
        const issue = err instanceof PayPalError && ((err.details || {}).details || [])[0];
        if (issue && issue.issue === 'ORDER_ALREADY_CAPTURED') {
          order = await paypal.getOrder(id);
        } else if (issue && issue.issue === 'INSTRUMENT_DECLINED') {
          return res.status(402).json({ error: 'Paiement refusé par PayPal.', restart: true });
        } else {
          throw err;
        }
      }

      const check = verifyPaidOrder(order, {
        value: row.amount,
        currency: row.currency,
        referenceId: row.reference_id,
      });
      if (!check.ok) {
        logger.error('payment verification failed', id, check.reason);
        return res.status(402).json({ error: "Le paiement n'a pas pu être confirmé." });
      }

      const token = newToken();
      await store.markPaid(id, { captureId: check.captureId, email: check.email, tokenHash: hashToken(token) });
      logger.log('order paid', id);
      res.json({ token });
    } catch (err) {
      logger.error('capture failed', id, err.message, err.details || '');
      res.status(502).json({ error: 'Erreur lors de la confirmation du paiement. Si vous avez été débité, utilisez « Retrouver mon accès ».' });
    }
  });

  // Landing page shown right after payment (token comes from the capture call).
  app.get('/acces/:token', requireAccess, (req, res) => {
    html(res, 200, views.accessGranted(config, req.params.token, baseUrl(req)));
  });

  // --- Course access ------------------------------------------------------

  async function requireAccess(req, res, next) {
    const token = req.params.token;
    res.set('X-Robots-Tag', 'noindex');
    res.set('Cache-Control', 'private, no-store');
    try {
      const row = TOKEN_RE.test(token) ? await store.findPaidByTokenHash(hashToken(token)) : null;
      if (!row) {
        return html(res, 404, views.message(config, 'Lien invalide', "Ce lien d'accès n'est pas valide ou a été remplacé. Utilisez « Retrouver mon accès »."));
      }
      next();
    } catch (err) {
      next(err);
    }
  }

  // --- Prompt generator -------------------------------------------------

  app.get('/outil', (req, res) => html(res, 200, views.toolPage(config)));

  app.get('/cours/:token/outil', requireAccess, (req, res) => html(res, 200, views.toolPage(config, { token: req.params.token })));

  async function resolvePro(req, res, next) {
    const token = req.body && typeof req.body.token === 'string' ? req.body.token : '';
    try {
      req.isPro = TOKEN_RE.test(token) && Boolean(await store.findPaidByTokenHash(hashToken(token)));
      next();
    } catch (err) {
      next(err);
    }
  }

  // Pro-only templates are refused before the free quota is counted.
  function checkTier(req, res, next) {
    const tpl = tool.templates.find((t) => t.id === String((req.body || {}).templateId || ''));
    if (!tpl) return res.status(404).json({ error: 'Modèle inconnu.' });
    if (!tpl.free && !req.isPro) return res.status(402).json({ error: 'Ce modèle fait partie de la version Pro.', upsell: true });
    next();
  }

  app.post('/api/outil/generer', apiLimiter, resolvePro, checkTier, toolLimiter, (req, res) => {
    const body = req.body || {};
    const result = tool.generate(String(body.templateId || ''), body.values, { pro: req.isPro });
    if (result.error) return res.status(result.status).json({ error: result.error, upsell: result.status === 402 });
    res.json({ prompt: result.prompt });
  });

  app.get('/cours/:token', requireAccess, (req, res) => html(res, 200, views.courseIndex(config, req.params.token)));

  app.get('/cours/:token/:slug', requireAccess, (req, res) => {
    const page = views.courseModule(config, req.params.token, req.params.slug);
    if (!page) return html(res, 404, views.message(config, 'Introuvable', "Ce module n'existe pas."));
    html(res, 200, page);
  });

  // --- Access recovery ----------------------------------------------------

  app.get('/retrouver', (req, res) => html(res, 200, views.recoverForm(config)));

  app.post('/retrouver', recoverLimiter, async (req, res) => {
    const email = String((req.body || {}).email || '').trim().toLowerCase();
    const orderId = String((req.body || {}).orderId || '').trim().toUpperCase();
    const fail = () =>
      html(res, 400, views.recoverForm(config, {
        error: "Aucun achat ne correspond à ces informations. Vérifiez l'email PayPal et le numéro indiqué dans l'email de confirmation PayPal.",
        values: { email, orderId },
      }));

    if (!email || !PAYPAL_ID_RE.test(orderId)) return fail();
    try {
      let row = await store.findByOrderOrCaptureId(orderId);
      if (!row) return fail();

      if (row.status !== 'paid') {
        // The buyer may have paid while their browser lost the connection:
        // ask PayPal directly before giving up.
        if (!config.salesEnabled) return fail();
        let order;
        try {
          order = await paypal.getOrder(row.paypal_order_id);
        } catch (err) {
          logger.error('recover getOrder failed', row.paypal_order_id, err.message);
          return fail();
        }
        const check = verifyPaidOrder(order, { value: row.amount, currency: row.currency, referenceId: row.reference_id });
        if (!check.ok) return fail();
        await store.markPaid(row.paypal_order_id, { captureId: check.captureId, email: check.email, tokenHash: null });
        row = await store.getByOrderId(row.paypal_order_id);
      }

      if (!row.payer_email || row.payer_email.toLowerCase() !== email) return fail();

      const token = newToken();
      await store.setTokenHash(row.paypal_order_id, hashToken(token));
      res.set('X-Robots-Tag', 'noindex');
      html(res, 200, views.accessGranted(config, token, baseUrl(req)));
    } catch (err) {
      logger.error('recover failed', err.message);
      html(res, 500, views.message(config, 'Erreur', 'Une erreur est survenue. Réessayez dans quelques minutes.'));
    }
  });

  app.use((req, res) => html(res, 404, views.message(config, 'Page introuvable', "Cette page n'existe pas.")));

  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    logger.error('unhandled error', err && err.message);
    if (res.headersSent) return;
    if (err && err.type === 'entity.parse.failed') return res.status(400).json({ error: 'Requête invalide.' });
    html(res, 500, views.message(config, 'Erreur', 'Une erreur est survenue.'));
  });

  return app;
}

module.exports = { createApp, hashToken };
