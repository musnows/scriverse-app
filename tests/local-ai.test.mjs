import test from "node:test";
import assert from "node:assert/strict";
import { MobileLocalAiStore, createMobileLocalAiBridge } from "../dist/public/local-ai-engine.js";

function storage() { const values = new Map(); return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), values }; }
function model(providerId, extra = {}) { return { providerId, displayName: "手机模型", modelId: "mobile-model", modelKind: "chat", purposes: ["chat", "continue", "polish"], contextWindow: 32768, preset: { temperature: 0.7, max_tokens: 1024 }, thinkingEnabled: false, multimodalEnabled: false, imageToolDefault: false, enabled: true, ...extra }; }
async function configured(fetchImpl, extra = {}) {
  const data = storage();
  const store = new MobileLocalAiStore({ storage: data, fetchImpl });
  const provider = await store.createProvider({ name: "手机本机", baseUrl: "http://127.0.0.1:11434/v1", apiKey: "test-secret-not-for-storage", rpmLimit: 10000, ...extra });
  const item = await store.createModel(model(provider.id));
  return { store, provider, item, data };
}

test("local provider credentials stay out of metadata and survive unrelated updates", async () => {
  const { store, provider, item, data } = await configured();
  assert.equal([...data.values.values()].some((value) => value.includes("test-secret-not-for-storage")), false);
  assert.equal(store.configuration().providers[0].apiKey, "");
  assert.equal(store.configuration().providers[0].hasApiKey, true);
  await store.updateProvider({ name: "新名称", baseUrl: provider.baseUrl, apiKey: "", providerId: provider.id, replaceApiKey: false });
  assert.equal((await store.credential(item.id)).provider.apiKey, "test-secret-not-for-storage");
  await assert.rejects(store.createProvider({ name: "坏地址", baseUrl: "file:///etc/passwd" }), { code: "LOCAL_AI_BASE_URL_INVALID" });
  await assert.rejects(store.createProvider({ name: "未知字段", baseUrl: "http://127.0.0.1:11434", secret: "injection" }), { code: "LOCAL_AI_INPUT_INVALID" });
});

for (const protocol of ["openai-chat-completions", "openai-responses", "anthropic-messages"]) test(`local ${protocol} uses the phone endpoint and appends the local prompt`, async () => {
  let calls = 0;
  const { store, item } = await configured(async (url, init) => {
    calls += 1;
    const body = JSON.parse(init.body);
    assert.ok(String(url).startsWith("http://127.0.0.1:11434/"));
    assert.ok(init.body.includes("本机约束"));
    assert.ok(!init.body.includes("undefined"));
    const payload = protocol === "openai-responses" ? { output_text: "本机回复" } : protocol === "anthropic-messages" ? { content: [{ type: "text", text: "本机回复" }] } : { choices: [{ message: { content: "本机回复" } }] };
    assert.equal(body.model, "mobile-model");
    return Response.json(payload);
  }, { protocol });
  await store.updateSystemPrompt({ systemPrompt: "本机约束" });
  const response = await store.run("complete", { requestId: crypto.randomUUID(), modelId: item.id, taskType: "chat", remoteSystemPrompt: "服务器约束", messages: [{ role: "user", content: "开始" }] });
  assert.equal(response.content, "本机回复");
  assert.equal(calls, 1);
});

test("local streaming emits content before completion and can be cancelled", async () => {
  const { store, item } = await configured(async () => new Response('data: {"choices":[{"delta":{"content":"实时回复"}}]}\n\ndata: [DONE]\n\n', { headers: { "content-type": "text/event-stream" } }));
  const events = [];
  const value = await store.run("complete", { requestId: crypto.randomUUID(), modelId: item.id, messages: [{ role: "user", content: "继续" }] }, (event) => events.push(event));
  assert.equal(value.content, "实时回复");
  assert.ok(events.some((event) => event.type === "delta" || event.content === "实时回复" || event.delta === "实时回复"));
  const aborting = await configured((_url, init) => new Promise((_resolve, reject) => init.signal.addEventListener("abort", () => reject(init.signal.reason), { once: true })));
  const requestId = crypto.randomUUID();
  const pending = aborting.store.run("complete", { requestId, modelId: aborting.item.id, messages: [{ role: "user", content: "取消" }] });
  aborting.store.cancel({ requestId });
  await assert.rejects(pending, { code: "LOCAL_AI_CANCELLED" });
});

test("disabled models and unconfigured purposes cannot send local requests", async () => {
  const { store, item } = await configured(() => assert.fail("Unexpected endpoint request"));
  await assert.rejects(store.run("complete", { requestId: crypto.randomUUID(), modelId: item.id, taskType: "chapter-analysis", messages: [{ role: "user", content: "开始" }] }), { code: "LOCAL_AI_TASK_TYPE_UNSUPPORTED" });
  await store.updateModel({ ...model(item.providerId), localModelId: item.id, enabled: false });
  await assert.rejects(store.run("complete", { requestId: crypto.randomUUID(), modelId: item.id, messages: [{ role: "user", content: "开始" }] }), { code: "LOCAL_AI_MODEL_DISABLED" });
});

test("local connection tests report success using the configuration UI contract", async () => {
  const bridge = createMobileLocalAiBridge({ storage: storage(), fetchImpl: async () => Response.json({ choices: [{ message: { content: "OK" } }] }) });
  const provider = (await bridge.createProvider({ name: "测试供应商", baseUrl: "http://127.0.0.1:11434/v1" })).data;
  const item = (await bridge.createModel(model(provider.id))).data;
  const response = await bridge.testModel({ modelId: item.id });
  assert.equal(response.ok, true);
  assert.equal(response.data.ok, true);
  assert.equal((await bridge.configuration()).data.providers[0].connectionStatus, "success");
});

test("Google Vertex signs the service-account assertion locally and uses a scoped OAuth token", async () => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const pem = "-----BEGIN PRIVATE KEY-----\n" + Buffer.from(await crypto.subtle.exportKey("pkcs8", pair.privateKey)).toString("base64") + "\n-----END PRIVATE KEY-----";
  const account = JSON.stringify({ type: "service_account", client_email: "test@example.iam.gserviceaccount.com", private_key: pem });
  let tokenRequests = 0;
  const { store, item } = await configured(async (url, init) => {
    if (url === "https://oauth2.googleapis.com/token") {
      tokenRequests += 1;
      const assertion = new URLSearchParams(init.body).get("assertion");
      const [header, payload, signature] = assertion.split(".");
      assert.equal(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", pair.publicKey, Buffer.from(signature, "base64url"), new TextEncoder().encode(header + "." + payload)), true);
      assert.equal(JSON.parse(Buffer.from(payload, "base64url")).aud, "https://oauth2.googleapis.com/token");
      return Response.json({ access_token: "scoped-test-token", expires_in: 3600 });
    }
    assert.equal(init.headers.Authorization, "Bearer scoped-test-token");
    assert.equal(init.body.includes("BEGIN PRIVATE KEY"), false);
    return Response.json({ choices: [{ message: { content: "Vertex 本机回复" } }] });
  }, { protocol: "google-vertex", baseUrl: "https://aiplatform.googleapis.com/v1/projects/test/locations/global/endpoints/openapi", apiKey: account });
  assert.equal((await store.run("complete", { requestId: crypto.randomUUID(), modelId: item.id, messages: [{ role: "user", content: "你好" }] })).content, "Vertex 本机回复");
  assert.equal(tokenRequests, 1);
});
