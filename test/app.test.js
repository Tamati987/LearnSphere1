'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { loadConfig } = require('../src/config');
const { createApp } = require('../src/app');
const { createMemoryStore } = require('../src/store');
const { PayPalError, verifyPaidOrder, createPayPalClient } = require('../src/paypal');
const { modules } = require('../src/content');

const FULL_ENV = {
  PAYPAL_CLIENT_ID: 'cid',
  PAYPAL_CLIENT_SECRET: 'secret',
  SELLER_NAME: 'Jean Test',
  SELLER_STATUS: 'Entrepreneur individuel (micro-entreprise)',
  SELLER_ADDRESS: '1 rue du Test, 75001 Paris',
  SELLER_EMAIL: 'contact@example.com',
  SELLER_SIRET: '12345678900011',
  SELLER_MEDIATOR: 'Médiateur Test, https://example.org',
};

// Fake PayPal: orders get paid when captured, with a configurable amount.
function fakePayPal({ paidValue = '27.00', email = 'Buyer@Example.com' } = {}) {
  const orders = new Map();
  let n = 0;
  const paid = (o) => ({
    id: o.id,
    status: 'COMPLETED',
    payer: { email_address: email },
    purchase_units: [{
      reference_id: o.referenceId,
      payments: { captures: [{ id: `CAP${o.id}`, status: 'COMPLETED', amount: { value: paidValue, currency_code: 'EUR' } }] },
    }],
  });
  return {
    orders,
    async createOrder({ referenceId }) {
      n += 1;
      const id = `ORDER${String(n).padStart(10, '0')}`;
      orders.set(id, { id, referenceId, captured: false });
      return { id, status: 'CREATED' };
    },
    async captureOrder(id) {
      const o = orders.get(id);
      if (o.captured) throw new PayPalError('x', 422, { details: [{ issue: 'ORDER_ALREADY_CAPTURED' }] });
      o.captured = true;
      return paid(o);
    },
    async getOrder(id) {
      const o = orders.get(id);
      return o.captured ? paid(o) : { id, status: 'APPROVED', purchase_units: [{ reference_id: o.referenceId }] };
    },
  };
}

async function start(env = FULL_ENV, paypal = fakePayPal()) {
  const config = loadConfig(env);
  const store = createMemoryStore();
  const silent = { log() {}, error() {}, warn() {} };
  const app = createApp({ config, store, paypal, logger: silent });
  const server = await new Promise((resolve) => {
    const s = app.listen(0, () => resolve(s));
  });
  const base = `http://127.0.0.1:${server.address().port}`;
  const req = (path, opts = {}) => fetch(base + path, { redirect: 'manual', ...opts });
  const post = (path, body) => req(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const form = (path, data) => req(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(data).toString(),
  });
  return { server, base, req, post, form, store, paypal, close: () => new Promise((r) => server.close(r)) };
}

test('sales stay closed until legal and PayPal settings are present', async () => {
  const t = await start({});
  try {
    const home = await (await t.req('/')).text();
    assert.match(home, /Bientôt disponible/);
    assert.doesNotMatch(home, /paypal\.com\/sdk/);
    const res = await t.post('/api/orders', { waiver: true });
    assert.equal(res.status, 503);
  } finally {
    await t.close();
  }
});

test('home page offers checkout and legal pages render seller info', async () => {
  const t = await start();
  try {
    const res = await t.req('/');
    assert.equal(res.status, 200);
    const home = await res.text();
    assert.match(home, /paypal\.com\/sdk\/js\?client-id=cid&currency=EUR/);
    assert.match(home, /27 €/);
    assert.match(home, /Mode test PayPal/);
    assert.match(res.headers.get('content-security-policy'), /frame-src https:\/\/\*\.paypal\.com/);
    for (const page of ['/mentions-legales', '/cgv', '/confidentialite']) {
      const html = await (await t.req(page)).text();
      assert.match(html, /Jean Test/);
      assert.match(html, /12345678900011/);
      assert.doesNotMatch(html, /À RENSEIGNER/);
    }
  } finally {
    await t.close();
  }
});

test('order requires explicit withdrawal waiver', async () => {
  const t = await start();
  try {
    assert.equal((await t.post('/api/orders', {})).status, 400);
    assert.equal((await t.post('/api/orders', { waiver: 'true' })).status, 400);
    assert.equal(t.paypal.orders.size, 0);
  } finally {
    await t.close();
  }
});

test('full purchase flow grants access to every module', async () => {
  const t = await start();
  try {
    const created = await t.post('/api/orders', { waiver: true });
    assert.equal(created.status, 200);
    const { id } = await created.json();
    const row = await t.store.getByOrderId(id);
    assert.equal(row.status, 'pending');
    assert.ok(row.waiver_at instanceof Date);

    const cap = await t.post(`/api/orders/${id}/capture`);
    assert.equal(cap.status, 200);
    const { token } = await cap.json();
    assert.match(token, /^[a-f0-9]{64}$/);

    const paidRow = await t.store.getByOrderId(id);
    assert.equal(paidRow.status, 'paid');
    assert.equal(paidRow.payer_email, 'buyer@example.com');
    assert.notEqual(paidRow.token_hash, token, 'token must be stored hashed');

    const access = await t.req(`/acces/${token}`);
    assert.equal(access.status, 200);
    assert.match(await access.text(), new RegExp(`/cours/${token}`));

    const index = await t.req(`/cours/${token}`);
    assert.equal(index.status, 200);
    assert.equal(index.headers.get('x-robots-tag'), 'noindex');
    for (const m of modules) {
      const page = await t.req(`/cours/${token}/${m.slug}`);
      assert.equal(page.status, 200, m.slug);
    }
    assert.equal((await t.req(`/cours/${token}/nope`)).status, 404);

    // Second capture of the same order does not hand out a new token.
    const again = await t.post(`/api/orders/${id}/capture`);
    assert.equal(again.status, 409);
  } finally {
    await t.close();
  }
});

test('invalid or forged tokens are refused', async () => {
  const t = await start();
  try {
    assert.equal((await t.req(`/cours/${'a'.repeat(64)}`)).status, 404);
    assert.equal((await t.req(`/acces/${'b'.repeat(64)}`)).status, 404);
    assert.equal((await t.req('/cours/hello')).status, 404);
    assert.equal((await t.req(`/cours/${'a'.repeat(64)}/${modules[0].slug}`)).status, 404);
  } finally {
    await t.close();
  }
});

test('payment with the wrong amount is rejected', async () => {
  const t = await start(FULL_ENV, fakePayPal({ paidValue: '1.00' }));
  try {
    const { id } = await (await t.post('/api/orders', { waiver: true })).json();
    const cap = await t.post(`/api/orders/${id}/capture`);
    assert.equal(cap.status, 402);
    assert.equal((await t.store.getByOrderId(id)).status, 'pending');
  } finally {
    await t.close();
  }
});

test('unknown order ids cannot be captured', async () => {
  const t = await start();
  try {
    assert.equal((await t.post('/api/orders/ORDER9999999999/capture')).status, 404);
    assert.equal((await t.post('/api/orders/bad%20id/capture')).status, 400);
  } finally {
    await t.close();
  }
});

test('recovery issues a new link and revokes the old one', async () => {
  const t = await start();
  try {
    const { id } = await (await t.post('/api/orders', { waiver: true })).json();
    const { token } = await (await t.post(`/api/orders/${id}/capture`)).json();

    const wrong = await t.form('/retrouver', { email: 'other@example.com', orderId: id });
    assert.equal(wrong.status, 400);

    // Works with the capture (transaction) id shown in PayPal's receipt email.
    const ok = await t.form('/retrouver', { email: 'BUYER@example.com', orderId: `CAP${id}` });
    assert.equal(ok.status, 200);
    const html = await ok.text();
    const newToken = html.match(/\/cours\/([a-f0-9]{64})/)[1];
    assert.notEqual(newToken, token);
    assert.equal((await t.req(`/cours/${newToken}`)).status, 200);
    assert.equal((await t.req(`/cours/${token}`)).status, 404);
  } finally {
    await t.close();
  }
});

test('recovery finishes an order paid while the browser was closed', async () => {
  const t = await start();
  try {
    const { id } = await (await t.post('/api/orders', { waiver: true })).json();
    // Captured at PayPal, but our capture endpoint was never reached.
    await t.paypal.captureOrder(id);
    assert.equal((await t.store.getByOrderId(id)).status, 'pending');

    const ok = await t.form('/retrouver', { email: 'buyer@example.com', orderId: id });
    assert.equal(ok.status, 200);
    assert.equal((await t.store.getByOrderId(id)).status, 'paid');

    // An unpaid pending order cannot be recovered.
    const { id: id2 } = await (await t.post('/api/orders', { waiver: true })).json();
    assert.equal((await t.form('/retrouver', { email: 'buyer@example.com', orderId: id2 })).status, 400);
  } finally {
    await t.close();
  }
});

test('capture tolerates ORDER_ALREADY_CAPTURED from PayPal', async () => {
  const t = await start();
  try {
    const { id } = await (await t.post('/api/orders', { waiver: true })).json();
    await t.paypal.captureOrder(id); // first capture happened elsewhere (e.g. retry)
    const cap = await t.post(`/api/orders/${id}/capture`);
    assert.equal(cap.status, 200);
    assert.match((await cap.json()).token, /^[a-f0-9]{64}$/);
  } finally {
    await t.close();
  }
});

test('verifyPaidOrder checks status, reference, amount and currency', () => {
  const order = {
    status: 'COMPLETED',
    payer: { email_address: 'A@B.fr' },
    purchase_units: [{ reference_id: 'r1', payments: { captures: [{ id: 'C1', status: 'COMPLETED', amount: { value: '27.00', currency_code: 'EUR' } }] } }],
  };
  const exp = { value: '27.00', currency: 'EUR', referenceId: 'r1' };
  assert.deepEqual(verifyPaidOrder(order, exp), { ok: true, captureId: 'C1', email: 'a@b.fr' });
  assert.equal(verifyPaidOrder({ ...order, status: 'APPROVED' }, exp).ok, false);
  assert.equal(verifyPaidOrder(order, { ...exp, referenceId: 'r2' }).ok, false);
  assert.equal(verifyPaidOrder(order, { ...exp, currency: 'USD' }).ok, false);
  assert.equal(verifyPaidOrder(order, { ...exp, value: '30.00' }).ok, false);
});

test('PayPal client authenticates and sends idempotent capture', async () => {
  const calls = [];
  const fetchImpl = async (url, opts) => {
    calls.push({ url, opts });
    if (url.endsWith('/v1/oauth2/token')) return { ok: true, status: 200, json: async () => ({ access_token: 'tok', expires_in: 3600 }) };
    return { ok: true, status: 201, json: async () => ({ id: 'ORDER1', status: 'COMPLETED' }) };
  };
  const client = createPayPalClient({ apiBase: 'https://api-m.sandbox.paypal.com', clientId: 'id', clientSecret: 'sec' }, fetchImpl);
  await client.createOrder({ referenceId: 'r', description: 'Kit', value: '27.00', currency: 'EUR' });
  await client.captureOrder('ORDER1');
  assert.equal(calls.filter((c) => c.url.endsWith('/oauth2/token')).length, 1, 'token is cached');
  const create = JSON.parse(calls[1].opts.body);
  assert.deepEqual(create.purchase_units[0].amount, { currency_code: 'EUR', value: '27.00' });
  assert.equal(create.intent, 'CAPTURE');
  assert.equal(calls[2].opts.headers['PayPal-Request-Id'], 'capture-ORDER1');
  assert.equal(calls[2].opts.headers.Authorization, 'Bearer tok');
});

test('config rejects malformed prices and PayPal environments', () => {
  assert.throws(() => loadConfig({ PRODUCT_PRICE: '27,00' }));
  assert.throws(() => loadConfig({ PAYPAL_ENV: 'prod' }));
  assert.equal(loadConfig({ PRODUCT_PRICE: '19' }).product.price, '19.00');
  assert.equal(loadConfig({ PAYPAL_ENV: 'live' }).paypal.apiBase, 'https://api-m.paypal.com');
});

const tool = require('../src/tool');

test('generator: free templates work, Pro ones are refused, daily limit applies', async () => {
  const t = await start();
  try {
    const page = await (await t.req('/outil')).text();
    assert.match(page, /Générateur de demandes IA/);
    assert.match(page, /id="catalog"/);
    assert.doesNotMatch(page, /Rôle : /, 'prompt-building logic is not shipped to the browser');

    const free = tool.templates.find((x) => x.free);
    const paid = tool.templates.find((x) => !x.free);

    const locked = await t.post('/api/outil/generer', { templateId: paid.id, values: {} });
    assert.equal(locked.status, 402);

    const unknown = await t.post('/api/outil/generer', { templateId: 'nope', values: {} });
    assert.equal(unknown.status, 404);

    for (let i = 0; i < tool.FREE_DAILY_LIMIT; i++) {
      const ok = await t.post('/api/outil/generer', { templateId: free.id, values: { [free.fields[0].name]: 'boulangerie <b>' } });
      assert.equal(ok.status, 200, `free call ${i + 1}`);
      const { prompt } = await ok.json();
      assert.match(prompt, /^Rôle : /);
      assert.match(prompt, /Format : /);
    }
    const over = await t.post('/api/outil/generer', { templateId: free.id, values: {} });
    assert.equal(over.status, 429);
    assert.equal((await over.json()).upsell, true);
  } finally {
    await t.close();
  }
});

test('generator: buyers get every template without limit', async () => {
  const t = await start();
  try {
    const { id } = await (await t.post('/api/orders', { waiver: true })).json();
    const { token } = await (await t.post(`/api/orders/${id}/capture`)).json();

    const page = await t.req(`/cours/${token}/outil`);
    assert.equal(page.status, 200);
    assert.match(await page.text(), /Version Pro/);
    assert.equal((await t.req(`/cours/${'c'.repeat(64)}/outil`)).status, 404);

    for (let i = 0; i < tool.FREE_DAILY_LIMIT + 3; i++) {
      for (const tpl of tool.templates.slice(0, 2)) {
        const r = await t.post('/api/outil/generer', { templateId: tpl.id, values: {}, token });
        assert.equal(r.status, 200);
      }
    }
    const paid = tool.templates.find((x) => !x.free);
    assert.equal((await t.post('/api/outil/generer', { templateId: paid.id, values: {}, token: 'f'.repeat(64) })).status, 402);
  } finally {
    await t.close();
  }
});

test('generator: every template builds a complete prompt and caps field length', () => {
  assert.ok(tool.templates.length >= 15);
  const ids = new Set();
  for (const tpl of tool.templates) {
    assert.ok(!ids.has(tpl.id), `duplicate id ${tpl.id}`);
    ids.add(tpl.id);
    const values = Object.fromEntries(tpl.fields.map((f) => [f.name, 'x'.repeat(5000)]));
    const { prompt } = tool.generate(tpl.id, values, { pro: true });
    for (const part of ['Rôle : ', 'Contexte : ', 'Tâche : ', 'Format : ']) assert.ok(prompt.includes(part), `${tpl.id} ${part}`);
    assert.doesNotMatch(prompt, /x{1501}/, `${tpl.id} must cap fields`);
    const empty = tool.generate(tpl.id, {}, { pro: true }).prompt;
    assert.doesNotMatch(empty, /undefined/, `${tpl.id} handles empty values`);
  }
});

test('home page shows photos from the allowed image CDN', async () => {
  const t = await start();
  try {
    const res = await t.req('/');
    const html = await res.text();
    assert.equal((html.match(/<img /g) || []).length, 4);
    assert.match(html, /https:\/\/images\.unsplash\.com\/photo-/);
    assert.match(res.headers.get('content-security-policy'), /img-src[^;]*https:\/\/images\.unsplash\.com/);
    assert.match(html, /href="\/outil"/);
  } finally {
    await t.close();
  }
});
