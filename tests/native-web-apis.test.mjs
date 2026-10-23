import test from "node:test";
import assert from "node:assert/strict";
import { createNativeXmlHttpRequest, createNativeEventSource } from "../src/native-web-apis.js";

function once(target, event) { return new Promise((resolve) => target.addEventListener(event, resolve, { once: true })); }

test("native XHR uploads multipart content, emits progress and preserves API errors", async () => {
  const Xhr = createNativeXmlHttpRequest(async (url, init) => {
    assert.equal(url, "https://localhost/api/import");
    assert.equal(init.body.get("file").name, "novel.txt");
    assert.equal(init.headers.get("X-CSRF-Token"), "test-token");
    init.onUploadProgress({ loaded: 50, total: 100 });
    return Response.json({ error: { code: "INVALID_UPLOAD", message: "测试错误" } }, { status: 400, headers: { "x-test": "preserved" } });
  }, { baseUrl: "https://localhost/" });
  const request = new Xhr();
  const progress = [];
  request.upload.addEventListener("progress", (event) => progress.push(event.loaded / event.total));
  request.open("POST", "/api/import");
  request.setRequestHeader("X-CSRF-Token", "test-token");
  const completed = once(request, "load");
  const form = new FormData(); form.set("file", new Blob(["测试内容"]), "novel.txt");
  request.send(form);
  await completed;
  assert.deepEqual(progress, [0.5]);
  assert.equal(request.status, 400);
  assert.equal(request.readyState, 4);
  assert.equal(JSON.parse(request.responseText).error.code, "INVALID_UPLOAD");
  assert.equal(request.getResponseHeader("x-test"), "preserved");
});

test("native XHR abort and timeout are distinguishable to upload callers", async () => {
  const Xhr = createNativeXmlHttpRequest((_url, init) => new Promise((_resolve, reject) => init.signal.addEventListener("abort", () => reject(init.signal.reason))), { baseUrl: "https://localhost" });
  const aborted = new Xhr(); aborted.open("POST", "/api/import");
  const abortEvent = once(aborted, "abort"); aborted.send("test"); aborted.abort(); await abortEvent;
  assert.equal(aborted.status, 0);
  const timed = new Xhr(); timed.open("POST", "/api/import"); timed.timeout = 10;
  const timeoutEvent = once(timed, "timeout"); timed.send("test"); await timeoutEvent;
  assert.equal(timed.status, 0);
});

test("native EventSource keeps IM event types, multiline data and event identity", async () => {
  const Source = createNativeEventSource(async (_url, init) => {
    assert.equal(init.headers.Accept, "text/event-stream");
    return new Response(new ReadableStream({ start(controller) {
      controller.enqueue(new TextEncoder().encode('event: ready\r\nid: 17\r\ndata: {"state":\r\n'));
      controller.enqueue(new TextEncoder().encode('data: "ready"}\r\n\r\n'));
    } }), { headers: { "content-type": "text/event-stream" } });
  }, { baseUrl: "https://localhost" });
  const source = new Source("/api/im/events");
  const event = await once(source, "ready");
  assert.equal(event.data, '{"state":\n"ready"}');
  assert.equal(event.lastEventId, "17");
  assert.equal(source.readyState, 1);
  source.close();
  assert.equal(source.readyState, 2);
});
