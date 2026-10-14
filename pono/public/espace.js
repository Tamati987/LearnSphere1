// Espace client PONO : le jeton secret est la dernière partie de l'adresse (/espace/<jeton>)
const token = decodeURIComponent(location.pathname.split("/")[2] || "");
const $ = (s) => document.querySelector(s);
const DONE = ["gagné", "perdu"];
const STATUSES = ["nouveau", "en cours", "répondu", "gagné", "perdu"];
let me = null;
let filter = "open";
const chatHistory = [];

function el(tag, props = {}, children = []) {
  const node = Object.assign(document.createElement(tag), props);
  for (const child of [].concat(children)) if (child != null) node.append(child);
  return node;
}

async function api(path, options = {}) {
  const response = await fetch(path, { ...options, headers: { "x-client-token": token, "Content-Type": "application/json", ...(options.headers || {}) } });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `Erreur ${response.status}`);
  return data;
}
const send = (path, method, body) => api(path, { method, body: JSON.stringify(body || {}) });

const today = () => new Date().toISOString().slice(0, 10);
const isDue = (r) => me.features.followUps && r.followUp && r.followUp <= today() && !DONE.includes(r.status);

async function copy(textValue, statusEl) {
  try { await navigator.clipboard.writeText(textValue); statusEl.textContent = "Copié."; }
  catch { statusEl.textContent = "Sélectionnez le texte puis copiez-le."; }
}

// ---------- Onglets ----------
function showTab() {
  const tab = (location.hash || "#assistant").slice(1);
  const known = ["assistant", "reponse", "demandes", "modeles", "infos"];
  const current = known.includes(tab) ? tab : "assistant";
  document.querySelectorAll(".panel").forEach(p => { p.hidden = p.dataset.tab !== current; });
  document.querySelectorAll(".tabs-nav a").forEach(a => a.setAttribute("aria-current", a.getAttribute("href") === `#${current}` ? "page" : "false"));
}
window.addEventListener("hashchange", showTab);

// ---------- Affichage ----------
function renderHeader() {
  $("#bizName").textContent = me.profile.business || me.name;
  $("#planName").textContent = me.plan;
  $("#aiMode").textContent = me.ai === "configured" ? "IA activée" : "Mode démo";
  $("#aiMode").title = me.ai === "configured" ? "" : "Réponses simples basées sur vos informations. Avec l'IA, elles sont rédigées sur mesure.";
  const open = me.requests.filter(r => !DONE.includes(r.status)).length;
  $("#statOpen").textContent = open;
  $("#statDue").textContent = me.features.followUps ? me.requests.filter(isDue).length : "—";
  $("#statWon").textContent = me.requests.filter(r => r.status === "gagné").length;
  const p = me.profile;
  $("#profileHint").hidden = Boolean(p.services || p.prices || p.hours || p.faq);
  document.querySelectorAll(".pro-only").forEach(n => { n.hidden = !me.features.followUps; });
  $("#draftSave").hidden = !me.features.templates;
}

function renderRequests() {
  const list = $("#reqList");
  const due = me.requests.filter(isDue);
  const box = $("#dueBox");
  box.hidden = !due.length;
  box.textContent = due.length ? `⏰ ${due.length} relance${due.length > 1 ? "s" : ""} à faire aujourd'hui : ${due.map(r => r.customer).join(", ")}.` : "";
  const rows = me.requests
    .filter(r => filter === "all" || (filter === "done" ? DONE.includes(r.status) : !DONE.includes(r.status)))
    .sort((a, b) => Number(isDue(b)) - Number(isDue(a)));
  if (!rows.length) {
    list.replaceChildren(el("p", { className: "empty-state", textContent: filter === "done" ? "Aucune demande terminée." : "Aucune demande pour le moment. Ajoutez la première avec le formulaire ci-dessus." }));
    return;
  }
  list.replaceChildren(...rows.map(requestCard));
}

function requestCard(r) {
  const date = new Date(r.createdAt).toLocaleDateString("fr-FR", { day: "numeric", month: "short" });
  const status = el("select", { ariaLabel: "Statut" }, STATUSES.map(s => el("option", { value: s, textContent: s, selected: r.status === s })));
  status.addEventListener("change", () => updateRequest(r, { status: status.value }));
  const controls = [status];
  if (me.features.followUps) {
    const follow = el("input", { type: "date", value: r.followUp || "", ariaLabel: "Date de relance" });
    follow.addEventListener("change", () => updateRequest(r, { followUp: follow.value }));
    controls.push(el("span", { textContent: "Relancer le", className: "req-label" }), follow);
  }
  const del = el("button", { type: "button", className: "mini danger", textContent: "Supprimer" });
  del.addEventListener("click", async () => {
    if (!confirm(`Supprimer la demande de ${r.customer} ?`)) return;
    try { await api(`/api/client/requests/${encodeURIComponent(r.id)}`, { method: "DELETE" }); me.requests = me.requests.filter(x => x.id !== r.id); refresh(); }
    catch (e) { alert(e.message); }
  });
  controls.push(del);
  const head = [el("strong", { textContent: r.customer }), el("small", { textContent: [date, r.contact].filter(Boolean).join(" · ") })];
  if (isDue(r)) head.push(el("span", { className: "due-tag", textContent: "À relancer" }));
  return el("article", { className: `req st-${r.status.replace(/\s/g, "-")}${isDue(r) ? " is-due" : ""}` }, [
    el("div", { className: "req-head" }, head),
    r.subject ? el("p", { className: "req-subject", textContent: r.subject }) : null,
    el("div", { className: "req-controls" }, controls)
  ]);
}

async function updateRequest(r, patch) {
  try {
    const { request } = await send(`/api/client/requests/${encodeURIComponent(r.id)}`, "PATCH", patch);
    me.requests = me.requests.map(x => (x.id === r.id ? request : x));
    refresh();
  } catch (e) { alert(e.message); }
}

function renderTemplates() {
  $("#tplLocked").hidden = me.features.templates;
  $("#tplArea").hidden = !me.features.templates;
  if (!me.features.templates) return;
  const list = $("#tplList");
  if (!me.templates.length) { list.replaceChildren(el("p", { className: "empty-state", textContent: "Aucun modèle pour le moment." })); return; }
  list.replaceChildren(...me.templates.map(t => {
    const status = el("span", { className: "status-line", role: "status" });
    const copyBtn = el("button", { type: "button", className: "mini", textContent: "Copier" });
    copyBtn.addEventListener("click", () => copy(t.body, status));
    const del = el("button", { type: "button", className: "mini danger", textContent: "Supprimer" });
    del.addEventListener("click", async () => {
      if (!confirm(`Supprimer le modèle « ${t.title} » ?`)) return;
      try { await api(`/api/client/templates/${encodeURIComponent(t.id)}`, { method: "DELETE" }); me.templates = me.templates.filter(x => x.id !== t.id); renderTemplates(); }
      catch (e) { alert(e.message); }
    });
    return el("article", { className: "tpl" }, [el("strong", { textContent: t.title }), el("p", { textContent: t.body }), el("div", { className: "row-actions" }, [copyBtn, del, status])]);
  }));
}

function fillProfile() {
  const form = $("#profileForm");
  for (const [k, v] of Object.entries(me.profile)) if (form.elements[k]) form.elements[k].value = v || "";
}

function refresh() { renderHeader(); renderRequests(); renderTemplates(); }

// ---------- Assistant ----------
function chatLine(role, content) {
  const node = el("div", { className: `message ${role === "user" ? "user-message" : "assistant-message"}`, textContent: content });
  $("#chatLog").append(node);
  $("#chatLog").scrollTop = $("#chatLog").scrollHeight;
  return node;
}
$("#chatForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const input = $("#chatText"), message = input.value.trim();
  if (!message) return;
  input.value = "";
  chatLine("user", message);
  const pending = chatLine("assistant", "…");
  try {
    const { reply } = await send("/api/client/chat", "POST", { message, history: chatHistory.slice(-6) });
    pending.textContent = reply;
    chatHistory.push({ role: "user", content: message }, { role: "assistant", content: reply });
  } catch (err) { pending.textContent = err.message; }
});

// ---------- Préparer une réponse ----------
$("#draftForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const button = e.submitter || $("#draftForm button");
  button.disabled = true;
  $("#draftStatus").textContent = "";
  try {
    const { reply } = await send("/api/client/draft", "POST", { message: $("#draftMessage").value, customer: $("#draftCustomer").value });
    $("#draftText").value = reply;
    $("#draftOut").hidden = false;
  } catch (err) { $("#draftStatus").textContent = err.message; $("#draftOut").hidden = false; }
  finally { button.disabled = false; }
});
$("#draftCopy").addEventListener("click", () => copy($("#draftText").value, $("#draftStatus")));
$("#draftSave").addEventListener("click", async () => {
  const body = $("#draftText").value.trim();
  if (!body) return;
  try {
    const { template } = await send("/api/client/templates", "POST", { title: `Réponse${$("#draftCustomer").value ? ` à ${$("#draftCustomer").value}` : ""} (${new Date().toLocaleDateString("fr-FR")})`, body });
    me.templates.push(template); renderTemplates();
    $("#draftStatus").textContent = "Enregistré dans vos modèles.";
  } catch (err) { $("#draftStatus").textContent = err.message; }
});

// ---------- Demandes ----------
$("#reqForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const f = e.target;
  try {
    const { request } = await send("/api/client/requests", "POST", { customer: f.customer.value, contact: f.contact.value, subject: f.subject.value, followUp: f.followUp.value });
    me.requests.unshift(request);
    f.reset();
    filter = "open"; syncFilters(); refresh();
  } catch (err) { alert(err.message); }
});
function syncFilters() { document.querySelectorAll(".filters button").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.filter === filter))); }
document.querySelectorAll(".filters button").forEach(b => b.addEventListener("click", () => { filter = b.dataset.filter; syncFilters(); renderRequests(); }));

// ---------- Modèles ----------
$("#tplForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const f = e.target;
  try {
    const { template } = await send("/api/client/templates", "POST", { title: f.title.value, body: f.body.value });
    me.templates.push(template); f.reset(); renderTemplates();
  } catch (err) { alert(err.message); }
});

// ---------- Mes informations ----------
$("#profileForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const f = e.target, status = $("#profileStatus");
  const body = Object.fromEntries(["business", "services", "prices", "hours", "address", "tone", "faq"].map(k => [k, f.elements[k].value]));
  try {
    const { profile } = await send("/api/client/profile", "PUT", body);
    me.profile = profile; renderHeader();
    status.className = "status-line"; status.textContent = "Enregistré. Votre assistant utilise déjà ces informations.";
  } catch (err) { status.className = "status-line error"; status.textContent = err.message; }
});

// ---------- Démarrage ----------
(async function start() {
  try {
    me = await api("/api/client/me");
    $("#state").hidden = true;
    $("#app").hidden = false;
    chatLine("assistant", `Ia ora na ! Je suis l'assistant de ${me.profile.business || me.name}. Posez-moi une question comme le ferait un client.`);
    fillProfile();
    refresh();
    showTab();
  } catch (err) {
    $("#state").className = "space-state error";
    $("#state").textContent = err.message;
  }
})();
