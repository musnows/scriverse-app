export function encodeBytes(bytes) {
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 16384) binary += String.fromCharCode(...bytes.subarray(offset, offset + 16384));
  return btoa(binary);
}

export function decodeBytes(value) {
  return Uint8Array.from(atob(value ?? ""), (character) => character.charCodeAt(0));
}

export function createNativeFetch(native, { baseUrl = "https://localhost", ready = Promise.resolve() } = {}) {
  const streams = new Map();
  const listener = native.addListener("httpStream", (event) => {
    const target = streams.get(event.requestId);
    if (!target) return;
    if (event.error) {
      streams.delete(event.requestId);
      target.error(new TypeError(event.error));
    } else if (event.done) {
      streams.delete(event.requestId);
      target.close();
    } else if (event.chunk) target.enqueue(decodeBytes(event.chunk));
  });
  return async (input, init = {}) => {
    await ready;
    await listener;
    const url = new URL(typeof input === "string" || input instanceof URL ? input : input.url, baseUrl);
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.hash) throw new TypeError("请求地址无效");
    const request = new Request(url, input instanceof Request ? input : init);
    const signal = init.signal ?? request.signal;
    signal?.throwIfAborted();
    const requestId = crypto.randomUUID();
    const stream = request.headers.get("Accept")?.includes("text/event-stream") === true;
    let controller;
    let aborted = false;
    let finish;
    const finished = new Promise((resolve) => { finish = resolve; });
    const bodyStream = stream ? new ReadableStream({
      start(value) {
        controller = value;
        streams.set(requestId, {
          enqueue: (chunk) => value.enqueue(chunk),
          close: () => { value.close(); finish(); },
          error: (error) => { value.error(error); finish(); }
        });
      },
      cancel: () => { streams.delete(requestId); finish(); return native.cancelRequest({ requestId }); }
    }) : null;
    const abort = () => {
      aborted = true;
      if (streams.delete(requestId)) controller.error(signal.reason ?? new DOMException("请求已取消", "AbortError"));
      finish();
      void native.cancelRequest({ requestId });
    };
    signal?.addEventListener("abort", abort, { once: true });
    if (stream) void finished.then(() => signal?.removeEventListener("abort", abort));
    try {
      const body = ["GET", "HEAD"].includes(request.method) ? new Uint8Array() : new Uint8Array(await request.arrayBuffer());
      const result = await native.request({
        requestId,
        url: url.href,
        method: request.method,
        headers: Object.fromEntries(request.headers),
        body: encodeBytes(body),
        stream,
        timeoutMs: init.timeoutMs ?? 180000
      });
      if (aborted) throw signal.reason ?? new DOMException("请求已取消", "AbortError");
      return new Response([204, 205, 304].includes(result.status) || request.method === "HEAD" ? null : stream ? bodyStream : decodeBytes(result.body), { status: result.status, headers: result.headers });
    } catch (error) {
      if (streams.delete(requestId)) { controller.error(error); finish(); }
      if (signal?.aborted) throw signal.reason;
      throw new TypeError("无法连接目标地址，请检查网络与配置", { cause: error });
    } finally {
      if (!stream || aborted) signal?.removeEventListener("abort", abort);
    }
  };
}
