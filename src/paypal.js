'use strict';

// Minimal PayPal Orders v2 client (create / capture / get) using fetch.

class PayPalError extends Error {
  constructor(message, status, details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

function createPayPalClient({ apiBase, clientId, clientSecret }, fetchImpl = globalThis.fetch) {
  let cachedToken = null;
  let tokenExpiresAt = 0;

  async function getAccessToken() {
    if (cachedToken && Date.now() < tokenExpiresAt) return cachedToken;
    const auth = Buffer.from(`${clientId}:${clientSecret}`).toString('base64');
    const res = await fetchImpl(`${apiBase}/v1/oauth2/token`, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${auth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: 'grant_type=client_credentials',
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok || !data.access_token) {
      throw new PayPalError('Authentification PayPal impossible', res.status, data);
    }
    cachedToken = data.access_token;
    // Refresh one minute before PayPal's expiry.
    tokenExpiresAt = Date.now() + Math.max(0, (Number(data.expires_in) || 300) - 60) * 1000;
    return cachedToken;
  }

  async function call(method, path, body, extraHeaders = {}) {
    const token = await getAccessToken();
    const res = await fetchImpl(`${apiBase}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        ...extraHeaders,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new PayPalError(`PayPal ${method} ${path} a échoué`, res.status, data);
    }
    return data;
  }

  return {
    createOrder({ referenceId, description, value, currency }) {
      return call('POST', '/v2/checkout/orders', {
        intent: 'CAPTURE',
        purchase_units: [
          {
            reference_id: referenceId,
            custom_id: referenceId,
            description: description.slice(0, 127),
            amount: { currency_code: currency, value },
          },
        ],
      });
    },
    captureOrder(orderId) {
      return call('POST', `/v2/checkout/orders/${encodeURIComponent(orderId)}/capture`, undefined, {
        'PayPal-Request-Id': `capture-${orderId}`,
      });
    },
    getOrder(orderId) {
      return call('GET', `/v2/checkout/orders/${encodeURIComponent(orderId)}`);
    },
  };
}

// Returns { ok, captureId, email, reason } after checking that the order was
// really paid, for the expected amount and currency.
function verifyPaidOrder(order, { value, currency, referenceId }) {
  if (!order || order.status !== 'COMPLETED') {
    return { ok: false, reason: `statut de commande ${order && order.status}` };
  }
  const unit = (order.purchase_units || [])[0] || {};
  if (referenceId && unit.reference_id && unit.reference_id !== referenceId) {
    return { ok: false, reason: 'référence de commande inattendue' };
  }
  const capture = ((unit.payments || {}).captures || []).find((c) => c.status === 'COMPLETED');
  if (!capture) return { ok: false, reason: 'aucune capture terminée' };
  const amount = capture.amount || {};
  if (amount.currency_code !== currency || Number(amount.value).toFixed(2) !== value) {
    return { ok: false, reason: `montant inattendu ${amount.value} ${amount.currency_code}` };
  }
  const email = ((order.payer || {}).email_address || '').toLowerCase();
  return { ok: true, captureId: capture.id, email };
}

module.exports = { createPayPalClient, verifyPaidOrder, PayPalError };
