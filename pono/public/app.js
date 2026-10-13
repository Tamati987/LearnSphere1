const $ = (selector) => document.querySelector(selector);

const year = $("#year");
if (year) year.textContent = String(new Date().getFullYear());

// Lit la réponse JSON du serveur sans planter si c'est une page d'erreur HTML (ex. 502 Railway)
async function readJson(response) {
  return response.json().catch(() => ({}));
}

// ---------- Menu mobile ----------
const menuToggle = $("#menuToggle");
const mainNav = $("#mainNav");
menuToggle?.addEventListener("click", () => {
  const open = mainNav.classList.toggle("open");
  menuToggle.setAttribute("aria-expanded", String(open));
});
mainNav?.querySelectorAll("a").forEach(link => link.addEventListener("click", () => {
  mainNav.classList.remove("open");
  menuToggle?.setAttribute("aria-expanded", "false");
}));

// ---------- Formulaire de contact ----------
const contactForm = $("#contactForm");
const formStatus = $("#formStatus");
contactForm?.addEventListener("submit", async (event) => {
  event.preventDefault();
  formStatus.textContent = "Envoi en cours…";
  formStatus.className = "form-status";
  const form = new FormData(contactForm);
  const payload = {
    name: form.get("name"),
    email: form.get("email"),
    phone: form.get("phone"),
    subject: form.get("subject"),
    message: form.get("message"),
    consent: form.get("consent") === "on",
    website: form.get("website")
  };
  const button = contactForm.querySelector('button[type="submit"]');
  button.disabled = true;
  try {
    const response = await fetch("/api/contact", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await readJson(response);
    if (!response.ok) throw new Error(data.error || "L'envoi a échoué. Réessayez dans un instant.");
    formStatus.textContent = data.message || "Merci ! Votre demande a bien été enregistrée.";
    formStatus.className = "form-status success";
    contactForm.reset();
  } catch (error) {
    formStatus.textContent = error instanceof TypeError
      ? "Connexion impossible. Vérifiez votre réseau et réessayez."
      : error.message;
    formStatus.className = "form-status error";
  } finally {
    button.disabled = false;
  }
});

// ---------- Assistant ----------
const chatLauncher = $("#chatLauncher");
const chatPanel = $("#chatPanel");
const chatClose = $("#chatClose");
const chatMessages = $("#chatMessages");
const chatForm = $("#chatForm");
const chatInput = $("#chatInput");
const chatHistory = [];

function toggleChat(open) {
  chatPanel.hidden = !open;
  chatLauncher.setAttribute("aria-expanded", String(open));
  if (open) chatInput.focus();
  else chatLauncher.focus();
}
chatLauncher?.addEventListener("click", () => toggleChat(chatPanel.hidden));
chatClose?.addEventListener("click", () => toggleChat(false));
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && chatPanel && !chatPanel.hidden) toggleChat(false);
});

function appendMessage(role, content) {
  const element = document.createElement("div");
  element.className = `message ${role === "user" ? "user-message" : "assistant-message"}`;
  element.textContent = content;
  chatMessages.appendChild(element);
  chatMessages.scrollTop = chatMessages.scrollHeight;
  return element;
}

chatForm?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const message = chatInput.value.trim();
  if (!message) return;
  chatInput.value = "";
  appendMessage("user", message);
  const previousHistory = chatHistory.slice(-6);
  chatHistory.push({ role: "user", content: message });
  const pending = appendMessage("assistant", "Je réfléchis…");
  const submit = chatForm.querySelector("button");
  submit.disabled = true;
  try {
    const response = await fetch("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message, history: previousHistory })
    });
    const data = await readJson(response);
    if (!response.ok) throw new Error(data.error || "L'assistant est temporairement indisponible.");
    pending.textContent = data.reply;
    chatHistory.push({ role: "assistant", content: data.reply });
  } catch (error) {
    pending.textContent = error instanceof TypeError
      ? "Impossible de contacter l'assistant. Vérifiez votre connexion."
      : error.message;
    chatHistory.pop();
  } finally {
    submit.disabled = false;
    chatInput.focus();
  }
});
