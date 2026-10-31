/* Authentication stays in memory; CPF and JWT are never placed in URLs or browser storage. */
(() => {
  "use strict";
  const form = document.getElementById("cpf-form");
  const login = document.getElementById("login-card");
  const button = document.getElementById("login-button");
  const cpf = document.getElementById("cpf");
  const message = document.getElementById("message");
  const content = document.getElementById("budget-content");
  const controls = document.getElementById("session-controls");
  const route = window.location.pathname.match(/^(.*)\/service-orders\/([0-9a-f-]{36})\/budget\/decision$/i);
  let token = "";
  let busy = false;
  let generation = 0;

  function clear() {
    generation += 1;
    token = "";
    busy = false;
    button.disabled = !route;
    cpf.value = "";
    content.replaceChildren();
    content.hidden = true;
    controls.hidden = true;
    login.hidden = false;
    message.textContent = "";
  }

  function render(html) {
    const card = new DOMParser().parseFromString(html, "text/html").querySelector(".card");
    if (!card) throw new Error("Não foi possível exibir o orçamento. Tente novamente.");
    content.replaceChildren(document.importNode(card, true));
    for (const action of ["approve", "reject"]) {
      const decisionForm = content.querySelector(`form[action$="/${action}"]`);
      if (decisionForm) decisionForm.addEventListener("submit", event => {
        event.preventDefault();
        decide(action);
      });
    }
    login.hidden = true;
    content.hidden = false;
    controls.hidden = false;
  }

  async function request(path, options = {}, authenticated = true) {
    const headers = { ...options.headers };
    if (authenticated) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(path, { ...options, headers, cache: "no-store", credentials: "omit", redirect: "error" });
    if (response.status === 401 || response.status === 403) {
      const error = new Error(response.status === 403
        ? "Este CPF não possui acesso a este orçamento."
        : "Identificação inválida ou expirada. Informe seu CPF novamente.");
      error.reauthenticate = true;
      throw error;
    }
    if (!response.ok && response.status !== 409) {
      throw new Error(response.status === 400 ? "Confira o CPF informado." : "Não foi possível concluir a consulta. Tente novamente.");
    }
    return response;
  }

  async function decide(action) {
    if (busy || !token) return;
    busy = true;
    const attempt = generation;
    message.textContent = "";
    content.querySelectorAll("button").forEach(element => { element.disabled = true; });
    try {
      const response = await request(`${route[1]}/service-orders/${route[2]}/budget/${action}`, { method: "POST" });
      const html = await response.text();
      if (attempt !== generation) return;
      render(html);
    } catch (error) {
      if (attempt !== generation) return;
      if (error.reauthenticate) clear();
      message.textContent = error instanceof TypeError ? "Falha de conexão. Tente novamente." : error.message;
    } finally {
      if (attempt === generation) {
        busy = false;
        content.querySelectorAll("button").forEach(element => { element.disabled = false; });
      }
    }
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (busy || !route) return;
    busy = true;
    const attempt = ++generation;
    button.disabled = true;
    token = "";
    message.textContent = "";
    try {
      const response = await request(`${route[1]}/auth/customer`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ cpf: cpf.value })
      }, false);
      const authentication = await response.json();
      if (attempt !== generation) return;
      if (typeof authentication.accessToken !== "string" || !authentication.accessToken) {
        throw new Error("Não foi possível confirmar a identificação.");
      }
      token = authentication.accessToken;
      cpf.value = "";
      const highlight = new URLSearchParams(window.location.search).get("decision");
      const query = highlight === "approve" || highlight === "reject" ? `?decision=${highlight}` : "";
      const budget = await request(`${route[1]}/service-orders/${route[2]}/budget/decision/view${query}`);
      const html = await budget.text();
      if (attempt !== generation) return;
      render(html);
    } catch (error) {
      if (attempt !== generation) return;
      clear();
      message.textContent = error instanceof TypeError ? "Falha de conexão. Tente novamente." : error.message;
    } finally {
      if (attempt === generation) {
        busy = false;
        button.disabled = false;
      }
    }
  });
  document.getElementById("logout-button").addEventListener("click", clear);
  window.addEventListener("pagehide", clear);
  if (!route) { button.disabled = true; message.textContent = "Link de orçamento inválido."; }
  else { button.disabled = false; }
})();
