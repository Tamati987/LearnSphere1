'use strict';

// Order storage. Postgres in production (DATABASE_URL), in-memory for local
// development and tests.

const SCHEMA = `
CREATE TABLE IF NOT EXISTS orders (
  paypal_order_id TEXT PRIMARY KEY,
  reference_id    TEXT NOT NULL,
  amount          TEXT NOT NULL,
  currency        TEXT NOT NULL,
  status          TEXT NOT NULL DEFAULT 'pending',
  waiver_at       TIMESTAMPTZ NOT NULL,
  capture_id      TEXT,
  payer_email     TEXT,
  token_hash      TEXT UNIQUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  paid_at         TIMESTAMPTZ
);
`;

function createMemoryStore() {
  const orders = new Map();
  return {
    kind: 'memory',
    async init() {},
    async createPending({ paypalOrderId, referenceId, amount, currency, waiverAt }) {
      orders.set(paypalOrderId, {
        paypal_order_id: paypalOrderId,
        reference_id: referenceId,
        amount,
        currency,
        status: 'pending',
        waiver_at: waiverAt,
        capture_id: null,
        payer_email: null,
        token_hash: null,
        created_at: new Date(),
        paid_at: null,
      });
    },
    async getByOrderId(id) {
      const o = orders.get(id);
      return o ? { ...o } : null;
    },
    async findByOrderOrCaptureId(id) {
      for (const o of orders.values()) {
        if (o.paypal_order_id === id || o.capture_id === id) return { ...o };
      }
      return null;
    },
    async markPaid(id, { captureId, email, tokenHash }) {
      const o = orders.get(id);
      if (!o) return false;
      Object.assign(o, { status: 'paid', capture_id: captureId, payer_email: email, token_hash: tokenHash, paid_at: new Date() });
      return true;
    },
    async setTokenHash(id, tokenHash) {
      const o = orders.get(id);
      if (!o || o.status !== 'paid') return false;
      o.token_hash = tokenHash;
      return true;
    },
    async findPaidByTokenHash(hash) {
      for (const o of orders.values()) {
        if (o.status === 'paid' && o.token_hash === hash) return { ...o };
      }
      return null;
    },
    async close() {},
  };
}

function createPgStore(databaseUrl) {
  const { Pool } = require('pg');
  const local = /@(localhost|127\.0\.0\.1)[:/]/.test(databaseUrl) || /\.railway\.internal[:/]/.test(databaseUrl);
  const pool = new Pool({
    connectionString: databaseUrl,
    ssl: local ? false : { rejectUnauthorized: false },
    max: 5,
  });
  return {
    kind: 'postgres',
    async init() {
      await pool.query(SCHEMA);
    },
    async createPending({ paypalOrderId, referenceId, amount, currency, waiverAt }) {
      await pool.query(
        `INSERT INTO orders (paypal_order_id, reference_id, amount, currency, waiver_at)
         VALUES ($1, $2, $3, $4, $5)`,
        [paypalOrderId, referenceId, amount, currency, waiverAt],
      );
    },
    async getByOrderId(id) {
      const { rows } = await pool.query('SELECT * FROM orders WHERE paypal_order_id = $1', [id]);
      return rows[0] || null;
    },
    async findByOrderOrCaptureId(id) {
      const { rows } = await pool.query(
        'SELECT * FROM orders WHERE paypal_order_id = $1 OR capture_id = $1 LIMIT 1',
        [id],
      );
      return rows[0] || null;
    },
    async markPaid(id, { captureId, email, tokenHash }) {
      const { rowCount } = await pool.query(
        `UPDATE orders SET status = 'paid', capture_id = $2, payer_email = $3, token_hash = $4, paid_at = now()
         WHERE paypal_order_id = $1`,
        [id, captureId, email, tokenHash],
      );
      return rowCount === 1;
    },
    async setTokenHash(id, tokenHash) {
      const { rowCount } = await pool.query(
        `UPDATE orders SET token_hash = $2 WHERE paypal_order_id = $1 AND status = 'paid'`,
        [id, tokenHash],
      );
      return rowCount === 1;
    },
    async findPaidByTokenHash(hash) {
      const { rows } = await pool.query(
        `SELECT * FROM orders WHERE token_hash = $1 AND status = 'paid'`,
        [hash],
      );
      return rows[0] || null;
    },
    async close() {
      await pool.end();
    },
  };
}

module.exports = { createMemoryStore, createPgStore, SCHEMA };
