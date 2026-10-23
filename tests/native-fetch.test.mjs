import test from "node:test";
import assert from "node:assert/strict";
import { createNativeFetch, encodeBytes } from "../src/native-fetch.js";

function plugin(request) {
  let listener;
  return {
    addListener(_name, callback) { listener = callback; return Promise.resolve({ remove() {} }); },
    request: (input) => request(input, (event) => listener({ requestId: input.requestId, ...event })),
    cancelRequest: async () => ({}),
  };
}

test("native requests preserve UTF-8, multipart data and response status", async () => {
  const native = plugin(async (request) => {
    assert.equal(request.method, "PATCH");
    assert.equal(new TextDecoder().decode(Uint8Array.from(atob(request.body), (value) => value.charCodeAt(0))), '{"name":"星海"}');
    return { status: 200, headers: { "content-type": "application/json" }, body: encodeBytes(new TextEncoder().encode('{"data":{"name":"星海"}}')) };
  });
  const response = await createNativeFetch(native)("https://example.test/api/characters/1", { method: "PATCH", body: JSON.stringify({ name: "星海" }) });
  assert.equal((await response.json()).data.name, "星海");
  const form = new FormData();
  form.set("file", new Blob(["星海"]), "book.txt");
  const upload = createNativeFetch(plugin(async (request) => {
    assert.match(request.headers["content-type"], /multipart\/form-data; boundary=/u);
    assert.ok(new TextDecoder().decode(Uint8Array.from(atob(request.body), (value) => value.charCodeAt(0))).includes('filename="book.txt"'));
    return { status: 204, headers: {} };
  }));
  assert.equal((await upload("https://example.test/api/import", { method: "POST", body: form })).status, 204);
});

test("native SSE is readable while the request is still active", async () => {
  let complete;
  const fetch = createNativeFetch(plugin(async (_request, emit) => {
    emit({ chunk: btoa("data: first\n\n") });
    complete = () => emit({ done: true });
    return { status: 200, headers: { "content-type": "text/event-stream" } };
  }));
  const response = await fetch("https://example.test/api/chat/stream", { method: "POST", headers: { Accept: "text/event-stream" }, body: "{}" });
  const reader = response.body.getReader();
  assert.equal(new TextDecoder().decode((await reader.read()).value), "data: first\n\n");
  complete();
  assert.equal((await reader.read()).done, true);
});

test("aborting a native stream cancels the request and errors the reader", async () => {
  let cancelled = false;
  const native = plugin(async () => ({ status: 200, headers: { "content-type": "text/event-stream" } }));
  native.cancelRequest = async () => { cancelled = true; };
  const controller = new AbortController();
  const response = await createNativeFetch(native)("https://example.test/api/chat/stream", { headers: { Accept: "text/event-stream" }, signal: controller.signal });
  const reader = response.body.getReader();
  controller.abort();
  await assert.rejects(reader.read(), { name: "AbortError" });
  assert.equal(cancelled, true);
});

test("native network rejects unsafe URLs before sending data", async () => {
  const fetch = createNativeFetch(plugin(() => assert.fail("Unexpected network request")));
  for (const url of ["file:///etc/passwd", "https://user:password@example.test", "https://example.test/#secret"]) await assert.rejects(fetch(url), TypeError);
});

test("native fetch preserves Request bodies while applying explicit overrides", async () => {
  const native = plugin(async (input) => {
    assert.equal(input.method, "PATCH");
    assert.equal(atob(input.body), "original-body");
    assert.equal(input.headers["x-overridden"], "new-value");
    return { status: 204, headers: {} };
  });
  const request = new Request("https://example.test/api/entity", { method: "PATCH", headers: { "X-Overridden": "old-value" }, body: "original-body" });
  assert.equal((await createNativeFetch(native)(request, { headers: { "X-Overridden": "new-value" } })).status, 204);
});
