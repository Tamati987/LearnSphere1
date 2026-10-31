// FENUA MANGA — boutique de mangas tahitiens + t-shirts Printful (Railway Function, Bun)
import { Resvg } from "@resvg/resvg-js";
import { createHmac, createHash, timingSafeEqual } from "node:crypto";
// Variables : STRIPE_SECRET_KEY, PRICE_CENTS (MANA, 499), TOA_PRICE_CENTS (599), OWNER_KEY, DATA_DIR (/data), PUBLIC_URL
const STRIPE_KEY = Bun.env.STRIPE_SECRET_KEY ?? "";
const OWNER_KEY = Bun.env.OWNER_KEY ?? "";
const DATA = (Bun.env.DATA_DIR ?? "/data").replace(/\/$/, "");

type Product = { label: string; name: string; desc: string; price: number; pdf: string; file: string };
const PRODUCTS: Record<string, Product> = {
  "mana-t1": {
    label: "MANA — Tome 1",
    name: "MANA — Tome 1 : L'éclipse (PDF)",
    desc: "Manga tahitien, 16 pages, format numérique à télécharger.",
    price: Number(Bun.env.PRICE_CENTS ?? 499),
    pdf: `${DATA}/MANA-Tome1.pdf`,
    file: "MANA-Tome1-FenuaManga.pdf",
  },
  "toa-t1": {
    label: "TOA — Tome 1",
    name: "TOA — Tome 1 (PDF)",
    desc: "Manga de combat tahitien, 44 pages, format numérique à télécharger.",
    price: Number(Bun.env.TOA_PRICE_CENTS ?? 599),
    pdf: `${DATA}/TOA-Tome1.pdf`,
    file: "TOA-Tome1-FenuaManga.pdf",
  },
};
const euros = (c: number) => (c / 100).toFixed(2).replace(".", ",") + " €";
const MANA_P = euros(PRODUCTS["mana-t1"].price);
const TOA_P = euros(PRODUCTS["toa-t1"].price);
const hideIfMissing = `onerror="this.style.display='none'"`;

/* ===================== MONÉTISATION =====================
   ADSENSE_CLIENT : identifiant AdSense (ca-pub-…). Vide = aucune pub, rien n'est chargé.
   SPONSOR_NAME, SPONSOR_URL, SPONSOR_TEXT : encart partenaire payé. Vide = « Ton commerce ici ». */
const cfg = (k: string) => (Bun.env[k] || "").trim();
const esc = (t: string) => t.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
const ADSENSE = /^ca-pub-\d{10,20}$/.test(cfg("ADSENSE_CLIENT")) ? cfg("ADSENSE_CLIENT") : "";
// Balise de vérification du site pour Google : le script publicitaire, lui, n'est chargé qu'après consentement
const adHead = ADSENSE ? `<meta name="google-adsense-account" content="${ADSENSE}">` : "";
const adSlot = ADSENSE ? `<div class="adzone"><div class="adlabel">Publicité</div><ins class="adsbygoogle" style="display:block" data-ad-client="${ADSENSE}" data-ad-format="auto" data-full-width-responsive="true"></ins></div>` : "";
// Bandeau cookies : sans accord, aucun script Google n'est chargé. Choix gardé dans le navigateur du visiteur.
const consentBox = ADSENSE ? `<div id="ck" class="ck" hidden role="dialog" aria-live="polite" aria-label="Cookies"><p>🍪 Des publicités Google aident à financer FENUA MANGA. Avec ton accord, Google utilise des cookies pour les afficher et les personnaliser. <a href="/mentions-legales#cookies">En savoir plus</a></p><div class="ck-b"><button type="button" data-c="0">Refuser</button><button type="button" data-c="1">Accepter</button></div></div>
<script>(()=>{const K='fm_consent',box=document.getElementById('ck');let v=null;try{v=localStorage.getItem(K)}catch(e){}
const load=()=>{if(window.__fmAds)return;window.__fmAds=1;const s=document.createElement('script');s.async=true;s.crossOrigin='anonymous';s.src='https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${ADSENSE}';document.head.appendChild(s);document.querySelectorAll('ins.adsbygoogle').forEach(()=>(window.adsbygoogle=window.adsbygoogle||[]).push({}))};
box.querySelectorAll('button').forEach(b=>b.onclick=()=>{const c=b.dataset.c;try{localStorage.setItem(K,c)}catch(e){}box.hidden=true;if(c==='1')load();else if(window.__fmAds)location.reload()});
window.fmCookies=()=>{box.hidden=false};if(v==='1')load();else if(v!=='0')box.hidden=false})()</script>` : "";
const sponsorBox = () => {
  const name = cfg("SPONSOR_NAME"), url = cfg("SPONSOR_URL");
  if (name && /^https:\/\//.test(url)) return `<a class="sponsor" href="${esc(url)}" target="_blank" rel="sponsored noopener"><span class="sp-tag">PARTENAIRE</span><b>${esc(name)}</b><span>${esc(cfg("SPONSOR_TEXT"))}</span></a>`;
  return `<a class="sponsor empty" href="/partenaires"><span class="sp-tag">ESPACE PARTENAIRE</span><b>Ton commerce ici</b><span>Fais connaître ta marque aux fans de manga du fenua →</span></a>`;
};
const SPONSOR_CSS = `.sponsor{display:grid;gap:4px;max-width:720px;margin:28px auto;padding:18px 20px;background:#fff;color:#0a0a0a;border:3px solid #0a0a0a;border-radius:18px;box-shadow:8px 8px 0 #ffe14d;text-decoration:none;text-align:center;transition:transform .1s,box-shadow .1s}
.sponsor:hover{transform:translate(-2px,-2px);box-shadow:10px 10px 0 #ffe14d}.sponsor b{font-size:20px}.sponsor span{color:#4a4550}
.sponsor.empty{border-style:dashed;background:#fffdf7}.sp-tag{font-size:11px;font-weight:900;letter-spacing:.25em;color:#ff2a36!important}
.adzone{max-width:760px;margin:28px auto;padding:0 16px;text-align:center}.adlabel{font-size:11px;letter-spacing:.2em;color:#8a8490;margin-bottom:4px}
.ck{position:fixed;left:12px;right:12px;bottom:12px;z-index:50;max-width:560px;margin:0 auto;background:#fff;color:#0a0a0a;border:3px solid #0a0a0a;border-radius:18px;box-shadow:6px 6px 0 #ff2a36;padding:14px 16px;font-size:14px;line-height:1.45}
.ck[hidden]{display:none}.ck p{margin:0 0 10px}.ck a{color:#0a0a0a}.ck-b{display:flex;gap:10px;justify-content:flex-end}
.ck button{font:inherit;font-weight:900;padding:9px 16px;border-radius:12px;border:2px solid #0a0a0a;background:#fff;color:#0a0a0a;cursor:pointer}
.ck button[data-c="1"]{background:#ff2a36;color:#fff}`;

// Apparition en fondu des blocs .rv au défilement
const REVEAL = `<script>document.documentElement.classList.add('js');addEventListener('DOMContentLoaded',()=>{const els=document.querySelectorAll('.rv');if(!('IntersectionObserver' in window)){els.forEach(e=>e.classList.add('in'));return}const io=new IntersectionObserver(es=>es.forEach(e=>{if(e.isIntersecting){e.target.classList.add('in');io.unobserve(e.target)}}),{rootMargin:'0px 0px -8% 0px'});els.forEach((e,i)=>{e.style.transitionDelay=(i%3)*70+'ms';io.observe(e)})});</script>`;

const INDEX = `<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>FENUA MANGA — Le fenua en manga</title>
<meta name="description" content="Illustrations et mangas 100 % tahitiens. TOA, Tome 1 et MANA, Tome 1 : L'éclipse, à télécharger en PDF.">
<meta property="og:title" content="FENUA MANGA — TOA et MANA">
<meta property="og:description" content="Des mangas 100 % tahitiens à télécharger en PDF.">
<meta property="og:image" content="/img/og.jpg">
<link rel="icon" href="/img/favicon.png">
<meta name="theme-color" content="#fffdf7">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Bangers&family=Inter:wght@400;600;800;900&display=swap" rel="stylesheet">
<link rel="stylesheet" href="/style.css">
${adHead}
${REVEAL}
</head>
<body>

<header class="top">
  <a href="/" class="brand">FENUA <span>MANGA</span></a>
  <nav class="nav"><a href="#toa" class="opt">TOA</a><a href="#mana" class="opt">MANA</a><a href="/tshirts">T-shirts</a><a href="#acheter" class="btn small-btn">Acheter</a></nav>
</header>

<section class="hero" id="toa">
  <div class="hero-img"><img src="/img/toa-01.jpg" alt="Couverture de TOA, Tome 1" ${hideIfMissing}></div>
  <div class="hero-txt">
    <div class="badge">NOUVEAU</div>
    <div class="kata">トア</div>
    <h1>TOA</h1>
    <p class="sub">Tome 1 · Le toa vide</p>
    <p class="pitch">À Faa'a, Manu, 16 ans, porte un tatau qui ne s'est jamais réveillé.
      Pour sauver le fare de sa famille, il doit gagner le Tournoi des Toa à Taputapuātea…
      et dompter <b>Maʻo</b>, le requin-tigre qui dévore aussi son porteur.</p>
    <a href="#acheter" class="btn big">Lire TOA · <span class="price">${TOA_P}</span></a>
    <p class="small">44 pages · PDF à télécharger immédiatement après paiement</p>
  </div>
</section>

<div class="marquee" aria-hidden="true"><div>${"TOA ✦ MANA ✦ 100 % TAHITIEN ✦ PDF INSTANTANÉ ✦ T-SHIRTS FENUA MANGA ✦ ".repeat(8)}</div></div>
<section class="strip">
  <div><b>100 %</b> tahitien</div>
  <div><b>2</b> mangas</div>
  <div><b>PDF</b> instantané</div>
  <div><b>Paiement</b> sécurisé</div>
</section>

<section class="preview">
  <h2>Feuillette TOA</h2>
  <div class="pages">
    <img src="/img/toa-02.jpg" alt="TOA, extrait 1" loading="lazy" ${hideIfMissing}>
    <img src="/img/toa-03.jpg" alt="TOA, extrait 2" loading="lazy" ${hideIfMissing}>
    <img src="/img/toa-04.jpg" alt="TOA, extrait 3" loading="lazy" ${hideIfMissing}>
    <img src="/img/toa-05.jpg" alt="TOA, extrait 4" loading="lazy" ${hideIfMissing}>
  </div>
</section>

<section class="chars">
  <h2>Les personnages de TOA</h2>
  <div class="cards">
    <div class="card rv"><h3>Manu</h3><p>16 ans, de Faa'a. On l'appelle le « toa vide »… jusqu'au jour où son tatau s'éveille.</p></div>
    <div class="card rv"><h3>Kahu</h3><p>Son meilleur ami. Peureux, drôle, et il connaît tous les combattants par cœur.</p></div>
    <div class="card rv"><h3>Vaimiti</h3><p>La raie manta de Bora Bora. Rivale redoutable… ou future alliée ?</p></div>
    <div class="card red rv"><h3>Maʻo</h3><p>Le requin-tigre. Le totem le plus dangereux du Pacifique.</p></div>
  </div>
</section>

<section class="hero second" id="mana">
  <div class="hero-img"><img src="/img/page-01.jpg" alt="Couverture de MANA, Tome 1" ${hideIfMissing}></div>
  <div class="hero-txt">
    <div class="kata">フェヌア・マンガ</div>
    <h1>MANA</h1>
    <p class="sub">Tome 1 · L'éclipse</p>
    <p class="pitch">À Tahiti, on raconte qu'un pouvoir plus ancien que les îles dort dans l'océan.
      Le jour où le soleil disparaît, le tatau de Teiva s'embrase… et réveille <b>Tumu</b>, le dieu-requin.</p>
    <a href="#acheter" class="btn big">Lire MANA · <span class="price">${MANA_P}</span></a>
    <p class="small">16 pages · PDF à télécharger immédiatement après paiement</p>
  </div>
</section>

<section class="preview">
  <h2>Feuillette MANA</h2>
  <div class="pages">
    <img src="/img/page-04.jpg" alt="Page 4" loading="lazy" ${hideIfMissing}>
    <img src="/img/page-05.jpg" alt="Page 5" loading="lazy" ${hideIfMissing}>
    <img src="/img/page-06.jpg" alt="Page 6" loading="lazy" ${hideIfMissing}>
    <img src="/img/page-07.jpg" alt="Page 7" loading="lazy" ${hideIfMissing}>
  </div>
</section>

<section class="chars">
  <h2>Les personnages de MANA</h2>
  <div class="cards">
    <div class="card rv"><h3>Teiva</h3><p>Jeune rameur de va'a. Le tatau de son grand-père s'éveille en lui.</p></div>
    <div class="card rv"><h3>Hinatea</h3><p>Gardienne mystérieuse. L'eau obéit à chacun de ses gestes.</p></div>
    <div class="card red rv"><h3>Tumu</h3><p>Le dieu-requin. Un sourire trop large… des dents trop pointues.</p></div>
    <div class="card rv"><h3>Papa Rui</h3><p>Le grand-père. Il connaît le secret du mana.</p></div>
  </div>
</section>

<section id="acheter" class="buy">
  <h2>Acheter</h2>
  <div class="buy-box rv">
    <img src="/img/toa-01.jpg" alt="TOA Tome 1" ${hideIfMissing}>
    <div>
      <div class="badge dark">NOUVEAU</div>
      <h2>TOA — Tome 1</h2>
      <p>4 chapitres : « Le toa vide », « L'esprit qui dort », « Le tournoi des Toa », « Le prix du requin »</p>
      <ul>
        <li>44 pages au format manga</li>
        <li>PDF haute qualité, à lire sur téléphone, tablette ou ordinateur</li>
        <li>Téléchargement immédiat</li>
      </ul>
      <form method="post" action="/acheter">
        <input type="hidden" name="produit" value="toa-t1">
        <button class="btn big" type="submit">Acheter · ${TOA_P}</button>
      </form>
      <p class="small">Paiement sécurisé par carte via Stripe.</p>
    </div>
  </div>

  <div class="buy-box rv">
    <img src="/img/page-01.jpg" alt="MANA Tome 1" ${hideIfMissing}>
    <div>
      <h2>MANA — Tome 1</h2>
      <p>Chapitre 1 « L'éclipse » + Chapitre 2 « Tumu »</p>
      <ul>
        <li>16 pages au format manga</li>
        <li>PDF haute qualité, à lire sur téléphone, tablette ou ordinateur</li>
        <li>Téléchargement immédiat</li>
      </ul>
      <form method="post" action="/acheter">
        <input type="hidden" name="produit" value="mana-t1">
        <button class="btn big" type="submit">Acheter · ${MANA_P}</button>
      </form>
      <p class="small">Paiement sécurisé par carte via Stripe.</p>
    </div>
  </div>
</section>

<section id="tshirts" style="max-width:1080px;margin:0 auto;padding:48px 16px;text-align:center;border-top:4px solid var(--red);">
  <div class="badge">NOUVEAU</div>
  <h2>👕 Les t-shirts FENUA MANGA</h2>
  <p style="margin:12px auto 22px;max-width:560px;">Le logo devant, Manu et son tatau dans le dos. Imprimés à la commande, livrés chez toi.</p>
  <a href="/tshirts"><img src="/print/toa/back.png?w=600" alt="T-shirt TOA, dos" style="max-width:240px;margin:0 auto 22px;" ${hideIfMissing}></a>
  <a class="btn big" href="/tshirts">Voir les t-shirts</a>
</section>

<section id="soutenir" style="max-width:720px;margin:0 auto;padding:48px 16px;text-align:center;">
  <h2>🤝 Rejoignez les soutiens</h2>
  <p style="margin:12px 0 6px;">Votre soutien finance les prochains épisodes et de meilleurs outils (voix, animation, musique) pour des vidéos encore plus fortes.</p>
  <p class="small" style="margin-bottom:22px;">Les soutiens reçoivent les épisodes en avance, leur nom au générique et un vote pour choisir la prochaine histoire.</p>
  <a class="btn big" href="https://ko-fi.com/loshing57323" target="_blank" rel="noopener">Soutenir FENUA MANGA sur Ko-fi</a>
</section>

<section style="padding:0 16px;">${sponsorBox()}</section>
${adSlot}
${consentBox}

<footer>
  <div class="brand">FENUA <span>MANGA</span></div>
  <p>Le fenua en manga · <a href="https://www.tiktok.com/@fenuamanga" rel="noopener">TikTok</a> · <a href="https://www.youtube.com/@fenuamanga" rel="noopener">YouTube</a> · <a href="/tshirts">T-shirts</a> · <a href="https://ko-fi.com/loshing57323" rel="noopener">Soutenir</a> · <a href="/liens.html">Tous les liens</a> · <a href="/mentions-legales">Mentions légales</a></p>
  <p class="small">Histoires et personnages originaux © 2026 Fenua Manga. Illustrations créées avec l'aide de l'IA.</p>
</footer>

</body>
</html>
`;

const LIENS = `<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>FENUA MANGA — Liens</title>
<link rel="icon" href="/img/favicon.png">
<meta name="theme-color" content="#fffdf7">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Bangers&family=Inter:wght@400;600;800;900&display=swap" rel="stylesheet">
<link rel="stylesheet" href="/style.css">
${adHead}
<style>
  .links { max-width:440px; margin:0 auto; padding:40px 16px 56px; text-align:center; }
  .links img.logo { width:120px; height:120px; border-radius:50%; margin:0 auto 14px; border:4px solid var(--paper); box-shadow:0 0 0 4px var(--red); }
  .links h1 { font-size:30px; font-weight:900; }
  .links h1 span { color:var(--red); }
  .links p.bio { color:#4a4550; margin:6px 0 26px; }
  .links a.l { display:block; margin:0 0 16px; padding:16px; background:var(--paper); color:var(--ink); font-weight:900; font-size:17px;
    text-decoration:none; border:4px solid var(--ink); box-shadow:6px 6px 0 var(--red); }
  .links a.l { transition:transform .1s, box-shadow .1s; }
  .links a.l:hover { transform:translate(-2px,-2px); box-shadow:8px 8px 0 var(--red); }
  .links h1 { font-family:Bangers,Inter,sans-serif; font-weight:400; font-size:44px; letter-spacing:1.5px; }
  .links a.l.main { background:var(--red); color:var(--paper); border-color:var(--paper); box-shadow:6px 6px 0 var(--paper); }
  .guide { text-align:left; background:#fff; border:3px solid var(--ink); box-shadow:6px 6px 0 #ffe14d; padding:18px; margin-top:28px; }
  .guide h2 { font-size:20px; text-align:left; margin-bottom:10px; }
  .guide ol { margin-left:20px; color:#2b2730; }
  .guide li { margin-bottom:8px; }
</style>
</head>
<body>
<main class="links">
  <img class="logo" src="/img/logo.png" alt="FENUA MANGA" ${hideIfMissing}>
  <h1>FENUA <span>MANGA</span></h1>
  <p class="bio">Illustrations manga 100 % tahitiennes 🌺</p>

  <a class="l main" href="/#toa">🦈 NOUVEAU · Lire TOA · Tome 1 (${TOA_P})</a>
  <a class="l" href="/#mana">📖 Lire MANA · Tome 1 (${MANA_P})</a>
  <a class="l" href="/tshirts">👕 Les t-shirts FENUA MANGA</a>
  <a class="l" href="/tshirts#daytho">🖤 DAYTHO · DTHO 987</a>
  <a class="l" href="https://ko-fi.com/loshing57323" rel="noopener">🤝 Soutenir FENUA MANGA (Ko-fi)</a>
  <a class="l" href="/">🌐 Le site FENUA MANGA</a>
  <a class="l" href="#guide">🧭 Guide : par où commencer ?</a>
  <a class="l" href="https://www.tiktok.com/@fenuamanga" rel="noopener">🎬 TikTok · @fenuamanga</a>
  <a class="l" href="https://www.youtube.com/@fenuamanga" rel="noopener">▶️ YouTube · @fenuamanga</a>
  <a class="l" href="/partenaires">📣 Devenir partenaire de FENUA MANGA</a>
  ${sponsorBox()}

  <section class="guide" id="guide">
    <h2>🧭 Guide du lecteur</h2>
    <ol>
      <li><b>Regarde les épisodes</b> en vidéo sur TikTok ou YouTube (1 min chacun).</li>
      <li><b>Lis les tomes complets</b> en PDF : TOA (44 pages) et MANA (16 pages).</li>
      <li><b>Paiement par carte</b>, sécurisé par Stripe. Le PDF se télécharge tout de suite.</li>
      <li><b>Abonne-toi</b> pour ne pas rater les prochains tomes et les nouvelles séries.</li>
    </ol>
  </section>
  ${adSlot}
  <p class="small" style="margin-top:22px"><a href="/mentions-legales">Mentions légales & cookies</a></p>
</main>
${consentBox}
</body>
</html>
`;

const CSS = `:root{--ink:#0a0a0a;--paper:#fffaf0;--red:#ff2a36;--grey:#c4bcc8}
*{margin:0;padding:0;box-sizing:border-box}
html{scroll-behavior:smooth;overflow-x:hidden;overflow-x:clip}
body{overflow-x:hidden;overflow-x:clip}
body{font-family:"Inter",system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;line-height:1.5;background-size:22px 22px,100% 100%,100% 100%,100% 100%;background-attachment:scroll,fixed,fixed,fixed}
img{max-width:100%;display:block}
a{color:inherit}
.brand{font-weight:900;font-size:22px;letter-spacing:.02em;text-decoration:none;color:var(--paper)}
.brand span{color:var(--red)}
.top{position:sticky;top:0;z-index:10;display:flex;justify-content:space-between;align-items:center;padding:12px 16px;background:rgba(36,32,42,.88);border-bottom:3px solid var(--red);backdrop-filter:blur(8px)}
.btn{display:inline-block;background:var(--red);color:var(--paper);font-weight:900;text-decoration:none;border:3px solid var(--paper);padding:14px 26px;font-size:18px;letter-spacing:.03em;box-shadow:6px 6px 0 var(--paper);cursor:pointer;font-family:inherit;transition:transform .1s,box-shadow .1s}
.btn:active{transform:translate(4px,4px);box-shadow:2px 2px 0 var(--paper)}
.btn.big{font-size:21px;padding:16px 30px}
.small-btn{padding:8px 14px;font-size:15px;box-shadow:3px 3px 0 var(--paper)}
.price{color:#ffe14d}
.small{font-size:13px;color:var(--grey);margin-top:10px}
.badge{display:inline-block;background:#ffe14d;color:var(--ink);font-weight:900;font-size:14px;letter-spacing:.12em;padding:4px 10px;margin-bottom:10px;transform:rotate(-2deg)}
.badge.dark{background:var(--ink);color:#ffe14d}
.hero{display:grid;grid-template-columns:minmax(0,420px) minmax(0,1fr);gap:40px;align-items:center;max-width:1080px;margin:0 auto;padding:48px 16px}
.hero.second{border-top:4px solid var(--red);margin-top:24px}
.hero-img img{border:6px solid var(--paper);box-shadow:14px 14px 0 var(--red);transform:rotate(-2deg)}
.kata{font-size:18px;color:var(--red);letter-spacing:.3em;font-weight:700}
.hero h1{font-size:clamp(72px,14vw,150px);font-weight:900;line-height:.9;color:var(--red);letter-spacing:.03em;-webkit-text-stroke:5px var(--ink);paint-order:stroke fill;text-shadow:6px 6px 0 var(--paper),10px 10px 0 #000}
.sub{font-size:22px;font-weight:800;letter-spacing:.12em;margin:14px 0 18px;text-transform:uppercase}
.pitch{font-size:18px;max-width:520px;margin-bottom:28px;color:#f1eae2}
.pitch b{color:var(--red)}
.strip{display:grid;grid-template-columns:repeat(4,1fr);background:var(--red);border-top:4px solid var(--paper);border-bottom:4px solid var(--paper)}
.strip div{padding:16px 8px;text-align:center;font-size:15px;text-transform:uppercase;letter-spacing:.06em}
.strip b{display:block;font-size:24px;font-weight:900}
section h2{font-size:clamp(28px,5vw,42px);font-weight:900;text-align:center;margin-bottom:28px;text-transform:uppercase}
.preview,.chars{max-width:1080px;margin:0 auto;padding:56px 16px}
.pages{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}
.pages img{border:4px solid var(--paper);box-shadow:6px 6px 0 var(--red)}
.cards{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}
.card{background:var(--paper);color:var(--ink);border:4px solid var(--ink);box-shadow:6px 6px 0 var(--red);padding:18px}
.card h3{font-size:24px;font-weight:900;margin-bottom:6px}
.card.red{background:var(--red);color:var(--paper);box-shadow:6px 6px 0 var(--paper)}
.buy{padding:56px 16px 72px}
.buy-box{max-width:860px;margin:0 auto 40px;display:grid;grid-template-columns:220px minmax(0,1fr);gap:32px;align-items:center;background:var(--paper);color:var(--ink);border:5px solid var(--ink);box-shadow:12px 12px 0 var(--red);padding:28px}
.buy-box img{border:4px solid var(--ink)}
.buy-box h2{text-align:left;margin-bottom:6px}
.buy-box ul{margin:14px 0 22px 20px}
.buy-box .btn{border-color:var(--ink);box-shadow:6px 6px 0 var(--ink)}
.buy-box .small{color:#666}
footer{text-align:center;padding:40px 16px;border-top:4px solid var(--red);color:#e2d9e4}
footer p{margin-top:8px}
body.simple{display:flex;min-height:100vh;align-items:center;justify-content:center;padding:16px}
.box{max-width:520px;width:100%;background:var(--paper);color:var(--ink);border:5px solid var(--ink);box-shadow:12px 12px 0 var(--red);padding:32px;text-align:center}
.box .brand{color:var(--ink);display:block;margin-bottom:18px}
.box h1{font-size:34px;font-weight:900;margin-bottom:12px}
.box p{margin-bottom:18px}
.box .btn{border-color:var(--ink);box-shadow:6px 6px 0 var(--ink);margin-bottom:12px}
.box label{display:block;text-align:left;font-weight:800;margin-top:14px}
.box input[type=file]{width:100%;margin-top:4px}
.box h2{font-size:22px;margin:22px 0 4px;text-align:left}
@media (max-width:760px){.hero{grid-template-columns:1fr;gap:28px;padding:28px 16px;text-align:center}
.hero-img{max-width:300px;margin:0 auto}
.pitch{margin-left:auto;margin-right:auto}
.strip{grid-template-columns:repeat(2,1fr)}
.pages{grid-template-columns:repeat(2,1fr)}
.cards{grid-template-columns:1fr 1fr}
.buy-box{grid-template-columns:1fr;text-align:center}
.buy-box img{max-width:200px;margin:0 auto}
.buy-box h2{text-align:center}
.buy-box ul{text-align:left;display:inline-block}
}
.brand{font-family:Bangers,Inter,sans-serif;font-weight:400;font-size:28px;letter-spacing:1.5px}
.nav{display:flex;align-items:center;gap:4px}
.nav a:not(.btn){font-weight:900;text-decoration:none;padding:6px 8px;text-transform:uppercase;letter-spacing:.05em;font-size:15px}
.nav a:not(.btn):hover{color:#ffe14d}
@media (max-width:520px){.nav .opt{display:none}
}
.btn:hover{transform:translate(-2px,-2px);box-shadow:8px 8px 0 var(--paper)}
.small-btn:hover{box-shadow:5px 5px 0 var(--paper)}
.buy-box .btn:hover{box-shadow:8px 8px 0 var(--ink)}
:focus-visible{outline:3px solid #ffe14d;outline-offset:3px}
::selection{background:var(--red);color:var(--paper)}
.hero{position:relative;isolation:isolate}
.hero::before{content:"";position:absolute;inset:0 -50vw;z-index:-1;background:repeating-conic-gradient(from 0deg at 30% 50%,rgba(255,42,54,.2) 0 4deg,transparent 4deg 12deg);-webkit-mask:radial-gradient(circle at 30% 50%,transparent 10%,#000 65%);mask:radial-gradient(circle at 30% 50%,transparent 10%,#000 65%)}
.hero h1{font-family:Bangers,Inter,sans-serif;font-weight:400;letter-spacing:.05em}
.hero-img img{transition:transform .3s}
.hero-img:hover img{transform:rotate(0deg) scale(1.02)}
section h2{font-family:Bangers,Inter,sans-serif;font-weight:400;letter-spacing:.06em;font-size:clamp(36px,6vw,58px)}
.buy-box h2{font-size:clamp(30px,5vw,44px)}
.marquee{overflow:hidden;white-space:nowrap;background:var(--paper);color:var(--ink);border-top:4px solid var(--ink);font-family:Bangers,Inter,sans-serif;font-size:22px;letter-spacing:1.5px;padding:6px 0}
.marquee div{display:inline-block;animation:mq 30s linear infinite}
@keyframes mq{to{transform:translateX(-50%)}
}
${SPONSOR_CSS}
.pages img{transition:transform .2s,box-shadow .2s}
.pages img:hover{transform:translate(-4px,-4px) rotate(-1deg);box-shadow:10px 10px 0 var(--red)}
.card{transition:transform .15s,box-shadow .15s}
.card:hover{transform:translate(-3px,-3px) rotate(-.8deg);box-shadow:9px 9px 0 var(--red)}
.card.red:hover{box-shadow:9px 9px 0 var(--paper)}
.card h3{font-family:Bangers,Inter,sans-serif;font-weight:400;letter-spacing:.06em;font-size:30px}
.buy-box{transition:transform .2s}
.buy-box:hover{transform:translate(-3px,-3px)}
footer .brand{font-size:40px}
body{background-color:#fffdf7;color:var(--ink);background-image:radial-gradient(rgba(0,0,0,.06) 18%,transparent 19%),radial-gradient(ellipse 70% 50% at 10% 0%,rgba(255,42,54,.12),transparent 70%),radial-gradient(ellipse 60% 50% at 95% 35%,rgba(42,200,180,.12),transparent 70%),radial-gradient(ellipse 70% 50% at 20% 100%,rgba(255,200,60,.14),transparent 70%)}
.brand,.box .brand{color:var(--ink)}
.top{background:rgba(255,253,247,.92)}
.btn{border-color:var(--ink);box-shadow:6px 6px 0 var(--ink);color:#fff}
.btn:hover{box-shadow:8px 8px 0 var(--ink)}
.btn:active{box-shadow:2px 2px 0 var(--ink)}
.small-btn{box-shadow:3px 3px 0 var(--ink)}
.small-btn:hover{box-shadow:5px 5px 0 var(--ink)}
.hero h1{text-shadow:6px 6px 0 #ffe14d,10px 10px 0 var(--ink)}
.hero-img img,.pages img{border-color:var(--ink)}
.pitch{color:#2b2730}
.small{color:#6a6470}
.price{color:#c98a00}
.btn .price{color:#ffe14d}
.strip{color:#fff;border-color:var(--ink)}
.card.red{box-shadow:6px 6px 0 var(--ink)}
.card.red:hover{box-shadow:9px 9px 0 var(--ink)}
.nav a:not(.btn):hover{color:var(--red)}
.marquee{background:var(--ink);color:var(--paper)}
footer{color:#4a4550}
.links a.l.main{border-color:var(--ink);box-shadow:6px 6px 0 var(--ink)}
.btn{border-radius:14px;border-width:3px}
.small-btn{border-radius:12px}
.top{box-shadow:0 1px 0 rgba(0,0,0,.06),0 8px 24px -16px rgba(0,0,0,.25);backdrop-filter:blur(14px) saturate(1.4)}
.hero-img img{border-width:4px;border-radius:14px}
.pages img{border-width:3px;border-radius:12px}
.card{border-width:3px;border-radius:18px}
.buy-box{border-width:3px;border-radius:24px}
.buy-box img{border-width:3px;border-radius:12px}
.badge{border-radius:999px}
.strip{border-width:3px}
.js .rv{opacity:0;transform:translateY(18px);transition:opacity .6s ease,transform .6s cubic-bezier(.2,.8,.2,1)}
.js .rv.in{opacity:1;transform:none}
@media (prefers-reduced-motion:reduce){*{transition:none !important;animation:none !important}
.js .rv{opacity:1;transform:none}
html{scroll-behavior:auto}
}
`;

const page = (title: string, body: string) => `<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title>
<link rel="icon" href="/img/favicon.png"><meta name="robots" content="noindex"><link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin><link href="https://fonts.googleapis.com/css2?family=Bangers&family=Inter:wght@400;600;800;900&display=swap" rel="stylesheet"><link rel="stylesheet" href="/style.css"></head>
<body class="simple"><main class="box"><a href="/" class="brand">FENUA <span>MANGA</span></a>${body}</main></body></html>`;
const html = (s: string, status = 200) => new Response(s, { status, headers: { "Content-Type": "text/html; charset=utf-8" } });

async function stripe(path: string, body?: URLSearchParams) {
  const res = await fetch(`https://api.stripe.com/v1/${path}`, {
    method: body ? "POST" : "GET",
    headers: { Authorization: `Bearer ${STRIPE_KEY}`, "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  const data: any = await res.json();
  if (!res.ok) throw new Error(data?.error?.message ?? `Stripe ${res.status}`);
  return data;
}
async function paid(id: string | null) {
  if (!id || !/^cs_(test|live)_[A-Za-z0-9]+$/.test(id)) return null;
  try { const s = await stripe(`checkout/sessions/${id}`); return s.payment_status === "paid" ? s : null; } catch { return null; }
}
const productOf = (s: any): Product => PRODUCTS[s?.metadata?.produit] ?? PRODUCTS["mana-t1"];

// Fichiers acceptés par la page admin (nom du fichier envoyé)
const ALLOWED = /^(MANA-Tome1\.pdf|TOA-Tome1\.pdf|page-0[1-9]\.jpg|page-1[0-6]\.jpg|toa-0[1-5]\.jpg|logo\.png|favicon\.png|og\.jpg|tshirt-[a-z0-9-]+\.(png|jpg))$/;
const destOf = (name: string) =>
  name === "MANA-Tome1.pdf" ? PRODUCTS["mana-t1"].pdf : name === "TOA-Tome1.pdf" ? PRODUCTS["toa-t1"].pdf : `${DATA}/img/${name}`;

// Emplacements nommés : le fichier est enregistré sous le bon nom, peu importe comment il s'appelle sur le téléphone
const SLOTS: [string, string][] = [
  ["TOA-Tome1.pdf", "TOA · le PDF complet"],
  ["toa-01.jpg", "TOA · couverture (page 1)"],
  ["toa-02.jpg", "TOA · extrait 1"],
  ["toa-03.jpg", "TOA · extrait 2"],
  ["toa-04.jpg", "TOA · extrait 3"],
  ["toa-05.jpg", "TOA · extrait 4"],
  ["tshirt-manu-combat.png", "T-shirt TOA · Manu en garde (PNG détouré)"],
  ["tshirt-manu-debout.png", "T-shirt Le Toa vide · Manu debout (PNG détouré)"],
  ["tshirt-daytho-logo-noir.png", "DAYTHO · logo encre noire (PNG transparent)"],
  ["tshirt-daytho-logo-blanc.png", "DAYTHO · logo encre blanche (PNG transparent)"],
  ["tshirt-daytho-garcon.png", "DAYTHO · dos garçon, t-shirts clairs"],
  ["tshirt-daytho-garcon-sombre.png", "DAYTHO · dos garçon, t-shirts foncés"],
  ["tshirt-daytho-fille.png", "DAYTHO · dos fille, t-shirts clairs"],
  ["tshirt-daytho-fille-sombre.png", "DAYTHO · dos fille, t-shirts foncés"],
];
const CHECK = ["TOA-Tome1.pdf", "toa-01.jpg", "toa-02.jpg", "toa-03.jpg", "toa-04.jpg", "toa-05.jpg",
  "MANA-Tome1.pdf", "page-01.jpg", "page-04.jpg", "page-05.jpg", "page-06.jpg", "page-07.jpg", "tshirt-manu-combat.png", "tshirt-manu-debout.png",
  "tshirt-daytho-logo-noir.png", "tshirt-daytho-logo-blanc.png", "tshirt-daytho-logo-mono.png",
  "tshirt-daytho-garcon.png", "tshirt-daytho-garcon-sombre.png", "tshirt-daytho-garcon-rouge.png",
  "tshirt-daytho-fille.png", "tshirt-daytho-fille-sombre.png", "tshirt-daytho-fille-rouge.png"];

const adminForm = (msg = "", status = "") => page("Admin", `<h1>Dépôt des fichiers</h1>${msg}
<form method="post" enctype="multipart/form-data">
  <h2>Fichiers</h2>
  ${SLOTS.map(([n, l]) => `<label>${l}<input type="file" name="slot:${n}" accept="${n.endsWith(".pdf") ? "application/pdf" : "image/*"}"></label>`).join("\n  ")}
  <h2>Autres fichiers</h2>
  <label>Fichiers déjà bien nommés (MANA-Tome1.pdf, page-01.jpg…)<input type="file" name="f" multiple></label>
  <p></p>
  <button class="btn" type="submit">Envoyer</button>
</form>
<p class="small">${status}</p>`);

// Import automatique au démarrage : IMPORT_FILES = {"TOA-Tome1.pdf":"https://…", "toa-01.jpg":"https://…"}
async function importFiles() {
  let list: Record<string, string> = {};
  try { list = JSON.parse(Bun.env.IMPORT_FILES ?? "{}"); } catch { console.error("IMPORT_FILES invalide"); return; }
  for (const [name, src] of Object.entries(list)) {
    if (!ALLOWED.test(name) || !/^https:\/\//.test(src)) continue;
    try {
      const r = await fetch(src);
      if (!r.ok) { console.error("import", name, r.status); continue; }
      const buf = await r.arrayBuffer();
      await Bun.write(destOf(name), buf, { createPath: true });
      console.log("import OK", name, buf.byteLength);
      if (name.startsWith("tshirt-")) { assetCache.delete(name); pngCache.clear(); }
    } catch (e) { console.error("import", name, e); }
  }
}

/* ======================================================================
   T-SHIRTS FENUA MANGA — Printful + Stripe (designs vectoriels dessinés par Claude)
   Variables : PRINTFUL_API_KEY, PRINTFUL_STORE_ID (option), PRINTFUL_AUTO_CONFIRM, STRIPE_WEBHOOK_SECRET
   ====================================================================== */
const env = (k: string) => (Bun.env[k] || "").trim();
const TPUB = (env("PUBLIC_URL") || `https://${env("RAILWAY_PUBLIC_DOMAIN")}`).replace(/\/$/, "");
// Version des visuels : à augmenter quand une image change, pour que navigateurs et Printful ne gardent pas l'ancienne
const ASSET_V = "3";
/* ===================== DESIGNS (vectoriels, 4500x5400 = 15"x18" @300 DPI) ===================== */
const W = 4500, H = 5400;
const rd = (n: number) => Math.round(n * 10) / 10;
const svg = (body: string) => `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${body}</svg>`;
// Logo poitrine : un design pleine page réduit et placé sur le cœur (à droite vu de face)
const chest = (body: string) => svg(`<g transform="translate(2780 420) scale(0.3)">${body}</g>`);

// Logo FENUA MANGA (badge rond) dans une boîte 1000x1000
const LRED = "#E0141E", LINK = "#0A0A0A", PAPER = "#FBF8F1";
function spikes(n: number) {
  let d = "";
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2, w = (Math.PI / n) * 0.5, rin = i % 2 ? 395 : 430 - (i % 3) * 12;
    const P = (ang: number, r: number) => `${rd(500 + Math.cos(ang) * r)},${rd(500 + Math.sin(ang) * r)}`;
    d += `M${P(a - w, 520)}L${P(a, rin)}L${P(a + w, 520)}Z`;
  }
  return `<path d="${d}" fill="${LINK}"/>`;
}
function logoBadge() {
  return `<defs><clipPath id="lc"><circle cx="500" cy="500" r="500"/></clipPath>
  <pattern id="dots" width="16" height="16" patternUnits="userSpaceOnUse"><circle cx="8" cy="8" r="2.6" fill="#2a2a2a"/></pattern></defs>
  <g clip-path="url(#lc)"><circle cx="500" cy="500" r="500" fill="${LRED}"/>${spikes(72)}</g>
  <circle cx="500" cy="500" r="362" fill="${PAPER}"/><circle cx="500" cy="500" r="344" fill="${LINK}"/><circle cx="500" cy="500" r="344" fill="url(#dots)"/>
  <circle cx="500" cy="500" r="497" fill="none" stroke="${LINK}" stroke-width="8"/>
  <text x="500" y="262" font-family="Noto Sans JP" font-weight="500" font-size="62" letter-spacing="22" text-anchor="middle" fill="${LRED}">フェヌア</text>
  <g transform="rotate(-7 500 520)">
   <text x="510" y="492" font-family="Archivo Black" font-size="138" text-anchor="middle" fill="${LRED}">FENUA</text>
   <text x="500" y="482" font-family="Archivo Black" font-size="138" text-anchor="middle" fill="${PAPER}">FENUA</text>
   <text x="510" y="640" font-family="Archivo Black" font-size="138" text-anchor="middle" fill="${PAPER}">MANGA</text>
   <text x="500" y="630" font-family="Archivo Black" font-size="138" text-anchor="middle" fill="${LRED}">MANGA</text>
  </g>`;
}
// Logo MANA remasterisé : éclipse, aileron de requin, motif niho mano, マナ
function nihoBand(x0: number, x1: number, y: number, h: number, n: number, col: string) {
  const w = (x1 - x0) / n; let d = "";
  for (let i = 0; i < n; i++) { const a = x0 + i * w; d += `M${rd(a)},${y + h}L${rd(a + w / 2)},${y}L${rd(a + w)},${y + h}Z`; }
  return `<path d="${d}" fill="${col}"/>`;
}
function manaBody() {
  const cx = 2250, cy = 1500;
  let rays = "";
  for (let i = 0; i < 48; i++) { const a = (i / 48) * Math.PI * 2, w = 0.03, r1 = 1060, r2 = i % 2 ? 1280 : 1380; const P = (t: number, r: number) => `${rd(cx + Math.cos(t) * r)},${rd(cy + Math.sin(t) * r)}`; rays += `M${P(a - w, r1)}L${P(a, r2)}L${P(a + w, r1)}Z`; }
  return `<path d="${rays}" fill="${LRED}"/>
  <circle cx="${cx}" cy="${cy}" r="1000" fill="${LRED}"/>
  <circle cx="${cx}" cy="${cy}" r="900" fill="${LINK}" stroke="${PAPER}" stroke-width="40"/>
  <defs><clipPath id="ec"><circle cx="2250" cy="1500" r="880"/></clipPath></defs><g clip-path="url(#ec)"><path d="M1600,2000 C1850,1550 2100,1050 2550,820 C2420,1250 2560,1700 2950,2000 Z" fill="${PAPER}" stroke="${LINK}" stroke-width="30"/><path d="M1300,2000 Q1500,1900 1700,2000 T2100,2000 T2500,2000 T2900,2000 T3300,2000 L3300,2500 L1300,2500 Z" fill="${LRED}"/></g>
  <text x="2250" y="3350" font-family="Archivo Black" font-size="1150" text-anchor="middle" fill="${LINK}" stroke="${LINK}" stroke-width="120" stroke-linejoin="round" letter-spacing="40">MANA</text>
  <text x="2250" y="3350" font-family="Archivo Black" font-size="1150" text-anchor="middle" fill="${PAPER}" stroke="${LRED}" stroke-width="36" stroke-linejoin="round" paint-order="stroke" letter-spacing="40">MANA</text>
  <rect x="450" y="3600" width="3600" height="260" fill="${LINK}" stroke="${PAPER}" stroke-width="20"/>
  ${nihoBand(470, 4030, 3640, 180, 26, LRED)}
  <text x="2250" y="4500" font-family="Noto Sans JP" font-weight="900" font-size="440" text-anchor="middle" fill="${LRED}" stroke="${LINK}" stroke-width="40" paint-order="stroke" letter-spacing="120">マナ</text>
  <text x="2250" y="5050" font-family="Archivo Black" font-size="230" text-anchor="middle" fill="${PAPER}" stroke="${LINK}" stroke-width="30" paint-order="stroke" letter-spacing="60">FENUA MANGA · TAHITI</text>`;
}
// Image envoyée par la page admin (ex. tshirt-manu.png), en data URI pour le rendu
const assetCache = new Map<string, string>();
async function asset(name: string) {
  if (assetCache.has(name)) return assetCache.get(name)!;
  for (const n of [name, name.replace(/\.png$/, ".jpg")]) {
    const f = Bun.file(`${DATA}/img/${n}`);
    if (await f.exists()) {
      const buf = Buffer.from(await f.arrayBuffer());
      const mime = buf[0] === 0x89 ? "image/png" : "image/jpeg";
      const uri = `data:${mime};base64,${buf.toString("base64")}`;
      assetCache.set(name, uri);
      return uri;
    }
  }
  return null;
}
// Personnage détouré (PNG transparent 1080x1920) en grand au dos, titre en bas
async function cutoutBack(file: string, title: string) {
  const img = await asset(file);
  if (!img) return null;
  const ih = 4950, iw = Math.round((ih * 1080) / 1920), ix = Math.round((W - iw) / 2);
  return svg(`<image href="${img}" x="${ix}" y="-60" width="${iw}" height="${ih}" preserveAspectRatio="xMidYMid meet"/>
    <text x="2250" y="5330" font-family="Archivo Black" font-size="300" text-anchor="middle" fill="${PAPER}" stroke="${LINK}" stroke-width="30" paint-order="stroke" letter-spacing="30">${title}</text>`);
}
// Image PNG transparente placée dans une boîte de la toile d'impression
async function imageIn(file: string, x: number, y: number, w: number, h: number, align = "xMidYMin") {
  const img = await asset(file);
  if (!img) return null;
  return svg(`<image href="${img}" x="${x}" y="${y}" width="${w}" height="${h}" preserveAspectRatio="${align} meet"/>`);
}
// DAYTHO : logo sur le cœur (boîte 1350x330 côté droit vu de face), dessin pleine hauteur au dos.
// Trois versions de chaque visuel : « clair » (encre noire, t-shirts clairs), « sombre » (traits blancs, t-shirts foncés),
// « rouge » (comme sombre, mais les détails rouges passent en blanc pour rester visibles sur un t-shirt rouge).
type Ink = "noir" | "blanc" | "mono";
const daythoChest = (ink: Ink) => () => imageIn(`tshirt-daytho-logo-${ink}.png`, 2780, 480, 1350, 330, "xMaxYMin");
const daythoBack = (file: string) => () => imageIn(file, 0, 0, W, H, "xMidYMin");
const daythoSet = (who: "garcon" | "fille") => ({
  [`daytho-${who}`]: { front: daythoChest("noir"), back: daythoBack(`tshirt-daytho-${who}.png`) },
  [`daytho-${who}-sombre`]: { front: daythoChest("blanc"), back: daythoBack(`tshirt-daytho-${who}-sombre.png`) },
  [`daytho-${who}-rouge`]: { front: daythoChest("mono"), back: daythoBack(`tshirt-daytho-${who}-rouge.png`) },
});

const DESIGNS: Record<string, Record<string, () => string | null | Promise<string | null>>> = {
  toa: {
    front: () => svg(`<g transform="translate(2780 420) scale(1.25)">${logoBadge()}</g>`),
    back: () => cutoutBack("tshirt-manu-combat.png", "TOA"),
  },
  toavide: {
    front: () => svg(`<g transform="translate(2780 420) scale(1.25)">${logoBadge()}</g>`),
    back: () => cutoutBack("tshirt-manu-debout.png", "LE TOA VIDE"),
  },
  classic: {
    front: () => svg(`<g transform="translate(450 300) scale(3.6)">${logoBadge()}</g>`),
  },
  manalogo: { front: () => chest(manaBody()) },
  "daytho-logo-noir": { front: daythoChest("noir") },
  "daytho-logo-blanc": { front: daythoChest("blanc") },
  "daytho-logo-mono": { front: daythoChest("mono") },
  ...daythoSet("garcon"),
  ...daythoSet("fille"),
};

/* ===================== CATALOGUE ===================== */
// Printful #12 = Gildan 64000 Softstyle (t-shirt unisexe le moins cher du catalogue, 34 couleurs)
const TEE = 12;
type Color = { pf: string; fr: string; hex: string; design?: string };
type TProduct = { id: string; name: string; tagline: string; desc: string; price: number; design: string; sides: ("front" | "back")[]; printfulProduct: number; colors: Color[]; sizes: string[]; hidden?: boolean; brand?: "fenua" | "daytho" };
const TEE_COLORS: Color[] = [{ pf: "Black", fr: "Noir", hex: "#1b1b1b" }, { pf: "White", fr: "Blanc", hex: "#f4f4f2" }];
const WHITE_ONLY: Color[] = [TEE_COLORS[1]];
// Couleurs DAYTHO : chaque couleur de t-shirt reçoit la version du visuel qui ressort dessus
type Tone = "clair" | "sombre" | "rouge";
const DAYTHO_TEES: { pf: string; fr: string; hex: string; tone: Tone }[] = [
  { pf: "Black", fr: "Noir", hex: "#1b1b1b", tone: "sombre" },
  { pf: "White", fr: "Blanc", hex: "#f4f4f2", tone: "clair" },
  { pf: "Navy", fr: "Bleu marine", hex: "#263046", tone: "sombre" },
  { pf: "Red", fr: "Rouge", hex: "#b8202b", tone: "rouge" },
  { pf: "Heliconia", fr: "Rose", hex: "#d94a86", tone: "sombre" },
  { pf: "Military Green", fr: "Vert", hex: "#5a6145", tone: "sombre" },
];
const LOGO_INK: Record<Tone, string> = { clair: "daytho-logo-noir", sombre: "daytho-logo-blanc", rouge: "daytho-logo-mono" };
const daythoColors = (base: string | null, exclude: string[] = []): Color[] =>
  DAYTHO_TEES.filter((t) => !exclude.includes(t.pf)).map((t) => ({ pf: t.pf, fr: t.fr, hex: t.hex,
    design: base ? (t.tone === "clair" ? base : `${base}-${t.tone}`) : LOGO_INK[t.tone] }));
const SIZES = ["S", "M", "L", "XL", "2XL"];
const TPRODUCTS: TProduct[] = [
  { id: "toa", name: "T-shirt TOA", tagline: "Manu, prêt au combat", desc: "Le logo FENUA MANGA sur le cœur, et dans le dos Manu, le gamin de Faa'a, en garde avec son tatau qui s'embrase. Détouré, sans fond : seul le personnage est imprimé.", price: 3490, design: "toa", sides: ["front", "back"], printfulProduct: TEE, colors: TEE_COLORS, sizes: SIZES },
  { id: "toavide", name: "T-shirt Le Toa vide", tagline: "Manu avant l'éveil", desc: "Le logo FENUA MANGA sur le cœur, et dans le dos Manu, poings serrés, tel qu'on le découvre au début de TOA. Détouré, sans fond. Les tons sombres du dessin ressortent sur le blanc.", price: 3490, design: "toavide", sides: ["front", "back"], printfulProduct: TEE, colors: WHITE_ONLY, sizes: SIZES },
  { id: "classic", name: "T-shirt Fenua Manga", tagline: "Le logo original", desc: "Le logo FENUA MANGA en grand sur la poitrine, façon couverture de manga.", price: 2990, design: "classic", sides: ["front"], printfulProduct: TEE, colors: TEE_COLORS, sizes: SIZES },
  // DAYTHO · DTHO 987 (streetwear techwear)
  { id: "daytho-fille", brand: "daytho", name: "DAYTHO · DTHO 987 — Fille", tagline: "Masque rouge, regard froid", desc: "Le logo DAYTHO sur le cœur, et dans le dos la fille au masque rouge, assise, dans son cadre techwear デイソ. Dessin remasterisé en haute définition, adapté à chaque couleur : traits blancs sur les t-shirts foncés, encre noire sur le blanc.", price: 3490, design: "daytho-fille", sides: ["front", "back"], printfulProduct: TEE, colors: daythoColors("daytho-fille"), sizes: SIZES },
  { id: "daytho-garcon", brand: "daytho", name: "DAYTHO · DTHO 987 — Garçon", tagline: "Le poing levé", desc: "Le logo DAYTHO sur le cœur, et dans le dos le garçon à capuche, poing levé, dans son cadre techwear デイソ. Dessin remasterisé en haute définition, adapté à chaque couleur : traits blancs sur les t-shirts foncés, encre noire sur le blanc.", price: 3490, design: "daytho-garcon", sides: ["front", "back"], printfulProduct: TEE, colors: daythoColors("daytho-garcon", ["Heliconia"]), sizes: SIZES },
  { id: "daytho-logo", brand: "daytho", name: "DAYTHO · Logo", tagline: "DTHO 987, sobre", desc: "Juste le logo DAYTHO デイソ sur le cœur. L'encre s'adapte à la couleur : blanche sur les t-shirts foncés, noire sur le blanc.", price: 2990, design: "daytho-logo-noir", sides: ["front"], printfulProduct: TEE, colors: daythoColors(null), sizes: SIZES },
  // T-shirts test (cachés de la boutique, accessibles par lien direct)
  { id: "test-mana", name: "TEST · T-shirt MANA", tagline: "Commande test (privée)", desc: "T-shirt test : logo MANA remasterisé sur le cœur. Sert à vérifier le circuit complet (paiement, Printful, livraison).", price: 100, design: "manalogo", sides: ["front"], printfulProduct: TEE, colors: TEE_COLORS, sizes: SIZES, hidden: true },
  { id: "test-daytho", name: "TEST · T-shirt DAYTHO", tagline: "Commande test (privée)", desc: "T-shirt test : logo DAYTHO DTHO 987 sur le cœur. Sert à vérifier le circuit complet (paiement, Printful, livraison).", price: 100, design: "daytho-logo-noir", sides: ["front"], printfulProduct: TEE, colors: daythoColors(null), sizes: SIZES, hidden: true },
];
const byId = (id: string) => TPRODUCTS.find((p) => p.id === id);
// Design à imprimer pour une couleur donnée (certains visuels changent d'encre selon le t-shirt)
const designFor = (p: TProduct, colorPf: string) => p.colors.find((c) => c.pf === colorPf)?.design || p.design;
const teuro = (c: number) => (c / 100).toFixed(2).replace(".", ",") + " €";
// Zones de livraison : le client choisit sa zone dans le panier, Stripe n'accepte ensuite que les pays de cette zone
type Zone = { label: string; cents: number; countries: string[] };
const ZONES: Record<string, Zone> = {
  outremer: { label: "Tahiti, outre-mer & Pacifique", cents: 1190, countries: ["PF", "NC", "WF", "RE", "GP", "MQ", "GF", "YT", "PM", "BL", "MF", "AU", "NZ", "JP"] },
  europe: { label: "Europe & Amérique du Nord", cents: 790, countries: ["FR", "BE", "CH", "LU", "MC", "DE", "ES", "IT", "NL", "PT", "AT", "IE", "GB", "US", "CA"] },
};
const SHIPPING_CENTS = ZONES.europe.cents;

/* ===================== RENDU PNG ===================== */
const FONT_SRC = {
  bangers: "https://cdn.jsdelivr.net/npm/@expo-google-fonts/bangers@0.4.1/400Regular/Bangers_400Regular.ttf",
  noto: "https://cdn.jsdelivr.net/npm/@expo-google-fonts/noto-sans-jp@0.4.4/900Black/NotoSansJP_900Black.ttf",
  noto500: "https://cdn.jsdelivr.net/npm/@expo-google-fonts/noto-sans-jp@0.4.4/500Medium/NotoSansJP_500Medium.ttf",
  archivo: "https://cdn.jsdelivr.net/npm/@expo-google-fonts/archivo-black@0.4.1/400Regular/ArchivoBlack_400Regular.ttf",
};
let fontFiles: string[] | null = null;
async function fonts() {
  if (fontFiles) return fontFiles;
  const out: string[] = [];
  for (const [name, url] of Object.entries(FONT_SRC)) {
    const path = `/tmp/fm-${name}.ttf`;
    if (!(await Bun.file(path).exists())) {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`police ${name}: ${res.status}`);
      await Bun.write(path, await res.arrayBuffer());
    }
    out.push(path);
  }
  return (fontFiles = out);
}
const pngCache = new Map<string, Uint8Array>();
async function renderPng(design: string, side: string, width?: number) {
  const key = `${design}/${side}/${width || "full"}`;
  const hit = pngCache.get(key);
  if (hit) return hit;
  const fn = DESIGNS[design]?.[side];
  if (!fn) return null;
  const doc = await fn();
  if (!doc) return null;
  const png = new Resvg(doc, { fitTo: width ? { mode: "width", value: width } : { mode: "original" }, font: { fontFiles: await fonts(), loadSystemFonts: false, defaultFontFamily: "Bangers" } }).render().asPng();
  pngCache.set(key, png);
  return png;
}
// Logo, icône et image de partage générés si les fichiers n'ont pas été déposés
async function brandImage(name: string) {
  const key = `brand/${name}`;
  if (pngCache.has(key)) return pngCache.get(key)!;
  let doc = "", width = 0;
  if (name === "logo.png" || name === "favicon.png") {
    doc = `<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1000" viewBox="0 0 1000 1000">${logoBadge()}</svg>`;
    width = name === "logo.png" ? 512 : 64;
  } else if (name === "og.jpg") {
    doc = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630"><rect width="1200" height="630" fill="${LINK}"/>
      <g transform="translate(60 65) scale(0.5)">${logoBadge()}</g>
      <text x="640" y="250" font-family="Archivo Black" font-size="92" fill="${PAPER}">FENUA</text>
      <text x="640" y="350" font-family="Archivo Black" font-size="92" fill="${LRED}">MANGA</text>
      <text x="642" y="430" font-family="Archivo Black" font-size="34" fill="${PAPER}">TOA · MANA · T-SHIRTS</text>
      <text x="642" y="480" font-family="Noto Sans JP" font-weight="500" font-size="28" fill="#9a9a9a">Le fenua en manga</text></svg>`;
    width = 1200;
  } else return null;
  const png = new Resvg(doc, { fitTo: { mode: "width", value: width }, font: { fontFiles: await fonts(), loadSystemFonts: false } }).render().asPng();
  pngCache.set(key, png);
  return png;
}

/* ===================== STRIPE ===================== */
function formEncode(obj: any, prefix = "", out: string[] = []) {
  for (const [k, v] of Object.entries(obj)) {
    if (v === undefined || v === null) continue;
    const key = prefix ? `${prefix}[${k}]` : k;
    if (typeof v === "object") formEncode(v, key, out);
    else out.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(v))}`);
  }
  return out.join("&");
}
async function tstripe(method: string, path: string, body?: any) {
  const res = await fetch(`https://api.stripe.com/v1${path}`, {
    method, headers: { Authorization: `Bearer ${env("STRIPE_SECRET_KEY")}`, "Content-Type": "application/x-www-form-urlencoded" },
    body: body ? formEncode(body) : undefined,
  });
  const json: any = await res.json();
  if (!res.ok) throw new Error(`Stripe ${res.status}: ${json?.error?.message}`);
  return json;
}
function verifyStripeSig(raw: string, header: string, secret: string) {
  const parts = Object.fromEntries(header.split(",").map((p) => p.split("=") as [string, string]));
  const t = parts.t; if (!t) return false;
  const sigs = header.split(",").filter((p) => p.startsWith("v1=")).map((p) => p.slice(3));
  const expected = createHmac("sha256", secret).update(`${t}.${raw}`).digest("hex");
  const ok = sigs.some((s) => s.length === expected.length && timingSafeEqual(Buffer.from(s), Buffer.from(expected)));
  return ok && Math.abs(Date.now() / 1000 - Number(t)) < 600;
}

/* ===================== PRINTFUL ===================== */
let pfStore: string | null = null;
async function printfulStoreId() {
  if (env("PRINTFUL_STORE_ID")) return env("PRINTFUL_STORE_ID");
  if (pfStore !== null) return pfStore;
  try {
    const r = await fetch("https://api.printful.com/stores", { headers: { Authorization: `Bearer ${env("PRINTFUL_API_KEY")}` } });
    const j: any = await r.json().catch(() => ({}));
    const list = Array.isArray(j?.result) ? j.result : [];
    pfStore = list.length ? String(list[0].id) : "";
    log(`Printful magasins : ${list.map((x: any) => `${x.name} (${x.id}, ${x.type})`).join(", ") || `aucun (${r.status})`}`);
  } catch (e: any) { pfStore = ""; log(`Printful magasins : ${e.message}`); }
  return pfStore;
}
async function printful(method: string, path: string, body?: any) {
  const headers: Record<string, string> = { Authorization: `Bearer ${env("PRINTFUL_API_KEY")}`, "Content-Type": "application/json" };
  const sid = path.startsWith("/stores") ? "" : await printfulStoreId();
  if (sid) headers["X-PF-Store-Id"] = sid;
  const res = await fetch(`https://api.printful.com${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const json: any = await res.json().catch(() => ({}));
  return { ok: res.ok, status: res.status, json };
}
type CatalogInfo = { variants: Map<string, number>; placements: string[] };
const catalogCache = new Map<number, CatalogInfo>();
async function catalog(productId: number): Promise<CatalogInfo> {
  const c = catalogCache.get(productId);
  if (c) return c;
  const r = await printful("GET", `/products/${productId}`);
  if (!r.ok) throw new Error(`Printful catalogue ${productId}: ${r.status} ${r.json?.error?.message || r.json?.result || ""}`);
  const variants = new Map<string, number>();
  for (const v of r.json.result.variants) variants.set(`${v.color}|${v.size}`, v.id);
  const placements = (r.json.result.product.files || []).map((f: any) => f.type);
  const info = { variants, placements };
  catalogCache.set(productId, info);
  return info;
}
function placementFor(side: string, placements: string[]) {
  if (side === "front") return placements.includes("front_large") ? "front_large" : "front";
  return side;
}
const printFiles = (p: TProduct, colorPf: string, placements: string[]) =>
  p.sides.map((side) => ({ type: placementFor(side, placements), url: `${TPUB}/print/${designFor(p, colorPf)}/${side}.png?v=${ASSET_V}` }));

/* ===================== COMMANDES ===================== */
type Line = { product: string; color: string; size: string; qty: number };
const events: { at: string; msg: string }[] = [];
const log = (msg: string) => { events.unshift({ at: new Date().toISOString(), msg }); events.length = Math.min(events.length, 100); console.log(msg); };
const done = new Set<string>();

function parseCart(s: string): Line[] {
  return s.split(";").filter(Boolean).map((x) => { const [product, color, size, qty] = x.split("|"); return { product, color, size, qty: Number(qty) || 1 }; });
}
async function fulfill(sessionId: string) {
  if (done.has(sessionId)) return "déjà traitée";
  const s = await tstripe("GET", `/checkout/sessions/${sessionId}?expand[]=customer_details`);
  if (s.payment_status !== "paid") return "pas encore payée";
  const lines = parseCart(s.metadata?.cart || "");
  const ship = s.collected_information?.shipping_details || s.shipping_details;
  const addr = ship?.address || s.customer_details?.address;
  const items = [];
  for (const l of lines) {
    const p = byId(l.product); if (!p) continue;
    const cat = await catalog(p.printfulProduct);
    const variant_id = cat.variants.get(`${l.color}|${l.size}`);
    if (!variant_id) throw new Error(`variante introuvable ${p.id} ${l.color} ${l.size}`);
    items.push({
      variant_id, quantity: l.qty, name: `${p.name} — ${l.color} ${l.size}`, retail_price: (p.price / 100).toFixed(2),
      files: printFiles(p, l.color, cat.placements),
    });
  }
  const external_id = "fm" + createHash("sha256").update(sessionId).digest("hex").slice(0, 28);
  const order = {
    external_id, shipping: "STANDARD",
    recipient: {
      name: ship?.name || s.customer_details?.name, address1: addr?.line1, address2: addr?.line2 || undefined, city: addr?.city,
      state_code: addr?.state || undefined, country_code: addr?.country, zip: addr?.postal_code, email: s.customer_details?.email, phone: s.customer_details?.phone || undefined,
    },
    items, retail_costs: { currency: "EUR", shipping: (Number(s.total_details?.amount_shipping ?? SHIPPING_CENTS) / 100).toFixed(2) },
  };
  const confirm = env("PRINTFUL_AUTO_CONFIRM") === "true";
  const r = await printful("POST", `/orders${confirm ? "?confirm=true" : ""}`, order);
  if (!r.ok && !/external.?id/i.test(JSON.stringify(r.json))) throw new Error(`Printful commande ${r.status}: ${r.json?.error?.message || JSON.stringify(r.json).slice(0, 300)}`);
  done.add(sessionId);
  log(`Commande Printful ${r.ok ? (confirm ? "envoyée en production" : "créée en brouillon") : "déjà existante"} — ${order.recipient.name}, ${items.length} article(s), session ${sessionId.slice(-8)}`);
  return "ok";
}

async function ensureWebhook() {
  if (!env("STRIPE_SECRET_KEY") || env("STRIPE_WEBHOOK_SECRET") || TPUB.includes("localhost")) return;
  try {
    const url = `${TPUB}/stripe/webhook`;
    const list = await tstripe("GET", "/webhook_endpoints?limit=100");
    if (list.data.some((w: any) => w.url === url)) { log("Webhook Stripe déjà enregistré, mais STRIPE_WEBHOOK_SECRET manque."); return; }
    const w = await tstripe("POST", "/webhook_endpoints", { url, enabled_events: ["checkout.session.completed", "checkout.session.async_payment_succeeded"], description: "FENUA MANGA boutique" });
    console.log(`WEBHOOK_CREATED STRIPE_WEBHOOK_SECRET=${w.secret}`);
    log("Webhook Stripe créé : ajouter STRIPE_WEBHOOK_SECRET dans les variables Railway.");
  } catch (e: any) { log(`Webhook Stripe : ${e.message}`); }
}

/* ===================== PAGES ===================== */
const TEE_PATH = "M340,60 C380,110 620,110 660,60 L880,150 L960,380 L820,430 L790,330 L790,1040 L210,1040 L210,330 L180,430 L40,380 L120,150 Z";
function mockupSvg(p: TProduct, colorPf: string, side: "front" | "back", w = 500) {
  const c = p.colors.find((x) => x.pf === colorPf) || p.colors[0];
  const collar = side === "front" ? `<path d="M340,60 C380,140 620,140 660,60" fill="none" stroke="rgba(0,0,0,.25)" stroke-width="10"/>` : `<path d="M340,60 C380,95 620,95 660,60" fill="none" stroke="rgba(0,0,0,.25)" stroke-width="10"/>`;
  const art = p.sides.includes(side) ? `<image href="/print/${designFor(p, c.pf)}/${side}.png?w=700&amp;v=${ASSET_V}" x="275" y="175" width="450" height="540"/>` : "";
  return `<svg viewBox="0 0 1000 1080" width="${w}" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="${p.name} ${c.fr} ${side === "front" ? "devant" : "dos"}"><path d="${TEE_PATH}" fill="${c.hex}" stroke="rgba(128,128,128,.35)" stroke-width="6" stroke-linejoin="round"/>${collar}${art}</svg>`;
}
type TMeta = { noindex?: boolean; image?: string; desc?: string };
const MARQUEE = "IMPRIMÉ À LA DEMANDE ✦ LIVRÉ À TAHITI & PARTOUT ✦ PAIEMENT SÉCURISÉ STRIPE ✦ 100 % FENUA ✦ ";
const tlayout = (title: string, body: string, meta: TMeta = {}) => `<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title}</title><meta name="description" content="${meta.desc || "T-shirts manga tahitiens, imprimés à la demande."}">
${meta.noindex ? `<meta name="robots" content="noindex">` : ""}<meta name="theme-color" content="#fffdf7">
<meta property="og:title" content="${title}"><meta property="og:image" content="${meta.image || `${TPUB}/img/og.jpg`}">
<link rel="icon" href="/img/favicon.png">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Bangers&family=Inter:wght@400;600;800;900&display=swap" rel="stylesheet">
<style>
:root{--bg:#fffdf7;--card:#ffffff;--ink:#0a0a0a;--mute:#5a5560;--red:#ff2a36;--gold:#ffe14d;--line:#e2dccf}
*{box-sizing:border-box}html{scroll-behavior:smooth;overflow-x:hidden;overflow-x:clip}
body{margin:0;background-color:var(--bg);background-size:22px 22px,100% 100%,100% 100%,100% 100%;background-attachment:scroll,fixed,fixed,fixed;color:var(--ink);font-family:Inter,system-ui,sans-serif;line-height:1.5;overflow-x:hidden;overflow-x:clip}
a{color:inherit}::selection{background:var(--red);color:var(--ink)}
:focus-visible{outline:3px solid var(--gold);outline-offset:3px}
header{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:12px 16px;border-bottom:3px solid var(--red);position:sticky;top:0;background:rgba(36,32,42,.88);backdrop-filter:blur(8px);z-index:5}
.logo{font-family:Bangers;font-size:28px;white-space:nowrap;letter-spacing:1.5px;text-decoration:none;line-height:1}.logo span{color:var(--red)}
nav{display:flex;align-items:center;gap:4px}
.navl{font-weight:800;text-decoration:none;white-space:nowrap;padding:6px 8px;font-size:15px;text-transform:uppercase;letter-spacing:.05em}
.navl:hover{color:var(--gold)}
.cartbtn{position:relative;white-space:nowrap;background:var(--red);color:var(--ink);border:3px solid var(--ink);padding:6px 12px;font-weight:900;text-decoration:none;box-shadow:3px 3px 0 var(--ink);text-transform:uppercase;font-size:14px;margin-left:6px;transition:transform .1s,box-shadow .1s}
.cartbtn:hover{transform:translate(-2px,-2px);box-shadow:5px 5px 0 var(--ink)}
#cc{display:inline-grid;place-items:center;min-width:22px;height:22px;margin-left:4px;padding:0 5px;background:var(--gold);color:#111;border:2px solid #111;font-size:12px}
@media(max-width:520px){.navl.opt{display:none}}
.marquee{overflow:hidden;background:var(--red);border-bottom:3px solid var(--ink);white-space:nowrap;font-family:Bangers;font-size:20px;letter-spacing:1.5px;padding:5px 0;color:var(--ink)}
.marquee div{display:inline-block;animation:mq 26s linear infinite}
@keyframes mq{to{transform:translateX(-50%)}}
main{max-width:1080px;margin:0 auto;padding:16px}
.hero{position:relative;padding:48px 0 28px;text-align:center;isolation:isolate}
.hero::before{content:"";position:absolute;inset:0 -40vw;z-index:-1;background:repeating-conic-gradient(from 0deg at 50% 60%,rgba(255,42,54,.22) 0 4deg,transparent 4deg 12deg);-webkit-mask:radial-gradient(circle at 50% 60%,transparent 12%,#000 60%);mask:radial-gradient(circle at 50% 60%,transparent 12%,#000 60%)}
.kata{color:var(--red);font-weight:800;letter-spacing:.4em;font-size:15px}
.hero h1{font-family:Bangers;font-size:clamp(54px,12vw,112px);margin:6px 0 0;line-height:.9;letter-spacing:2px;-webkit-text-stroke:3px #000;paint-order:stroke fill;text-shadow:5px 5px 0 var(--red)}
.hero h1 em{color:var(--red);font-style:normal;text-shadow:5px 5px 0 var(--ink)}.hero p{color:var(--mute);max-width:560px;margin:16px auto 0;font-size:17px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:24px;margin-top:28px}
.card{position:relative;background:var(--card);border:4px solid var(--ink);box-shadow:8px 8px 0 var(--red);text-decoration:none;display:block;transition:transform .15s,box-shadow .15s}
.card:hover{transform:translate(-4px,-4px) rotate(-.6deg);box-shadow:12px 12px 0 var(--red)}
.card .img{background:radial-gradient(rgba(255,255,255,.08) 22%,transparent 23%) 0 0/14px 14px,radial-gradient(circle at 50% 40%,#5a4f60,#302a37 72%);display:flex;justify-content:center;padding:22px 18px;border-bottom:4px solid var(--ink)}
.card .img svg{width:100%;max-width:300px;height:auto;transition:transform .25s}.card:hover .img svg{transform:scale(1.04)}
.card .txt{padding:14px 16px 18px}
.card h3{font-family:Bangers;font-size:30px;margin:0;letter-spacing:.8px;line-height:1.05}
.tag{position:absolute;top:14px;right:-10px;background:var(--gold);color:#111;font-weight:900;font-size:17px;padding:6px 12px;border:3px solid #111;transform:rotate(4deg);box-shadow:3px 3px 0 #111}
.price{color:var(--gold);font-weight:900}
.dots{display:flex;gap:6px;margin-top:10px}.dots i{width:16px;height:16px;border:2px solid var(--ink);display:inline-block}
.mute{color:var(--mute)}
.crumb{display:inline-block;margin:6px 0 14px;font-weight:800;text-decoration:none;text-transform:uppercase;letter-spacing:.06em;font-size:14px;color:var(--mute)}.crumb:hover{color:var(--gold)}
.prod{display:grid;grid-template-columns:1fr 1fr;gap:36px;margin-top:4px;align-items:start}
.prod>div:first-child{position:sticky;top:84px}
@media(max-width:760px){.prod{grid-template-columns:1fr;gap:24px}.prod>div:first-child{position:static}}
.views{background:radial-gradient(rgba(255,255,255,.08) 22%,transparent 23%) 0 0/14px 14px,radial-gradient(circle at 50% 40%,#5a4f60,#302a37 72%);border:5px solid var(--ink);box-shadow:12px 12px 0 var(--red);padding:20px;display:flex;justify-content:center}
.views svg{width:100%;max-width:440px;height:auto}.tabs{display:flex;gap:10px;margin-top:20px}
.pill{border:3px solid var(--ink);background:var(--card);color:var(--ink);padding:8px 16px;font-weight:900;cursor:pointer;font-family:inherit;font-size:15px;box-shadow:3px 3px 0 var(--ink);transition:transform .1s,box-shadow .1s}
.pill:hover{transform:translate(-1px,-1px);box-shadow:4px 4px 0 var(--ink)}
.pill[aria-pressed=true]{background:var(--red);color:var(--ink);box-shadow:3px 3px 0 var(--gold)}
.label{font-weight:900;text-transform:uppercase;letter-spacing:.1em;font-size:13px;color:var(--mute)}.label b{color:var(--ink)}
.opts{display:flex;flex-wrap:wrap;gap:10px;margin:10px 0 22px}
.swatch{width:42px;height:42px;border:3px solid var(--ink);cursor:pointer;box-shadow:3px 3px 0 #000;transition:transform .1s}
.swatch:hover{transform:translateY(-2px)}.swatch[aria-pressed=true]{outline:3px solid var(--gold);outline-offset:3px}
.bigprice{display:inline-block;background:var(--gold);color:#111;font-weight:900;font-size:26px;padding:4px 14px;border:3px solid #111;box-shadow:4px 4px 0 var(--red);transform:rotate(-2deg);margin:14px 0 6px}
.buy{width:100%;background:var(--red);color:var(--ink);border:4px solid var(--ink);padding:18px;font-size:22px;font-family:Bangers;letter-spacing:1.5px;cursor:pointer;box-shadow:7px 7px 0 var(--ink);transition:transform .1s,box-shadow .1s}
.buy:hover:not(:disabled){transform:translate(-2px,-2px);box-shadow:9px 9px 0 var(--ink)}
.buy:active:not(:disabled){transform:translate(4px,4px);box-shadow:3px 3px 0 var(--ink)}
.buy:disabled{opacity:.45;cursor:not-allowed}
h2{font-family:Bangers;font-size:clamp(40px,7vw,56px);margin:0;letter-spacing:1px;line-height:.95}
.perks{list-style:none;padding:0;margin:22px 0 0;display:grid;gap:8px}
.perks li{background:var(--card);border:2px solid var(--line);border-left:5px solid var(--red);padding:10px 12px;font-size:14px;color:#f1eae2}
.private{display:inline-block;background:var(--ink);color:#111;font-weight:900;font-size:12px;letter-spacing:.2em;padding:3px 8px;margin-bottom:8px}
.box{background:var(--card);border:4px solid var(--ink);box-shadow:10px 10px 0 var(--red);padding:6px 18px 18px;margin:18px 0}
.row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 0;border-bottom:2px dashed var(--line)}
.row .pill{padding:2px 10px;box-shadow:2px 2px 0 var(--ink)}
#tot{font-size:16px;line-height:1.8}
footer{max-width:1080px;margin:56px auto 0;padding:28px 16px 40px;border-top:4px solid var(--red);color:var(--mute);font-size:14px;text-align:center}
footer .logo{display:inline-block;font-size:34px;color:var(--ink);margin-bottom:6px}
.notice{background:var(--gold);color:#111;border:3px solid #111;box-shadow:5px 5px 0 var(--red);padding:12px 14px;margin:16px 0;font-weight:800}
select{width:100%;background:var(--card);color:var(--ink);border:3px solid var(--ink);padding:12px;font-size:16px;font-family:inherit;font-weight:700;text-overflow:ellipsis;margin:6px 0 14px;box-shadow:4px 4px 0 var(--red)}
.dsec{margin-top:72px;padding:28px 0 0;border-top:3px solid var(--ink);position:relative}
.dsec::before{content:"";position:absolute;left:0;top:-3px;width:120px;height:3px;background:var(--red)}
.dhead{display:flex;align-items:flex-end;justify-content:space-between;gap:12px;flex-wrap:wrap}
.dhead h2{font-family:Inter,system-ui,sans-serif;font-weight:900;font-size:clamp(38px,8vw,64px);letter-spacing:.22em}
.dhead h2 span{color:var(--red);font-size:.5em;letter-spacing:.1em;margin-left:10px}
.dept{font-size:13px;letter-spacing:.5em;color:var(--mute);border:1px solid var(--line);padding:4px 10px}
.dsec .card{border-width:2px;box-shadow:6px 6px 0 #14121a}.dsec .card:hover{box-shadow:10px 10px 0 var(--red);transform:translate(-4px,-4px)}
.dsec .card h3{font-family:Inter,system-ui,sans-serif;font-weight:900;font-size:19px;letter-spacing:.08em;text-transform:uppercase}

body{background-image:radial-gradient(rgba(0,0,0,.06) 18%,transparent 19%),radial-gradient(ellipse 70% 50% at 10% 0%,rgba(255,42,54,.12),transparent 70%),radial-gradient(ellipse 60% 50% at 95% 35%,rgba(42,200,180,.12),transparent 70%),radial-gradient(ellipse 70% 50% at 20% 100%,rgba(255,200,60,.14),transparent 70%)}
header{background:rgba(255,253,247,.92)}
.cartbtn,.buy,.marquee,.pill[aria-pressed=true],.private{color:#fff}
.marquee{background:var(--ink);border-bottom-color:var(--red)}
.hero h1{color:var(--ink);-webkit-text-stroke:0;text-shadow:5px 5px 0 #ffe14d}.hero h1 em{text-shadow:5px 5px 0 var(--ink)}
.card .img,.views{background:radial-gradient(rgba(0,0,0,.06) 22%,transparent 23%) 0 0/14px 14px,radial-gradient(circle at 50% 40%,#ffffff,#efe9df 72%)}
.perks li{color:#2b2730}.dsec .card{box-shadow:6px 6px 0 #d8d1c4}
.price{color:#c98a00}
/* Moderne */
header{box-shadow:0 1px 0 rgba(0,0,0,.06),0 8px 24px -16px rgba(0,0,0,.25);backdrop-filter:blur(14px) saturate(1.4)}
.cartbtn{border-radius:12px;border-width:2px}#cc{border-radius:999px}
.card{border-width:3px;border-radius:20px;overflow:hidden}.card .img{border-bottom-width:3px}
.tag{border-radius:999px;right:12px;top:12px;border-width:2px}
.views{border-width:3px;border-radius:24px}
.pill{border-radius:12px;border-width:2px}.swatch{border-radius:12px;border-width:2px}
.buy{border-radius:16px;border-width:3px}
.bigprice{border-radius:12px;border-width:2px}
.box{border-width:3px;border-radius:22px}
.perks li{border-radius:12px}
select{border-radius:12px;border-width:2px}
.private,.dept{border-radius:999px}
.notice{border-radius:14px}
.dsec .card{border-radius:16px}
.js .rv{opacity:0;transform:translateY(18px);transition:opacity .6s ease,transform .6s cubic-bezier(.2,.8,.2,1)}
.js .rv.in{opacity:1;transform:none}
@media(max-width:760px){#add{position:sticky;bottom:12px;z-index:4;box-shadow:0 10px 30px -8px rgba(0,0,0,.45),5px 5px 0 var(--ink)}}
${SPONSOR_CSS}
@media(prefers-reduced-motion:reduce){*{transition:none!important;animation:none!important}.js .rv{opacity:1;transform:none}}
</style>${REVEAL}<script>
const getCart=()=>{try{return JSON.parse(localStorage.getItem('fm_cart')||'[]')}catch(e){return []}};
const setCart=c=>{try{localStorage.setItem('fm_cart',JSON.stringify(c))}catch(e){};count()};
function count(){const el=document.getElementById('cc');if(el)el.textContent=getCart().reduce((a,l)=>a+l.qty,0)}document.addEventListener('DOMContentLoaded',count);
</script></head><body><header><a class="logo" href="/">FENUA <span>MANGA</span></a><nav><a class="navl opt" href="/">Mangas</a><a class="navl" href="/tshirts">T-shirts</a><a class="navl opt" href="/tshirts#daytho">Daytho</a><a class="cartbtn" href="/panier">Panier<span id="cc">0</span></a></nav></header>
<div class="marquee" aria-hidden="true"><div>${MARQUEE.repeat(6)}</div></div><main>${body}</main>
<footer><a class="logo" href="/">FENUA <span>MANGA</span></a><br>Tahiti — T-shirts imprimés à la demande et livrés par notre partenaire d'impression. Paiement sécurisé par Stripe.<br><a href="/">Les mangas</a> · <a href="/tshirts">T-shirts</a> · <a href="/livraison">Livraison & retours</a> · <a href="/soutenir">Soutenir</a> · <a href="/liens">Liens</a> · <a href="/mentions-legales">Mentions légales</a></footer>
</body></html>`;

function card(p: TProduct) {
  const dots = p.colors.length > 2 ? `<div class="dots">${p.colors.map((c) => `<i style="background:${c.hex}" title="${c.fr}"></i>`).join("")}</div>` : "";
  return `<a class="card rv" href="/tshirts/${p.id}"><span class="tag">${teuro(p.price)}</span><div class="img">${mockupSvg(p, p.colors[0].pf, p.sides.includes("back") ? "back" : "front", 300)}</div>
  <div class="txt"><h3>${p.name}</h3><div class="mute">${p.tagline}</div>${dots}</div></a>`;
}
function home() {
  const fenua = TPRODUCTS.filter((p) => !p.hidden && p.brand !== "daytho").map(card).join("");
  const daytho = TPRODUCTS.filter((p) => !p.hidden && p.brand === "daytho").map(card).join("");
  return tlayout("T-shirts — FENUA MANGA", `<section class="hero"><div class="kata">ティーシャツ</div><h1>LES T-SHIRTS<br><em>FENUA MANGA</em></h1>
  <p>Tirés de nos mangas. Imprimés à la commande, livrés chez toi.</p></section><div class="grid">${fenua}</div>
  <section class="dsec" id="daytho"><div class="dhead"><h2>DAYTHO<span>デイソ</span></h2><div class="dept">DTHO 987</div></div>
  <p class="mute">Streetwear techwear. Logo sur le cœur, personnages au dos. Noir, blanc, bleu marine, rouge, rose et vert.</p><div class="grid">${daytho}</div></section>
  ${sponsorBox()}
  <section class="box" style="text-align:center;max-width:720px;margin:28px auto"><h2>Soutiens FENUA MANGA 🤝</h2><p class="mute">Chaque soutien finance les prochains tomes. Les soutiens reçoivent les épisodes en avance et leur nom au générique.</p><a class="buy" style="display:inline-block;width:auto;padding:14px 28px;text-decoration:none" href="/soutenir">Soutenir sur Ko-fi</a></section>`);
}
function productPage(p: TProduct) {
  const sw = p.colors.map((c, i) => `<button class="swatch" title="${c.fr}" aria-label="${c.fr}" aria-pressed="${i === 0}" data-c="${c.pf}" style="background:${c.hex}"></button>`).join("");
  const sz = p.sizes.map((s, i) => `<button class="pill" aria-pressed="${i === 1}" data-s="${s}">${s}</button>`).join("");
  const views: Record<string, string> = {};
  for (const c of p.colors) for (const side of p.sides) views[`${c.pf}|${side}`] = mockupSvg(p, c.pf, side, 440);
  const startSide = p.sides.includes("back") ? "back" : p.sides[0];
  const tabs = p.sides.length > 1 ? `<div class="tabs">${p.sides.map((s) => `<button class="pill" aria-pressed="${s === startSide}" data-v="${s}">${s === "front" ? "Devant" : "Dos"}</button>`).join("")}</div>` : "";
  const back = p.brand === "daytho" ? "/tshirts#daytho" : "/tshirts";
  return tlayout(`${p.name} — FENUA MANGA`, `<a class="crumb" href="${back}">← Tous les t-shirts</a><div class="prod"><div><div class="views" id="view">${views[`${p.colors[0].pf}|${startSide}`]}</div>${tabs}</div>
  <div>${p.hidden ? `<div class="private">PRIVÉ · TEST</div>` : ""}<h2>${p.name}</h2><div class="mute">${p.tagline}</div><div class="bigprice">${teuro(p.price)}${p.hidden ? " · livraison offerte" : ""}</div><p>${p.desc}</p>
  <div class="label">Couleur : <b id="cn">${p.colors[0].fr}</b></div><div class="opts">${sw}</div>
  <div class="label">Taille</div><div class="opts" id="sizes">${sz}</div>
  <button class="buy" id="add">Ajouter au panier</button>
  <ul class="perks"><li>👕 Coton 100 % ringspun (Gildan 64000 Softstyle), doux et léger, coupe unisexe.</li><li>🖨️ Imprimé à la demande : expédition sous 2 à 5 jours ouvrés.</li><li>🔒 Paiement sécurisé par Stripe · <a href="/livraison">Livraison & retours</a></li><li>📖 Tu n'as pas encore lu TOA ? <a href="/#toa">Le Tome 1 en PDF, ${TOA_P}</a></li></ul></div></div>
  <script>
  const V=${JSON.stringify(views)},C=${JSON.stringify(Object.fromEntries(p.colors.map((c) => [c.pf, c.fr])))};let c="${p.colors[0].pf}",s="${p.sizes[1]}",v="${startSide}";
  const draw=()=>document.getElementById('view').innerHTML=V[c+'|'+v];
  const pick=(sel,attr,fn)=>document.querySelectorAll(sel).forEach(b=>b.onclick=()=>{document.querySelectorAll(sel).forEach(x=>x.setAttribute('aria-pressed','false'));b.setAttribute('aria-pressed','true');fn(b.dataset[attr]);});
  pick('.swatch','c',x=>{c=x;document.getElementById('cn').textContent=C[x];draw()});pick('#sizes .pill','s',x=>s=x);pick('.tabs .pill','v',x=>{v=x;draw()});
  document.getElementById('add').onclick=()=>{const cart=getCart();const f=cart.find(l=>l.product==='${p.id}'&&l.color===c&&l.size===s);if(f)f.qty++;else cart.push({product:'${p.id}',color:c,size:s,qty:1});setCart(cart);location.href='/panier'};
  </script>`, { noindex: p.hidden, desc: `${p.name} · ${p.tagline}. ${teuro(p.price)}, imprimé à la demande.`, image: `${TPUB}/print/${p.design}/${p.sides.includes("back") ? "back" : "front"}.png?w=1200&v=${ASSET_V}` });
}
function cartPage() {
  const P = Object.fromEntries(TPRODUCTS.map((p) => [p.id, { name: p.name, price: p.price, hidden: !!p.hidden, colors: Object.fromEntries(p.colors.map((c) => [c.pf, c.fr])) }]));
  const Z = Object.fromEntries(Object.entries(ZONES).map(([k, z]) => [k, { label: z.label, cents: z.cents }]));
  const ready = !!env("STRIPE_SECRET_KEY") && !!env("PRINTFUL_API_KEY");
  const opts = Object.entries(ZONES).map(([k, z]) => `<option value="${k}">${z.label} · ${teuro(z.cents)}</option>`).join("");
  return tlayout("Panier — FENUA MANGA", `<div style="max-width:720px;margin:0 auto"><div class="kata" style="margin-top:20px">カート</div><h2>Ton panier</h2><div class="box"><div id="lines"></div>
  <div id="zonebox"><p class="label" style="margin:18px 0 0">Livraison vers</p><select id="zone">${opts}</select></div>
  <p id="tot"></p></div>
  ${ready ? "" : `<div class="notice">Les t-shirts arrivent très bientôt : la commande ouvre dans quelques jours.</div>`}
  <button class="buy" id="pay" ${ready ? "" : "disabled"}>Payer</button><p class="mute" style="font-size:14px">🔒 Paiement sécurisé par Stripe. Adresse demandée à l'étape suivante.</p></div>
  <script>const P=${JSON.stringify(P)},Z=${JSON.stringify(Z)};const eur=c=>(c/100).toFixed(2).replace('.',',')+' €';
  const zs=document.getElementById('zone');try{const z=localStorage.getItem('fm_zone');if(z&&Z[z])zs.value=z}catch(e){}
  zs.onchange=()=>{try{localStorage.setItem('fm_zone',zs.value)}catch(e){};render()};
  function render(){const cart=getCart().filter(l=>P[l.product]&&P[l.product].colors[l.color]);const el=document.getElementById('lines');
  if(!cart.length){el.innerHTML='<p class="mute">Ton panier est vide. <a href="/tshirts">Voir les t-shirts</a></p>';document.getElementById('pay').disabled=true;document.getElementById('tot').textContent='';return}
  el.innerHTML=cart.map((l,i)=>'<div class="row"><div><b>'+P[l.product].name+'</b><div class="mute">'+P[l.product].colors[l.color]+' · '+l.size+'</div></div><div><button class="pill" data-i="'+i+'" data-d="-1">−</button> '+l.qty+' <button class="pill" data-i="'+i+'" data-d="1">+</button></div><div class="price">'+eur(P[l.product].price*l.qty)+'</div></div>').join('');
  const sub=cart.reduce((a,l)=>a+P[l.product].price*l.qty,0),free=cart.every(l=>P[l.product].hidden),ship=free?0:Z[zs.value].cents;
  document.getElementById('tot').innerHTML='Sous-total : '+eur(sub)+'<br>Livraison : '+(ship?eur(ship):'offerte')+'<br><b style="font-size:20px">Total : '+eur(sub+ship)+'</b>';
  el.querySelectorAll('button').forEach(b=>b.onclick=()=>{const c=getCart().filter(l=>P[l.product]&&P[l.product].colors[l.color]);c[b.dataset.i].qty+=Number(b.dataset.d);setCart(c.filter(l=>l.qty>0));render()})}
  render();
  document.getElementById('pay').onclick=async e=>{e.target.disabled=true;e.target.textContent='Redirection…';
  const r=await fetch('/tshirts/checkout',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({cart:getCart(),zone:zs.value})});const j=await r.json();
  if(j.url)location.href=j.url;else{alert(j.error||'Erreur');e.target.disabled=false;e.target.textContent='Payer'}};</script>`, { noindex: true });
}

/* ===================== PARTENAIRES ===================== */
const LEADS = `${DATA}/partenaires.jsonl`;
const leadHits = new Map<string, number[]>();
function partnersPage(msg = "") {
  return tlayout("Devenir partenaire — FENUA MANGA", `<div style="max-width:720px;margin:0 auto"><div class="kata" style="margin-top:20px">パートナー</div><h2>Deviens partenaire</h2>
  <p>FENUA MANGA, ce sont des mangas 100 % tahitiens, des épisodes vidéo sur TikTok et YouTube, et une boutique de t-shirts. Un public jeune, fan de manga et de culture du fenua.</p>
  <ul class="perks"><li>📣 Ton logo et ton lien sur le site (accueil, page Liens, boutique)</li><li>🎬 Une mention dans un épisode vidéo</li><li>🤝 Une offre sur mesure : un mois, un tome, un événement</li></ul>
  ${msg}
  <form class="box" method="post" action="/partenaires" style="display:grid;gap:10px">
    <label class="label">Ton nom<input name="nom" required maxlength="80" style="width:100%;padding:10px;font:inherit;border:3px solid var(--ink)"></label>
    <label class="label">Ton commerce / ta marque<input name="marque" maxlength="120" style="width:100%;padding:10px;font:inherit;border:3px solid var(--ink)"></label>
    <label class="label">Comment te contacter (e-mail, téléphone, Instagram…)<input name="contact" required maxlength="160" style="width:100%;padding:10px;font:inherit;border:3px solid var(--ink)"></label>
    <label class="label">Ton idée<textarea name="message" maxlength="1500" rows="4" style="width:100%;padding:10px;font:inherit;border:3px solid var(--ink)"></textarea></label>
    <input name="site" tabindex="-1" autocomplete="off" style="position:absolute;left:-9999px" aria-hidden="true">
    <button class="buy" type="submit">Envoyer ma demande</button>
  </form></div>`, { desc: "Fais connaître ta marque aux fans de manga du fenua : deviens partenaire de FENUA MANGA." });
}
async function readLeads() {
  const f = Bun.file(LEADS);
  if (!(await f.exists())) return [];
  return (await f.text()).split("\n").filter(Boolean).map((l) => { try { return JSON.parse(l); } catch { return null; } }).filter(Boolean).reverse();
}

/* ===================== MENTIONS LÉGALES ===================== */
// LEGAL_NAME, LEGAL_ADDRESS, LEGAL_EMAIL, LEGAL_ID (n° Tahiti / SIRET) : identité de l'éditeur, obligatoire pour un site marchand
function legalPage() {
  const v = (k: string) => esc(cfg(k));
  const ed = [v("LEGAL_NAME") || "FENUA MANGA", v("LEGAL_ADDRESS"), v("LEGAL_ID") && `N° d'entreprise : ${v("LEGAL_ID")}`, v("LEGAL_EMAIL") && `Contact : <a href="mailto:${v("LEGAL_EMAIL")}">${v("LEGAL_EMAIL")}</a>`].filter(Boolean).join("<br>");
  const ads = ADSENSE ? `<p>Des publicités Google AdSense sont affichées sur l'accueil et la page Liens. Elles ne sont chargées que si tu cliques sur « Accepter » dans le bandeau cookies. Google peut alors déposer des cookies pour afficher, mesurer et personnaliser les annonces (<a href="https://policies.google.com/technologies/ads?hl=fr" rel="noopener">en savoir plus</a>). Si tu refuses, aucun script publicitaire n'est chargé.</p>
  <p><button class="buy" type="button" style="width:auto;padding:12px 22px" onclick="fmCookies()">Modifier mon choix cookies</button></p>` : `<p>Aucune publicité ni aucun cookie publicitaire n'est utilisé pour le moment.</p>`;
  return tlayout("Mentions légales — FENUA MANGA", `<div style="max-width:760px;margin:0 auto"><div class="kata" style="margin-top:20px">法的情報</div><h2>Mentions légales</h2>
  <div class="box"><h3>Éditeur</h3><p>${ed}</p>
  <h3>Hébergement</h3><p>Railway Corporation, San Francisco (États-Unis) · <a href="https://railway.com" rel="noopener">railway.com</a></p>
  <h3>Propriété intellectuelle</h3><p>Les histoires, personnages, illustrations et visuels de FENUA MANGA, TOA, MANA et DAYTHO sont protégés. Toute reproduction sans autorisation est interdite.</p></div>
  <h2 style="margin-top:36px">Confidentialité</h2>
  <div class="box"><h3>Ce qu'on collecte</h3><ul>
  <li><b>Achats</b> : le paiement est traité par Stripe. On reçoit ton nom, ton e-mail, ton téléphone et, pour les t-shirts, ton adresse de livraison. On ne voit jamais ta carte bancaire.</li>
  <li><b>T-shirts</b> : ton nom et ton adresse sont transmis à notre partenaire d'impression Printful pour fabriquer et expédier ta commande.</li>
  <li><b>Formulaire partenaires</b> : les informations que tu envoies sont conservées sur notre serveur pour te recontacter.</li></ul>
  <p>Ces données servent uniquement à traiter ta commande ou ta demande. Elles ne sont jamais vendues. Tu peux demander à les consulter, les corriger ou les supprimer${v("LEGAL_EMAIL") ? ` en écrivant à <a href="mailto:${v("LEGAL_EMAIL")}">${v("LEGAL_EMAIL")}</a>` : ""}.</p>
  <h3 id="cookies">Cookies</h3><p>Le panier, ta zone de livraison et ton choix cookies sont gardés dans ton navigateur (stockage local). Ils sont nécessaires au fonctionnement du site et ne servent pas à te suivre.</p>
  ${ads}</div></div>${consentBox}`, { desc: "Mentions légales, données personnelles et cookies de FENUA MANGA." });
}

/* ===================== ROUTES T-SHIRTS ===================== */
const thtml = (s: string, status = 200) => new Response(s, { status, headers: { "Content-Type": "text/html; charset=utf-8" } });
const tjson = (o: any, status = 200) => new Response(JSON.stringify(o), { status, headers: { "Content-Type": "application/json" } });
async function tshirtRoute(req: Request, url: URL, p: string): Promise<Response | null> {
  if (p === "/tshirts" || p === "/t-shirts") return thtml(home());
  if (p === "/daytho") return new Response(null, { status: 302, headers: { Location: "/tshirts#daytho" } });
  if (p.startsWith("/tshirts/") && req.method === "GET" && !["/tshirts/merci", "/tshirts/admin"].includes(p)) {
    const prod = byId(p.slice(9)); return prod ? thtml(productPage(prod)) : null;
  }
  if (p === "/panier") return thtml(cartPage());
  if (p === "/partenaires" && req.method === "GET") return thtml(partnersPage());
  if (p === "/partenaires" && req.method === "POST") {
    const form = await req.formData().catch(() => null);
    const get = (k: string, max: number) => String(form?.get(k) ?? "").trim().slice(0, max);
    if (!form || get("site", 10)) return thtml(partnersPage(`<div class="notice">Māuruuru ! Ta demande est bien reçue.</div>`));
    const ip = req.headers.get("x-forwarded-for")?.split(",")[0].trim() || "?";
    const now = Date.now(), hits = (leadHits.get(ip) || []).filter((t) => now - t < 3600_000);
    if (hits.length >= 5) return thtml(partnersPage(`<div class="notice">Trop de demandes, réessaie plus tard.</div>`), 429);
    const lead = { at: new Date().toISOString(), nom: get("nom", 80), marque: get("marque", 120), contact: get("contact", 160), message: get("message", 1500) };
    if (!lead.nom || !lead.contact) return thtml(partnersPage(`<div class="notice">Indique ton nom et un moyen de te contacter.</div>`), 400);
    hits.push(now); leadHits.set(ip, hits);
    const f = Bun.file(LEADS);
    await Bun.write(LEADS, ((await f.exists()) ? await f.text() : "") + JSON.stringify(lead) + "\n", { createPath: true });
    log(`Nouvelle demande partenaire : ${lead.marque || lead.nom}`);
    return thtml(partnersPage(`<div class="notice">Māuruuru ${esc(lead.nom)} ! On te recontacte très vite.</div>`));
  }
  if (p === "/mentions-legales" || p === "/confidentialite" || p === "/cookies") return thtml(legalPage());
  if (p === "/livraison") return thtml(tlayout("Livraison — FENUA MANGA", `<div class="kata" style="margin-top:20px">配送</div><h2>Livraison & retours</h2>
  <p>Chaque t-shirt est imprimé à la commande par notre partenaire d'impression puis expédié directement chez toi. Fabrication : 2 à 5 jours ouvrés. Livraison : environ 3 à 8 jours en Europe, plus pour l'outre-mer et l'international.</p>
  <p>Frais de port par commande :</p><ul>${Object.values(ZONES).map((z) => `<li>${z.label} : ${teuro(z.cents)}</li>`).join("")}</ul>
  <p>Article abîmé ou erreur d'impression ? Écris-nous dans les 30 jours avec une photo : on le remplace gratuitement. Les articles étant faits sur mesure, les échanges de taille ne sont pas repris.</p>`));
  if (p.startsWith("/print/")) {
    const [, , design, file] = p.split("/");
    const w = Number(url.searchParams.get("w")) || undefined;
    const png = await renderPng(design, (file || "").replace(/\.png$/, ""), w ? Math.min(Math.max(w, 32), W) : undefined);
    if (!png) return new Response("Not found", { status: 404 });
    return new Response(png, { headers: { "Content-Type": "image/png", "Cache-Control": "public, max-age=86400" } });
  }
  if (p === "/tshirts/checkout" && req.method === "POST") {
    if (!env("STRIPE_SECRET_KEY")) return tjson({ error: "Paiement pas encore activé." }, 503);
    if (!env("PRINTFUL_API_KEY")) return tjson({ error: "Les t-shirts arrivent très bientôt !" }, 503);
    const body: any = await req.json().catch(() => ({}));
    const rawLines: Line[] = Array.isArray(body) ? body : Array.isArray(body?.cart) ? body.cart : [];
    const zone = ZONES[body?.zone] ? body.zone : "outremer";
    const lines = rawLines.filter((l) => { const pr = byId(l.product); return pr && pr.colors.some((x) => x.pf === l.color) && pr.sizes.includes(l.size) && l.qty > 0 && l.qty <= 20; });
    if (!lines.length) return tjson({ error: "Panier vide." }, 400);
    for (const l of lines) { const pr = byId(l.product)!; for (const side of pr.sides) if (!(await renderPng(designFor(pr, l.color), side, 64))) return tjson({ error: `${pr.name} arrive très bientôt !` }, 503); }
    const cart = lines.map((l) => `${l.product}|${l.color}|${l.size}|${Math.floor(l.qty)}`).join(";");
    if (cart.length > 490) return tjson({ error: "Panier trop grand, fais deux commandes." }, 400);
    const free = lines.every((l) => byId(l.product)!.hidden);
    const shipCents = free ? 0 : ZONES[zone].cents;
    const countries = free ? Object.values(ZONES).flatMap((z) => z.countries) : ZONES[zone].countries;
    try {
      const s = await tstripe("POST", "/checkout/sessions", {
        mode: "payment", locale: "fr", allow_promotion_codes: true, success_url: `${TPUB}/tshirts/merci?session_id={CHECKOUT_SESSION_ID}`, cancel_url: `${TPUB}/panier`,
        line_items: lines.map((l) => { const pr = byId(l.product)!; const col = pr.colors.find((x) => x.pf === l.color)!;
          return { quantity: Math.floor(l.qty), price_data: { currency: "eur", unit_amount: pr.price, product_data: { name: `${pr.name} — ${col.fr} ${l.size}`, images: [`${TPUB}/print/${designFor(pr, l.color)}/${pr.sides.includes("back") ? "back" : "front"}.png?w=600&v=${ASSET_V}`] } } }; }),
        shipping_address_collection: { allowed_countries: countries }, phone_number_collection: { enabled: true },
        shipping_options: [{ shipping_rate_data: { type: "fixed_amount", display_name: free ? "Livraison offerte" : `Livraison · ${ZONES[zone].label}`, fixed_amount: { amount: shipCents, currency: "eur" } } }],
        metadata: { cart, shop: "fenua-manga", zone },
      });
      return tjson({ url: s.url });
    } catch (e: any) { log(`Checkout : ${e.message}`); return tjson({ error: "Le paiement n'a pas pu démarrer, réessaie dans un instant." }, 500); }
  }
  if (p === "/tshirts/merci") {
    const id = url.searchParams.get("session_id") || "";
    if (id) { try { await fulfill(id); } catch (e: any) { log(`Merci/fulfill : ${e.message}`); } }
    return thtml(tlayout("Merci — FENUA MANGA", `<div class="hero"><h1>MĀURU<em>URU !</em></h1><p>Ta commande est enregistrée. Tu vas recevoir un reçu par e-mail, et ton t-shirt part à l'impression.</p><p><a href="/tshirts">Retour aux t-shirts</a></p></div>
    <div class="box" style="text-align:center"><h2>Lis l'histoire 📖</h2><p>TOA, Tome 1 : 44 pages de combat au fenua, en PDF à lire tout de suite.</p><a class="buy" style="display:inline-block;width:auto;padding:14px 28px;text-decoration:none" href="/#acheter">Lire TOA · ${TOA_P}</a></div>
    <script>try{localStorage.removeItem('fm_cart')}catch(e){};count()</script>`, { noindex: true }));
  }
  if (p === "/stripe/webhook" && req.method === "POST") {
    const raw = await req.text();
    const secret = env("STRIPE_WEBHOOK_SECRET");
    if (!secret || !verifyStripeSig(raw, req.headers.get("stripe-signature") || "", secret)) return new Response("bad signature", { status: 400 });
    const ev = JSON.parse(raw);
    if (["checkout.session.completed", "checkout.session.async_payment_succeeded"].includes(ev.type) && ev.data.object.metadata?.shop === "fenua-manga") {
      try { await fulfill(ev.data.object.id); } catch (e: any) { log(`Webhook/fulfill : ${e.message}`); return new Response("retry", { status: 500 }); }
    }
    return new Response("ok");
  }
  if (p === "/tshirts/admin") {
    if (!env("OWNER_KEY") || url.searchParams.get("key") !== env("OWNER_KEY")) return thtml("Accès refusé", 403);
    const check = async (name: string, fn: () => Promise<string>) => { try { return `<div class="row"><b>${name}</b><span>✅ ${await fn()}</span></div>`; } catch (e: any) { return `<div class="row"><b>${name}</b><span>❌ ${e.message}</span></div>`; } };
    const k = env("STRIPE_SECRET_KEY");
    const rows = [
      await check("Stripe", async () => { if (!k) throw new Error("clé manquante"); await tstripe("GET", "/balance"); return `connecté (${/_live_/.test(k) ? "LIVE" : "test"})`; }),
      await check("Webhook Stripe", async () => { if (!env("STRIPE_WEBHOOK_SECRET")) throw new Error("secret manquant"); return "configuré"; }),
      await check("Printful", async () => { if (!env("PRINTFUL_API_KEY")) throw new Error("clé manquante"); const cat = await catalog(TEE); return `connecté · Gildan 64000 · ${cat.variants.size} variantes`; }),
      await check("Mode commandes", async () => (env("PRINTFUL_AUTO_CONFIRM") === "true" ? "envoi automatique en production" : "brouillon (à confirmer dans Printful)")),
    ].join("");
    const ev = events.map((e) => `<div class="row"><span class="mute">${e.at.slice(0, 16).replace("T", " ")}</span><span>${esc(e.msg)}</span></div>`).join("") || `<p class="mute">Rien pour l'instant.</p>`;
    const tests = TPRODUCTS.filter((pr) => pr.hidden).map((pr) => `<a href="/tshirts/${pr.id}">${pr.name}</a>`).join(" · ");
    const leads = (await readLeads()).map((l: any) => `<div class="row"><span class="mute">${esc(String(l.at).slice(0, 16).replace("T", " "))}</span><span><b>${esc(l.marque || l.nom)}</b> · ${esc(l.nom)} · ${esc(l.contact)}<br>${esc(l.message || "")}</span></div>`).join("") || `<p class="mute">Aucune demande pour l'instant.</p>`;
    const legalMissing = ["LEGAL_NAME", "LEGAL_ADDRESS", "LEGAL_EMAIL"].filter((k) => !cfg(k));
    const money = `<div class="row"><b>Mentions légales</b><span>${legalMissing.length ? `⚠️ à compléter : ${legalMissing.join(", ")}` : "✅ complètes"}</span></div><div class="row"><b>Publicité AdSense</b><span>${ADSENSE ? `✅ active (${ADSENSE})` : "⏸️ inactive : ajouter ADSENSE_CLIENT"}</span></div><div class="row"><b>Encart partenaire</b><span>${cfg("SPONSOR_NAME") ? `✅ ${esc(cfg("SPONSOR_NAME"))}` : "⏸️ libre (« Ton commerce ici »)"}</span></div>`;
    return thtml(tlayout("Admin t-shirts — FENUA MANGA", `<h2>Admin t-shirts</h2>${rows}${money}<h3>Demandes partenaires</h3>${leads}<h3>T-shirts test (privés)</h3><p>${tests}</p><h3>Journal</h3>${ev}`, { noindex: true }));
  }
  return null;
}
ensureWebhook();
(async () => {
  if (!env("PRINTFUL_API_KEY")) return;
  try {
    await printfulStoreId();
    const cat = await catalog(TEE);
    const miss = TPRODUCTS.flatMap((p) => p.colors.flatMap((c) => p.sizes.filter((z) => !cat.variants.has(`${c.pf}|${z}`)).map((z) => `${p.id} ${c.pf} ${z}`)));
    log(`Printful OK (Gildan 64000) : ${cat.variants.size} variantes${miss.length ? `, MANQUANTES : ${miss.join(", ")}` : ", toutes les tailles/couleurs trouvées"}`);
  } catch (e: any) { log(`Printful ERREUR : ${e.message}`); }
})();
fonts().catch((e) => console.error("polices:", e.message));

importFiles().then(async () => {
  const missing: string[] = [];
  for (const n of CHECK) if (!(await Bun.file(destOf(n)).exists())) missing.push(n);
  console.log(`Fichiers manquants : ${missing.join(", ") || "aucun"}`);
});

Bun.serve({
  port: Number(Bun.env.PORT ?? 3000),
  maxRequestBodySize: 150 * 1024 * 1024,
  async fetch(req) {
    const url = new URL(req.url);
    const origin = Bun.env.PUBLIC_URL ?? `https://${req.headers.get("host")}`;
    const p = url.pathname;
    const tr = await tshirtRoute(req, url, p);
    if (tr) return tr;

    if (p === "/robots.txt") return new Response(`User-agent: *\nDisallow: /admin\nDisallow: /tshirts/admin\nDisallow: /panier\nDisallow: /merci\nDisallow: /telecharger\nSitemap: ${TPUB}/sitemap.xml\n`, { headers: { "Content-Type": "text/plain" } });
    if (p === "/sitemap.xml") {
      const urls = ["/", "/tshirts", "/liens", "/livraison", "/partenaires", "/mentions-legales", ...TPRODUCTS.filter((x) => !x.hidden).map((x) => `/tshirts/${x.id}`)];
      return new Response(`<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${urls.map((u) => `<url><loc>${TPUB}${u}</loc></url>`).join("")}</urlset>`, { headers: { "Content-Type": "application/xml" } });
    }
    if (p === "/ads.txt") return ADSENSE ? new Response(`google.com, ${ADSENSE.replace("ca-", "")}, DIRECT, f08c47fec0942fa0\n`, { headers: { "Content-Type": "text/plain" } }) : new Response("Not found", { status: 404 });
    if (p === "/favicon.ico") return Response.redirect(`${origin}/img/favicon.png`, 301);
    if (p === "/" || p === "/index.html") return html(INDEX);
    if (p === "/liens" || p === "/liens.html") return html(LIENS);
    if (p === "/soutenir" || p === "/soutien") return Response.redirect("https://ko-fi.com/loshing57323", 302);
    if (p === "/style.css") return new Response(CSS, { headers: { "Content-Type": "text/css; charset=utf-8", "Cache-Control": "public, max-age=600" } });

    if (p.startsWith("/img/")) {
      const name = p.slice(5);
      if (!ALLOWED.test(name) || name.endsWith(".pdf")) return new Response("Not found", { status: 404 });
      const f = Bun.file(`${DATA}/img/${name}`);
      if (await f.exists()) return new Response(f, { headers: { "Cache-Control": "public, max-age=3600" } });
      const gen = await brandImage(name);
      if (gen) return new Response(gen, { headers: { "Content-Type": "image/png", "Cache-Control": "public, max-age=3600" } });
      return new Response("Not found", { status: 404 });
    }

    if (p === "/admin") {
      if (!OWNER_KEY || url.searchParams.get("key") !== OWNER_KEY) return html(page("Accès refusé", "<h1>Accès refusé</h1>"), 403);
      let msg = "";
      if (req.method === "POST") {
        const form = await req.formData();
        const saved: string[] = [];
        for (const [field, v] of form.entries()) {
          if (typeof v === "string" || !(v as File).size) continue;
          let name = "";
          if (field.startsWith("slot:")) name = field.slice(5);
          else if (field === "f") name = (v as File).name;
          if (!ALLOWED.test(name)) continue;
          await Bun.write(destOf(name), v as Blob, { createPath: true });
          saved.push(name);
        }
        if (saved.some((n) => n.startsWith("tshirt-"))) { assetCache.clear(); pngCache.clear(); }
        msg = `<p><b>Enregistré :</b> ${saved.join(", ") || "aucun fichier reconnu"}</p>`;
      }
      const have: string[] = [];
      for (const n of CHECK) have.push(`${(await Bun.file(destOf(n)).exists()) ? "✅" : "❌"} ${n}`);
      return html(adminForm(msg, have.join("<br>")));
    }

    if (p === "/acheter" && req.method === "POST") {
      const form = await req.formData().catch(() => null);
      const id = String(form?.get("produit") ?? "mana-t1");
      const prod = PRODUCTS[id];
      if (!prod) return html(page("Introuvable", "<h1>Produit introuvable</h1>"), 404);
      if (!STRIPE_KEY) return html(page("Bientôt", "<h1>Paiement bientôt disponible</h1><p>Māuruuru !</p>"), 503);
      if (!(await Bun.file(prod.pdf).exists()))
        return html(page("Bientôt", `<h1>Bientôt disponible</h1><p>${prod.label} arrive très vite sur la boutique. Māuruuru !</p><a class="btn" href="/">Retour</a>`), 503);
      const body = new URLSearchParams({
        mode: "payment",
        "line_items[0][quantity]": "1",
        "line_items[0][price_data][currency]": "eur",
        "line_items[0][price_data][unit_amount]": String(prod.price),
        "line_items[0][price_data][product_data][name]": prod.name,
        "line_items[0][price_data][product_data][description]": prod.desc,
        "metadata[produit]": id,
        allow_promotion_codes: "true",
        locale: "fr",
        success_url: `${origin}/merci?session_id={CHECKOUT_SESSION_ID}`,
        cancel_url: `${origin}/#acheter`,
      });
      try { const s = await stripe("checkout/sessions", body); return Response.redirect(s.url, 303); }
      catch (e) { console.error("checkout", e); return html(page("Erreur", "<h1>Oups…</h1><p>Le paiement n'a pas pu démarrer. Réessaie dans un instant.</p>"), 502); }
    }

    if (p === "/merci") {
      const id = url.searchParams.get("session_id");
      const s = await paid(id);
      if (!s) return html(page("Paiement non confirmé", "<h1>Paiement non confirmé</h1><p>Si tu viens de payer, attends quelques secondes puis recharge la page.</p>"), 402);
      const prod = productOf(s);
      return html(page("Māuruuru !", `<h1>Māuruuru ! 🌺</h1><p>Ton paiement est confirmé. Voici ton manga :</p>
        <a class="btn" href="/telecharger?session_id=${encodeURIComponent(id!)}">⬇️ Télécharger ${prod.label}</a>
        <p class="small">Garde cette page : le lien reste valable pour retélécharger ton fichier.</p>
        <h2>Porte l'histoire 👕</h2><p>Manu et le logo FENUA MANGA, en t-shirt imprimé à la commande.</p>
        <a class="btn" href="/tshirts">Voir les t-shirts</a>`));
    }

    if (p === "/telecharger") {
      const s = await paid(url.searchParams.get("session_id"));
      if (!s) return html(page("Accès refusé", "<h1>Accès refusé</h1><p>Ce lien n'est pas associé à un paiement confirmé.</p>"), 403);
      const prod = productOf(s);
      const f = Bun.file(prod.pdf);
      if (!(await f.exists())) return html(page("Bientôt", "<h1>Fichier en préparation</h1><p>Écris-nous, on t'envoie ton manga très vite.</p>"), 503);
      return new Response(f, { headers: { "Content-Type": "application/pdf", "Content-Disposition": `attachment; filename="${prod.file}"` } });
    }

    return html(page("Introuvable", "<h1>Page introuvable</h1>"), 404);
  },
});
console.log("FENUA MANGA en ligne");
