const out = document.getElementById("out");
const tokenInput = document.getElementById("token");
let token = "";
const STATUSES = ["nouveau", "contacté", "devis envoyé", "client", "perdu"];
const xpf = n => `${Math.round(n || 0).toLocaleString("fr-FR")} XPF`;
const PLANS = ["Démarrage", "Pro", "Sur mesure"];
const planFromSubject = s => (s === "Forfait Pro" ? "Pro" : s === "Forfait Sur mesure" ? "Sur mesure" : "Démarrage");
let lastLink = null; // { clientId, url } : lien affiché juste après sa création
try { token = sessionStorage.getItem("pono-admin") || ""; } catch {}

function el(tag, props = {}, children = []) {
  const node = Object.assign(document.createElement(tag), props);
  for (const child of [].concat(children)) node.append(child);
  return node;
}

async function api(path, options = {}) {
  const response = await fetch(path, { ...options, headers: { "x-admin-token": token, ...(options.headers || {}) } });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `Erreur ${response.status}`);
  return data;
}

async function load() {
  out.replaceChildren(el("p", { className: "empty", textContent: "Chargement…" }));
  try {
    const [{ leads, storage }, { clients }] = await Promise.all([api("/api/admin/leads"), api("/api/admin/clients")]);
    try { sessionStorage.setItem("pono-admin", token); } catch {}
    const blocks = [];
    if (storage !== "persistent") blocks.push(el("div", { className: "warn", textContent: "⚠ Stockage non persistant : ces demandes seront effacées au prochain déploiement. Ajoutez un volume Railway." }));
    const download = el("button", { className: "button button-quiet", type: "button", textContent: "Télécharger (CSV)" });
    download.addEventListener("click", () => exportCsv(leads));
    const refresh = el("button", { className: "button button-quiet", type: "button", textContent: "Actualiser" });
    refresh.addEventListener("click", load);
    blocks.push(summary(leads));
    blocks.push(clientSection(clients));
    blocks.push(el("h2", { className: "section-title", textContent: "Demandes de contact" }));
    blocks.push(el("div", { className: "bar" }, [el("span", { textContent: `${leads.length} demande${leads.length > 1 ? "s" : ""}` }), refresh, download]));
    if (!leads.length) blocks.push(el("p", { className: "empty", textContent: "Aucune demande pour le moment." }));
    for (const lead of leads) blocks.push(card(lead, clients));
    out.replaceChildren(...blocks);
  } catch (error) {
    out.replaceChildren(el("p", { className: "err", textContent: error.message }));
  }
}

// Tableau de bord : ce qui est signé, ce qui est en négociation, ce qui attend une réponse
function summary(leads) {
  const by = s => leads.filter(l => (l.status || "nouveau") === s);
  const sum = list => list.reduce((t, l) => t + (Number(l.amount) || 0), 0);
  const tile = (label, value, hint) => el("div", { className: "tile" }, [el("small", { textContent: label }), el("strong", { textContent: value }), el("span", { textContent: hint })]);
  const clients = by("client"), quotes = by("devis envoyé"), fresh = by("nouveau");
  return el("div", { className: "tiles" }, [
    tile("Signé", xpf(sum(clients)), `${clients.length} client${clients.length > 1 ? "s" : ""}`),
    tile("En négociation", xpf(sum(quotes)), `${quotes.length} devis envoyé${quotes.length > 1 ? "s" : ""}`),
    tile("À rappeler", String(fresh.length), fresh.length ? "Répondez vite : c'est là que se gagnent les clients" : "Tout est traité")
  ]);
}

async function save(lead, patch) {
  try { await api(`/api/admin/leads/${encodeURIComponent(lead.id)}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(patch) }); load(); }
  catch (error) { alert(error.message); }
}

// ---------- Espaces clients ----------
async function createClient(data) {
  try {
    const { client, link } = await api("/api/admin/clients", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) });
    lastLink = { clientId: client.id, url: location.origin + link };
    await load();
    document.getElementById(`client-${client.id}`)?.scrollIntoView({ block: "center" });
  } catch (error) { alert(error.message); }
}

function linkBox(url) {
  const input = el("input", { value: url, readOnly: true, ariaLabel: "Lien de l'espace client" });
  const status = el("span", { className: "meta" });
  const copyBtn = el("button", { type: "button", textContent: "Copier le lien" });
  copyBtn.addEventListener("click", async () => {
    try { await navigator.clipboard.writeText(url); status.textContent = "Copié."; }
    catch { input.select(); status.textContent = "Lien sélectionné : copiez-le."; }
  });
  return el("div", { className: "link-box" }, [
    el("small", { textContent: "Envoyez ce lien à votre client. Il ne sera plus affiché ensuite : en cas de perte, créez-en un nouveau." }),
    el("div", { className: "link-row" }, [input, copyBtn]), status
  ]);
}

function clientSection(clients) {
  const name = el("input", { placeholder: "Nom du client", required: true, maxLength: 100, ariaLabel: "Nom du client" });
  const email = el("input", { type: "email", placeholder: "E-mail (facultatif)", maxLength: 180, ariaLabel: "E-mail du client" });
  const plan = el("select", { ariaLabel: "Forfait" }, PLANS.map(p => el("option", { value: p, textContent: p })));
  const form = el("form", { className: "client-form" }, [name, email, plan, el("button", { className: "button button-primary", type: "submit", textContent: "Créer l'espace" })]);
  form.addEventListener("submit", e => { e.preventDefault(); createClient({ name: name.value, email: email.value, plan: plan.value }); });
  const list = clients.length ? clients.map(clientCard) : [el("p", { className: "empty", textContent: "Aucun espace client. Créez-en un après chaque paiement Pro ou Sur mesure." })];
  return el("section", { className: "clients" }, [
    el("h2", { className: "section-title", textContent: `Espaces clients (${clients.length})` }), form, ...list
  ]);
}

function clientCard(c) {
  const plan = el("select", { ariaLabel: "Forfait" }, PLANS.map(p => el("option", { value: p, textContent: p, selected: c.plan === p })));
  plan.addEventListener("change", () => patchClient(c, { plan: plan.value }));
  const toggle = el("button", { type: "button", textContent: c.active ? "Suspendre" : "Réactiver" });
  toggle.addEventListener("click", () => patchClient(c, { active: !c.active }));
  const relink = el("button", { type: "button", textContent: "Nouveau lien" });
  relink.addEventListener("click", async () => {
    if (!confirm(`Créer un nouveau lien pour ${c.name} ? L'ancien lien cessera de fonctionner.`)) return;
    try { const { link } = await api(`/api/admin/clients/${encodeURIComponent(c.id)}/link`, { method: "POST" }); lastLink = { clientId: c.id, url: location.origin + link }; load(); }
    catch (error) { alert(error.message); }
  });
  const del = el("button", { type: "button", className: "danger", textContent: "Supprimer" });
  del.addEventListener("click", async () => {
    if (!confirm(`Supprimer définitivement l'espace de ${c.name} et toutes ses données ?`)) return;
    try { await api(`/api/admin/clients/${encodeURIComponent(c.id)}`, { method: "DELETE" }); load(); }
    catch (error) { alert(error.message); }
  });
  const seen = c.lastSeenAt ? `vu le ${new Date(c.lastSeenAt).toLocaleDateString("fr-FR")}` : "jamais ouvert";
  return el("article", { className: `lead client${c.active ? "" : " inactive"}`, id: `client-${c.id}` }, [
    el("header", {}, [el("strong", { textContent: c.business || c.name }), el("span", { className: "tag", textContent: c.plan }), el("span", { className: "meta", textContent: `${c.requests} demande${c.requests > 1 ? "s" : ""} · ${seen}${c.active ? "" : " · suspendu"}` })]),
    lastLink?.clientId === c.id ? linkBox(lastLink.url) : null,
    el("div", { className: "deal" }, [plan]),
    el("div", { className: "actions" }, [relink, toggle, del])
  ].filter(Boolean));
}

async function patchClient(c, patch) {
  try { await api(`/api/admin/clients/${encodeURIComponent(c.id)}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(patch) }); load(); }
  catch (error) { alert(error.message); }
}

function card(lead, clients = []) {
  const date = new Date(lead.createdAt).toLocaleString("fr-FR", { dateStyle: "medium", timeStyle: "short" });
  const reply = el("a", { href: `mailto:${encodeURIComponent(lead.email)}?subject=${encodeURIComponent("Votre demande PONO")}`, textContent: "Répondre par e-mail" });
  const actions = [reply];
  if (lead.phone) actions.push(el("a", { href: `tel:${lead.phone.replace(/[^\d+]/g, "")}`, textContent: `Appeler ${lead.phone}` }));
  const del = el("button", { type: "button", className: "danger", textContent: "Supprimer" });
  del.addEventListener("click", async () => {
    if (!confirm(`Supprimer définitivement la demande de ${lead.name} ?`)) return;
    try { await api(`/api/admin/leads/${encodeURIComponent(lead.id)}`, { method: "DELETE" }); load(); }
    catch (error) { alert(error.message); }
  });
  actions.push(del);
  const existing = clients.find(c => c.leadId === lead.id);
  if (!existing) {
    const space = el("button", { type: "button", className: "create", textContent: "Créer son espace client" });
    space.addEventListener("click", () => createClient({ name: lead.name, email: lead.email, phone: lead.phone, plan: planFromSubject(lead.subject), leadId: lead.id }));
    actions.unshift(space);
  } else actions.unshift(el("span", { className: "meta", textContent: `✓ Espace client créé (${existing.plan})` }));
  const status = el("select", { ariaLabel: "Statut" }, STATUSES.map(s => el("option", { value: s, textContent: s, selected: (lead.status || "nouveau") === s })));
  status.addEventListener("change", () => save(lead, { status: status.value }));
  const amount = el("input", { type: "number", min: 0, step: 100, value: lead.amount || "", placeholder: "Montant XPF", ariaLabel: "Montant en XPF" });
  amount.addEventListener("change", () => save(lead, { amount: Number(amount.value) || 0 }));
  return el("article", { className: `lead status-${(lead.status || "nouveau").replace(/\s/g, "-")}` }, [
    el("header", {}, [el("strong", { textContent: lead.name }), el("span", { className: "tag", textContent: `${lead.subject || "—"}${lead.lang === "en" ? " · 🌍 EN" : ""}` }), el("span", { className: "meta", textContent: `${date} · ${lead.email}` })]),
    el("p", { textContent: lead.message }),
    el("div", { className: "deal" }, [status, amount]),
    el("div", { className: "actions" }, actions)
  ]);
}

function exportCsv(leads) {
  const cols = ["createdAt", "name", "email", "phone", "subject", "status", "amount", "message"];
  const cell = v => `"${String(v ?? "").replace(/"/g, '""')}"`;
  const csv = "﻿" + [cols.join(";"), ...leads.map(l => cols.map(c => cell(l[c])).join(";"))].join("\n");
  const link = el("a", { href: URL.createObjectURL(new Blob([csv], { type: "text/csv" })), download: `pono-demandes-${new Date().toISOString().slice(0, 10)}.csv` });
  link.click();
  URL.revokeObjectURL(link.href);
}

document.getElementById("login").addEventListener("submit", (event) => {
  event.preventDefault();
  token = tokenInput.value.trim();
  load();
});
if (token) load();
