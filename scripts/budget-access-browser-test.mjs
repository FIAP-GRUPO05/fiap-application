// Local browser regression: only the CPF issuer is mocked; budget calls use the real Spring server.
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { readFile, writeFile, mkdir, mkdtemp, access } from "node:fs/promises";
import { resolve, join } from "node:path";
import { once } from "node:events";

const fixture = JSON.parse(await readFile(process.argv[3], "utf8"));
const backend = `http://127.0.0.1:${Number(process.argv[2])}`;
let decisions = 0;
const gateway = createServer(async (req, res) => {
  try {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const body = Buffer.concat(chunks);
    if (req.url === "/auth/customer" && req.method === "POST") {
      const cpf = JSON.parse(body).cpf;
      const token = cpf === fixture.ownerCpf ? fixture.ownerToken : cpf === fixture.foreignCpf ? fixture.foreignToken : null;
      res.writeHead(token ? 200 : 401, { "Content-Type": "application/json", "Cache-Control": "no-store" });
      res.end(JSON.stringify(token ? { accessToken: token } : { message: "Invalid credentials" }));
      return;
    }
    assert(req.url.startsWith("/") && !req.url.startsWith("//"));
    if (req.method === "POST" && /\/budget\/(approve|reject)$/.test(req.url)) {
      assert.equal(req.headers.authorization, `Bearer ${fixture.ownerToken}`, "Decisions require the owner JWT");
      decisions += 1;
    }
    const headers = {};
    for (const name of ["authorization", "content-type", "accept"]) if (req.headers[name]) headers[name] = req.headers[name];
    const upstream = await fetch(backend + req.url, { method: req.method, headers,
      body: ["GET", "HEAD"].includes(req.method) ? undefined : body, redirect: "manual" });
    res.writeHead(upstream.status, Object.fromEntries([...upstream.headers].filter(([name]) =>
      !["content-length", "content-encoding", "connection", "transfer-encoding"].includes(name))));
    res.end(Buffer.from(await upstream.arrayBuffer()));
  } catch {
    res.writeHead(502); res.end("Local test gateway failed");
  }
});
await new Promise(resolve => gateway.listen(0, "127.0.0.1", resolve));
const origin = `http://127.0.0.1:${gateway.address().port}`;
const report = resolve("build/reports");
await mkdir(report, { recursive: true });
const profile = await mkdtemp(join(report, "budget-browser-profile-"));
let browser;
let socket;
try {
  const candidates = [process.env.BROWSER_EXECUTABLE, "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe", "/usr/bin/google-chrome"];
  let executable;
  for (const candidate of candidates.filter(Boolean)) {
    try { await access(candidate); executable = candidate; break; } catch { /* try installed browser */ }
  }
  assert(executable, "Install Chrome/Edge or set BROWSER_EXECUTABLE for the optional browser regression");
  browser = spawn(executable, ["--headless", "--no-first-run", "--no-default-browser-check", "--disable-extensions",
    "--disable-background-networking", "--remote-debugging-address=127.0.0.1", "--remote-debugging-port=0",
    `--user-data-dir=${profile}`, "about:blank"], { windowsHide: true, stdio: "ignore" });
  const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
  let port;
  for (let i = 0; i < 100 && !port; i++) {
    try { port = (await readFile(join(profile, "DevToolsActivePort"), "utf8")).split("\n")[0]; }
    catch { await delay(100); }
  }
  assert(port, "Browser debugging endpoint did not start");
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  socket = new WebSocket(targets.find(target => target.type === "page").webSocketDebuggerUrl);
  await once(socket, "open");
  let id = 0;
  const pending = new Map();
  socket.addEventListener("message", event => {
    const message = JSON.parse(event.data);
    if (pending.has(message.id)) { pending.get(message.id)(message); pending.delete(message.id); }
  });
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const request = ++id;
    const timer = setTimeout(() => { pending.delete(request); reject(new Error("Browser command timed out: " + method)); }, 15000);
    pending.set(request, message => { clearTimeout(timer); message.error ? reject(new Error(message.error.message)) : resolve(message.result); });
    socket.send(JSON.stringify({ id: request, method, params }));
  });
  const evaluate = async expression => {
    const result = await send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
    assert(!result.exceptionDetails, "Browser JavaScript failed");
    return result.result.value;
  };
  const wait = async expression => {
    for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await delay(100); }
    throw new Error("Browser condition timed out: " + expression);
  };
  await send("Page.enable");
  await send("Page.navigate", { url: `${origin}/service-orders/${fixture.orderId}/budget/decision?decision=approve` });
  await wait("Boolean(document.getElementById('login-button') && !document.getElementById('login-button').disabled)");
  assert(!(await evaluate("document.body.textContent")).includes("Browser private diagnosis"));
  const login = async cpf => {
    await evaluate(`document.getElementById('cpf').value=${JSON.stringify(cpf)};document.getElementById('cpf-form').requestSubmit();true`);
  };
  await login("00000000000");
  await wait("document.getElementById('message').textContent.includes('inválida')");
  await login(fixture.foreignCpf);
  await wait("document.getElementById('message').textContent.includes('não possui acesso')");
  assert(await evaluate("document.getElementById('budget-content').hidden"));
  assert.equal(decisions, 0);
  await login(fixture.ownerCpf);
  await wait("!document.getElementById('budget-content').hidden");
  assert((await evaluate("document.getElementById('budget-content').textContent")).includes("Browser private diagnosis"));
  assert(await evaluate("localStorage.length === 0 && sessionStorage.length === 0 && !location.search.includes('cpf=') && !location.search.includes('token=')"));
  const screenshot = await send("Page.captureScreenshot", { format: "png" });
  await writeFile(join(report, "budget-access-browser.png"), Buffer.from(screenshot.data, "base64"));
  const budgetStatus = async () => (await (await fetch(`${backend}/service-orders/${fixture.orderId}/budget`,
    { headers: { Authorization: `Bearer ${fixture.ownerToken}` } })).json()).status;
  assert.equal(await budgetStatus(), "FINALIZED", "Login and page GET must not decide the budget");
  await evaluate("document.querySelector('form[action$=\"/approve\"]').requestSubmit();true");
  await wait("document.getElementById('budget-content').textContent.includes('Orçamento aprovado')");
  assert.equal(await budgetStatus(), "APPROVED");
  assert.equal(decisions, 1);
  await evaluate("document.getElementById('logout-button').click();true");
  assert(await evaluate("document.getElementById('budget-content').hidden && !document.getElementById('budget-content').textContent && !document.getElementById('login-card').hidden"));
  await login(fixture.ownerCpf);
  await wait("!document.getElementById('budget-content').hidden");
  await evaluate("window.dispatchEvent(new Event('pagehide'));true");
  assert(await evaluate("document.getElementById('budget-content').hidden && !document.getElementById('budget-content').textContent"));
  console.log(JSON.stringify({ status: "passed", checks: ["real browser CSP", "generic landing", "invalid CPF", "foreign customer denied",
    "owner authenticated", "no URL/storage token", "GET without decision", "JWT-protected approval", "logout", "pagehide privacy"] }));
  await send("Browser.close").catch(() => {});
} finally {
  if (socket?.readyState === WebSocket.OPEN) socket.close();
  if (browser && browser.exitCode === null) browser.kill();
  await new Promise(resolve => gateway.close(resolve));
}
