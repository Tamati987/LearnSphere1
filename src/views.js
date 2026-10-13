'use strict';

const { modules } = require('./content');
const { publicCatalog, templates, FREE_DAILY_LIMIT } = require('./tool');

const TOTAL_COUNT = templates.length;
const FREE_COUNT = templates.filter((t) => t.free).length;

function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function formatPrice(price) {
  return `${price.replace('.', ',').replace(/,00$/, '')} €`;
}

// Free-license photos (Unsplash License: commercial use allowed), served by
// Unsplash's CDN at the right size for each screen.
function photo(id, alt, { w, h, cls = '', eager = false }) {
  const src = (width) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&crop=faces,entropy&q=70&w=${width}&h=${Math.round((width * h) / w)}`;
  return `<img class="${cls}" src="${src(w)}" srcset="${src(Math.round(w / 2))} ${Math.round(w / 2)}w, ${src(w)} ${w}w, ${src(w * 2)} ${w * 2}w" sizes="(max-width: 760px) 100vw, ${w}px" width="${w}" height="${h}" alt="${esc(alt)}"${eager ? ' fetchpriority="high"' : ' loading="lazy"'} decoding="async">`;
}

function layout({ title, body, config, scripts = '' }) {
  const testBanner = config.salesEnabled && config.paypal.env === 'sandbox'
    ? '<div class="banner">Mode test PayPal (sandbox) : aucun paiement réel n\'est encaissé.</div>'
    : '';
  return `<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)} · LearnSphere</title>
<meta name="description" content="Le kit pratique pour gagner du temps chaque semaine grâce à l'IA : 7 modules et 40 demandes prêtes à l'emploi.">
<link rel="stylesheet" href="/style.css">
</head>
<body>
${testBanner}
<header class="top"><div class="wrap"><a class="logo" href="/">Learn<span>Sphere</span></a><nav><a href="/outil">Générateur IA</a><a href="/retrouver">Retrouver mon accès</a></nav></div></header>
<main class="wrap">${body}</main>
<footer class="wrap foot">
  <a href="/mentions-legales">Mentions légales</a> · <a href="/cgv">CGV</a> · <a href="/confidentialite">Confidentialité</a>
  <p>© ${new Date().getFullYear()} LearnSphere${config.seller.name ? ` — ${esc(config.seller.name)}` : ''} · Photos : Unsplash</p>
</footer>
${scripts}
</body>
</html>`;
}

function home(config) {
  const p = config.product;
  const list = modules
    .map((m) => `<li><strong>${esc(m.title)}</strong><span>${esc(m.summary)}</span></li>`)
    .join('');

  const buy = config.salesEnabled
    ? `<div class="buybox" id="buy">
        <p class="price">${formatPrice(p.price)} <small>paiement unique · accès à vie</small></p>
        <ul class="included"><li>7 modules + 40 demandes prêtes à copier</li><li>Générateur IA Pro : ${TOTAL_COUNT} modèles, illimité</li><li>Mises à jour incluses</li></ul>
        <label class="waiver"><input type="checkbox" id="waiver">
          <span>J'accepte les <a href="/cgv" target="_blank" rel="noopener">conditions générales de vente</a>, je demande l'accès immédiat au contenu numérique et je reconnais <strong>renoncer à mon droit de rétractation</strong> dès le début de l'accès (art. L221-28 13° du Code de la consommation).</span>
        </label>
        <div id="paypal-buttons" class="pp" aria-live="polite"><p class="muted">Cochez la case ci-dessus pour afficher le paiement.</p></div>
        <p id="pay-error" class="error" hidden></p>
        <p class="muted small">Paiement sécurisé par PayPal (compte PayPal ou carte bancaire). Accès immédiat après paiement.</p>
      </div>`
    : `<div class="buybox"><p class="price">Bientôt disponible</p><p class="muted">Les ventes ouvriront très prochainement.</p></div>`;

  const scripts = config.salesEnabled
    ? `<script src="https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(config.paypal.clientId)}&currency=${p.currency}&intent=capture&components=buttons&locale=fr_FR" data-sdk-integration-source="button-factory" defer></script>
<script src="/checkout.js" defer></script>`
    : '';

  const body = `
<section class="hero">
  <div class="hero-text">
    <p class="kicker">Formation en ligne · 7 modules · Générateur IA inclus</p>
    <h1>Gagnez du temps chaque semaine grâce à l'IA, sans être technicien.</h1>
    <p class="lead">Le kit pratique pour indépendants, commerçants et petites équipes : des méthodes simples, 40 demandes prêtes à copier et un générateur qui écrit pour vous les demandes parfaites.</p>
    <div class="cta-row">
      <a class="btn" href="#buy">Accéder au kit — ${formatPrice(p.price)}</a>
      <a class="btn ghost" href="/outil">Essayer le générateur gratuit</a>
    </div>
  </div>
  ${photo('1573497019940-1c28c88b4f3e', 'Professionnelle souriante au travail', { w: 560, h: 640, cls: 'hero-img', eager: true })}
</section>

<section class="grid3">
  <div>${photo('1499750310107-5fef28a66643', 'Bureau de travail avec ordinateur portable et carnet', { w: 400, h: 240, cls: 'card-img' })}<h3>Concret</h3><p>Chaque module part d'une tâche réelle de votre quotidien, avec un exemple à copier et un exercice de 5 à 10 minutes.</p></div>
  <div>${photo('1460925895917-afdab827c52f', 'Ordinateur portable affichant des graphiques', { w: 400, h: 240, cls: 'card-img' })}<h3>Tous les outils</h3><p>ChatGPT, Claude, Gemini, Copilot, Le Chat : la méthode marche avec l'assistant que vous utilisez déjà, y compris en version gratuite.</p></div>
  <div>${photo('1522202176988-66273c2fd55f', 'Petite équipe travaillant ensemble autour d’ordinateurs', { w: 400, h: 240, cls: 'card-img' })}<h3>Responsable</h3><p>Confidentialité, RGPD, vérification des faits : utilisez l'IA sans mettre en danger vos clients.</p></div>
</section>

<section class="tool-promo">
  <div>
    <p class="kicker">Nouveau</p>
    <h2>Le Générateur de demandes IA</h2>
    <p>Choisissez une tâche (répondre à un email, relancer une facture, planifier vos publications…), remplissez 2 ou 3 champs : le générateur rédige une demande complète et structurée à coller dans ChatGPT, Claude ou Gemini.</p>
    <ul class="checks">
      <li><strong>Gratuit :</strong> ${FREE_COUNT} modèles, ${FREE_DAILY_LIMIT} demandes par jour.</li>
      <li><strong>Pro (inclus avec le kit) :</strong> les ${TOTAL_COUNT} modèles, sans limite.</li>
    </ul>
    <a class="btn" href="/outil">Essayer gratuitement</a>
  </div>
</section>

<section>
  <h2>Au programme</h2>
  <ul class="program">${list}</ul>
</section>

<section class="two">
  <div>
    <h2>Pour qui ?</h2>
    <ul class="checks">
      <li>Vous passez trop de temps sur vos emails, devis et relances.</li>
      <li>Vous savez que vous devriez publier plus sur les réseaux, sans avoir le temps.</li>
      <li>Vous avez essayé l'IA mais les réponses étaient trop génériques.</li>
      <li>Vous voulez une méthode claire, pas un cours théorique.</li>
    </ul>
  </div>
  ${buy}
</section>

<section>
  <h2>Questions fréquentes</h2>
  <details><summary>Faut-il un abonnement payant à une IA ?</summary><p>Non. Toutes les méthodes fonctionnent avec les versions gratuites des principaux assistants.</p></details>
  <details><summary>Comment j'accède au contenu ?</summary><p>Juste après le paiement, vous recevez un lien d'accès personnel. Gardez-le précieusement ; vous pourrez aussi le retrouver avec votre email PayPal et votre numéro de commande.</p></details>
  <details><summary>Est-ce que je peux être remboursé ?</summary><p>Le contenu étant numérique et livré immédiatement, le droit de rétractation ne s'applique plus après l'accès (voir CGV). En cas de problème technique, écrivez-nous : nous trouverons une solution.</p></details>
  <details><summary>Le kit est-il mis à jour ?</summary><p>Oui, les mises à jour sont incluses et accessibles avec le même lien.</p></details>
</section>`;
  return layout({ title: "Le kit pratique de l'IA au travail", body, config, scripts });
}

function accessGranted(config, token, baseUrl) {
  const link = `${baseUrl}/cours/${token}`;
  const body = `
<section class="card">
  <h1>Merci, votre paiement est confirmé.</h1>
  <p>Voici votre <strong>lien d'accès personnel</strong>. Enregistrez-le dans vos favoris ou envoyez-le-vous par email :</p>
  <p class="link"><a href="/cours/${esc(token)}">${esc(link)}</a></p>
  <p class="muted">Ne le partagez pas : il donne accès à votre formation. Si vous le perdez, utilisez la page <a href="/retrouver">Retrouver mon accès</a> avec votre email PayPal et votre numéro de transaction.</p>
  <a class="btn" href="/cours/${esc(token)}">Commencer la formation</a>
</section>`;
  return layout({ title: 'Accès confirmé', body, config });
}

function courseIndex(config, token) {
  const items = modules
    .map((m) => `<li><a href="/cours/${esc(token)}/${m.slug}"><strong>${esc(m.title)}</strong><span>${esc(m.summary)} · ${esc(m.duration)}</span></a></li>`)
    .join('');
  const body = `<section><h1>Votre formation</h1><p class="muted">Avancez à votre rythme. Conseil : un module par jour, avec l'exercice.</p>
<p><a class="btn" href="/cours/${esc(token)}/outil">Ouvrir le Générateur IA Pro</a></p>
<ul class="program links">${items}</ul></section>`;
  return layout({ title: 'Votre formation', body, config });
}

function courseModule(config, token, slug) {
  const i = modules.findIndex((m) => m.slug === slug);
  if (i === -1) return null;
  const m = modules[i];
  const prev = modules[i - 1];
  const next = modules[i + 1];
  const nav = `<nav class="pager">
    ${prev ? `<a href="/cours/${esc(token)}/${prev.slug}">← ${esc(prev.title)}</a>` : '<span></span>'}
    <a href="/cours/${esc(token)}">Sommaire</a>
    ${next ? `<a href="/cours/${esc(token)}/${next.slug}">${esc(next.title)} →</a>` : '<span></span>'}
  </nav>`;
  const body = `<article class="lesson"><p class="kicker">${esc(m.duration)}</p><h1>${esc(m.title)}</h1>${m.html}${nav}</article>`;
  return layout({ title: m.title, body, config });
}

function toolPage(config, { token = null } = {}) {
  const pro = Boolean(token);
  const catalog = publicCatalog();
  const intro = pro
    ? '<p class="badge">Version Pro : tous les modèles, sans limite</p>'
    : `<p class="muted">Version gratuite : ${FREE_COUNT} modèles et ${FREE_DAILY_LIMIT} demandes par jour. Les modèles marqués <span class="lock">Pro</span> sont inclus avec le <a href="/#buy">Kit IA Pro</a>.</p>`;
  const body = `
<section class="tool" id="tool" data-pro="${pro ? '1' : '0'}" data-token="${pro ? esc(token) : ''}">
  <h1>Générateur de demandes IA</h1>
  ${intro}
  <div class="tool-grid">
    <div>
      <label for="tpl">1. Choisissez une tâche</label>
      <select id="tpl"></select>
      <form id="tool-form" novalidate>
        <div id="fields"></div>
        <button class="btn" type="submit">2. Générer ma demande</button>
      </form>
      <p id="tool-error" class="error" hidden></p>
      <div id="upsell" class="upsell" hidden>
        <p><strong>Ce modèle fait partie de la version Pro.</strong> Débloquez les ${TOTAL_COUNT} modèles sans limite avec le Kit IA Pro.</p>
        <a class="btn" href="/#buy">Découvrir le kit</a>
      </div>
    </div>
    <div>
      <label for="out">3. Copiez-la dans votre assistant IA</label>
      <textarea id="out" readonly rows="16" placeholder="Votre demande apparaîtra ici."></textarea>
      <button class="btn ghost" type="button" id="copy" disabled>Copier</button>
      <p class="muted small">Relisez toujours la réponse de l'IA avant de l'envoyer à un client.</p>
    </div>
  </div>
</section>
<script type="application/json" id="catalog">${JSON.stringify(catalog).replace(/</g, '\\u003c')}</script>`;
  return layout({ title: 'Générateur de demandes IA', body, config, scripts: '<script src="/tool.js" defer></script>' });
}

function recoverForm(config, { error = '', values = {} } = {}) {
  const body = `
<section class="card narrow">
  <h1>Retrouver mon accès</h1>
  <p class="muted">Indiquez l'email de votre compte PayPal et le numéro de commande ou de transaction figurant dans l'email de confirmation PayPal. Un nouveau lien vous sera donné ; l'ancien ne fonctionnera plus.</p>
  ${error ? `<p class="error">${esc(error)}</p>` : ''}
  <form method="post" action="/retrouver">
    <label>Email PayPal<input type="email" name="email" required maxlength="254" value="${esc(values.email)}"></label>
    <label>N° de commande ou de transaction PayPal<input type="text" name="orderId" required maxlength="64" value="${esc(values.orderId)}"></label>
    <button class="btn" type="submit">Retrouver mon accès</button>
  </form>
</section>`;
  return layout({ title: 'Retrouver mon accès', body, config });
}

function message(config, title, text, status = '') {
  const body = `<section class="card narrow"><h1>${esc(title)}</h1><p>${esc(text)}</p>${status}<p><a href="/">Retour à l'accueil</a></p></section>`;
  return layout({ title, body, config });
}

function sellerBlock(s) {
  return `<p><strong>${esc(s.name || '[NOM À RENSEIGNER]')}</strong><br>
${esc(s.status || '[STATUT À RENSEIGNER]')}<br>
SIRET : ${esc(s.siret || '[SIRET À RENSEIGNER]')}<br>
Adresse : ${esc(s.address || '[ADRESSE À RENSEIGNER]')}<br>
Contact : ${esc(s.email || '[EMAIL À RENSEIGNER]')}<br>
${esc(s.vatMention)}</p>`;
}

function legal(config, page) {
  const s = config.seller;
  const p = config.product;
  const pages = {
    'mentions-legales': {
      title: 'Mentions légales',
      html: `<h2>Éditeur du site</h2>${sellerBlock(s)}
<p>Directeur de la publication : ${esc(s.name || '[NOM À RENSEIGNER]')}.</p>
<h2>Hébergement</h2>
<p>Railway Corporation, 548 Market St PMB 68956, San Francisco, CA 94104, États-Unis — railway.com.</p>
<h2>Propriété intellectuelle</h2>
<p>L'ensemble des contenus de la formation est protégé par le droit d'auteur. Toute reproduction, diffusion ou revente, même partielle, sans autorisation écrite est interdite.</p>`,
    },
    cgv: {
      title: 'Conditions générales de vente',
      html: `<h2>1. Vendeur</h2>${sellerBlock(s)}
<h2>2. Produit</h2>
<p>« ${esc(p.name)} » : formation en ligne composée de 7 modules et d'un bonus, accessible depuis un navigateur via un lien personnel, sans limite de durée tant que le service est en ligne.</p>
<h2>3. Prix</h2>
<p>${formatPrice(p.price)}, paiement unique. ${esc(s.vatMention)}.</p>
<h2>4. Paiement</h2>
<p>Le paiement s'effectue en ligne via PayPal (compte PayPal ou carte bancaire). La commande est définitive à la confirmation du paiement par PayPal.</p>
<h2>5. Livraison</h2>
<p>L'accès est délivré immédiatement après le paiement, sous forme d'un lien personnel affiché à l'écran. En cas de perte, il peut être régénéré depuis la page « Retrouver mon accès ».</p>
<h2>6. Droit de rétractation</h2>
<p>Conformément à l'article L221-28 13° du Code de la consommation, le droit de rétractation ne peut être exercé pour la fourniture d'un contenu numérique non fourni sur un support matériel dont l'exécution a commencé avec l'accord préalable exprès du consommateur et sa renonciation expresse à son droit de rétractation. Avant de payer, l'acheteur coche une case par laquelle il donne cet accord et cette renonciation ; la date en est conservée.</p>
<h2>7. Licence d'utilisation</h2>
<p>L'achat donne droit à un usage personnel et non transférable. Le partage du lien d'accès ou la diffusion du contenu sont interdits.</p>
<h2>8. Réclamations et médiation</h2>
<p>Pour toute réclamation : ${esc(s.email || '[EMAIL À RENSEIGNER]')}. Conformément à l'article L612-1 du Code de la consommation, le consommateur peut recourir gratuitement à un médiateur de la consommation. Coordonnées du médiateur : ${esc(config.seller.mediator || '[MÉDIATEUR À DÉSIGNER]')}.</p>
<h2>9. Droit applicable</h2>
<p>Les présentes CGV sont soumises au droit français.</p>`,
    },
    confidentialite: {
      title: 'Politique de confidentialité',
      html: `<h2>Responsable du traitement</h2>${sellerBlock(s)}
<h2>Données collectées</h2>
<p>Lors d'un achat : l'identifiant de commande PayPal, l'adresse email du compte PayPal, le montant, la date et la date d'acceptation des conditions. Aucune donnée bancaire n'est reçue ni stockée par ce site : le paiement est traité par PayPal.</p>
<h2>Finalités et base légale</h2>
<p>Exécution du contrat (délivrance et récupération de l'accès) et obligations légales (comptabilité). Aucune publicité, aucun cookie de suivi, aucune revente de données.</p>
<h2>Durée de conservation</h2>
<p>10 ans pour les données comptables (article L123-22 du Code de commerce).</p>
<h2>Vos droits</h2>
<p>Accès, rectification, effacement, limitation, opposition : écrivez à ${esc(s.email || '[EMAIL À RENSEIGNER]')}. Vous pouvez saisir la CNIL (cnil.fr).</p>
<h2>Sous-traitants</h2>
<p>PayPal (paiement), Railway (hébergement et base de données).</p>`,
    },
  };
  const page_ = pages[page];
  return layout({ title: page_.title, body: `<article class="lesson"><h1>${esc(page_.title)}</h1>${page_.html}</article>`, config });
}

module.exports = { home, accessGranted, courseIndex, courseModule, toolPage, recoverForm, message, legal, esc };
