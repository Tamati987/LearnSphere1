(function () {
  'use strict';

  var waiver = document.getElementById('waiver');
  var container = document.getElementById('paypal-buttons');
  var errorBox = document.getElementById('pay-error');
  var rendered = false;

  function showError(text) {
    errorBox.textContent = text;
    errorBox.hidden = false;
  }

  function readJson(res) {
    return res.json().catch(function () { return {}; }).then(function (data) {
      if (!res.ok) {
        var err = new Error(data.error || 'Erreur inattendue.');
        err.data = data;
        throw err;
      }
      return data;
    });
  }

  function render() {
    if (rendered) return;
    if (!window.paypal || !window.paypal.Buttons) {
      showError('Le module de paiement PayPal ne s\'est pas chargé. Désactivez un éventuel bloqueur de publicité puis rechargez la page.');
      return;
    }
    rendered = true;
    container.innerHTML = '';
    window.paypal.Buttons({
      style: { layout: 'vertical', shape: 'rect', label: 'pay' },
      onClick: function (data, actions) {
        errorBox.hidden = true;
        return waiver.checked ? actions.resolve() : actions.reject();
      },
      createOrder: function () {
        return fetch('/api/orders', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ waiver: waiver.checked === true }),
        }).then(readJson).then(function (data) { return data.id; });
      },
      onApprove: function (data, actions) {
        return fetch('/api/orders/' + encodeURIComponent(data.orderID) + '/capture', { method: 'POST' })
          .then(readJson)
          .then(function (result) {
            window.location.href = '/acces/' + result.token;
          })
          .catch(function (err) {
            if (err.data && err.data.restart) return actions.restart();
            if (err.data && err.data.recover) { window.location.href = '/retrouver'; return; }
            showError(err.message);
          });
      },
      onError: function (err) {
        showError((err && err.message) || 'Le paiement n\'a pas abouti. Réessayez dans un instant.');
      },
    }).render('#paypal-buttons');
  }

  waiver.addEventListener('change', function () {
    if (waiver.checked) render();
  });
  // The box may have been ticked before this script finished loading.
  if (waiver.checked) render();
})();
