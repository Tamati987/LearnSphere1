(function () {
  'use strict';

  var root = document.getElementById('tool');
  var catalog = JSON.parse(document.getElementById('catalog').textContent);
  var pro = root.getAttribute('data-pro') === '1';
  var token = root.getAttribute('data-token') || '';
  var select = document.getElementById('tpl');
  var fieldsBox = document.getElementById('fields');
  var form = document.getElementById('tool-form');
  var out = document.getElementById('out');
  var copyBtn = document.getElementById('copy');
  var errorBox = document.getElementById('tool-error');
  var upsell = document.getElementById('upsell');
  var submitBtn = form.querySelector('button[type="submit"]');

  // Group templates by category in the select.
  var groups = {};
  catalog.forEach(function (t) {
    if (!groups[t.category]) {
      groups[t.category] = document.createElement('optgroup');
      groups[t.category].label = t.category;
      select.appendChild(groups[t.category]);
    }
    var opt = document.createElement('option');
    opt.value = t.id;
    opt.textContent = t.title + (!t.free && !pro ? ' — Pro' : '');
    groups[t.category].appendChild(opt);
  });

  function current() {
    for (var i = 0; i < catalog.length; i++) if (catalog[i].id === select.value) return catalog[i];
    return catalog[0];
  }

  function renderFields() {
    var t = current();
    errorBox.hidden = true;
    var locked = !t.free && !pro;
    upsell.hidden = !locked;
    submitBtn.disabled = locked;
    fieldsBox.innerHTML = '';
    t.fields.forEach(function (f) {
      var label = document.createElement('label');
      label.textContent = f.label;
      var input = document.createElement(f.type === 'textarea' ? 'textarea' : 'input');
      input.name = f.name;
      input.maxLength = 1500;
      input.disabled = locked;
      if (f.type === 'textarea') input.rows = 4; else input.type = 'text';
      if (f.placeholder) input.placeholder = f.placeholder;
      label.appendChild(input);
      fieldsBox.appendChild(label);
    });
  }

  select.addEventListener('change', renderFields);
  renderFields();

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    errorBox.hidden = true;
    var values = {};
    Array.prototype.forEach.call(fieldsBox.querySelectorAll('input, textarea'), function (el) {
      values[el.name] = el.value;
    });
    submitBtn.disabled = true;
    fetch('/api/outil/generer', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ templateId: select.value, values: values, token: token }),
    })
      .then(function (res) {
        return res.json().catch(function () { return {}; }).then(function (data) {
          if (!res.ok) {
            errorBox.textContent = data.error || 'Erreur inattendue. Réessayez.';
            errorBox.hidden = false;
            if (data.upsell) upsell.hidden = false;
            return;
          }
          out.value = data.prompt;
          copyBtn.disabled = false;
          copyBtn.textContent = 'Copier';
        });
      })
      .catch(function () {
        errorBox.textContent = 'Connexion impossible. Vérifiez votre réseau et réessayez.';
        errorBox.hidden = false;
      })
      .then(function () { submitBtn.disabled = !current().free && !pro; });
  });

  copyBtn.addEventListener('click', function () {
    function done() { copyBtn.textContent = 'Copié ✓'; }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(out.value).then(done, function () { out.select(); document.execCommand('copy'); done(); });
    } else {
      out.select();
      document.execCommand('copy');
      done();
    }
  });
})();
