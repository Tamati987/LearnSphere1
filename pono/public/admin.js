const out = document.getElementById("out");
const tokenInput = document.getElementById("token");
let token = "";
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
    const { leads, storage } = await api("/api/admin/leads");
    try { sessionStorage.setItem("pono-admin", token); } catch {}
    const blocks = [];
    if (storage !== "persistent") blocks.push(el("div", { className: "warn", textContent: "⚠ Stockage non persistant : ces demandes seront effacées au prochain déploiement. Ajoutez un volume Railway." }));
    const download = el("button", { className: "button button-quiet", type: "button", textContent: "Télécharger (CSV)" });
    download.addEventListener("click", () => exportCsv(leads));
    const refresh = el("button", { className: "button button-quiet", type: "button", textContent: "Actualiser" });
    refresh.addEventListener("click", load);
    blocks.push(el("div", { className: "bar" }, [el("span", { textContent: `${leads.length} demande${leads.length > 1 ? "s" : ""}` }), refresh, download]));
    if (!leads.length) blocks.push(el("p", { className: "empty", textContent: "Aucune demande pour le moment." }));
    for (const lead of leads) blocks.push(card(lead));
    out.replaceChildren(...blocks);
  } catch (error) {
    out.replaceChildren(el("p", { className: "err", textContent: error.message }));
  }
}

function card(lead) {
  const date = new Date(lead.createdAt).toLocaleString("fr-FR", { dateStyle: "medium", timeStyle: "short" });
  const reply = el("a", { href: `mailto:${encodeURIComponent(lead.email)}?subject=${encodeURIComponent("Votre demande PONO")}`, textContent: "Répondre par e-mail" });
  const actions = [reply];
  if (lead.phone) actions.push(el("a", { href: `tel:${lead.phone.replace(/[^\d+]/g, "")}`, textContent: `Appeler ${lead.phone}` }));
  const del = el("button", { type: "button", textContent: "Supprimer" });
  del.addEventListener("click", async () => {
    if (!confirm(`Supprimer définitivement la demande de ${lead.name} ?`)) return;
    try { await api(`/api/admin/leads/${encodeURIComponent(lead.id)}`, { method: "DELETE" }); load(); }
    catch (error) { alert(error.message); }
  });
  actions.push(del);
  return el("article", { className: "lead" }, [
    el("header", {}, [el("strong", { textContent: lead.name }), el("span", { className: "tag", textContent: lead.subject || "—" }), el("span", { className: "meta", textContent: `${date} · ${lead.email}` })]),
    el("p", { textContent: lead.message }),
    el("div", { className: "actions" }, actions)
  ]);
}

function exportCsv(leads) {
  const cols = ["createdAt", "name", "email", "phone", "subject", "message"];
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
