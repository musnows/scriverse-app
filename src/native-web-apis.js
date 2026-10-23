function emit(target, type, fields = {}) {
  const event = new Event(type);
  for (const [key, value] of Object.entries(fields)) Object.defineProperty(event, key, { value, enumerable: true });
  target[`on${type}`]?.call(target, event);
  target.dispatchEvent(event);
}

export function createNativeXmlHttpRequest(fetchImpl, { baseUrl, Original = null } = {}) {
  return class NativeXmlHttpRequest extends EventTarget {
    static UNSENT = 0; static OPENED = 1; static HEADERS_RECEIVED = 2; static LOADING = 3; static DONE = 4;
    constructor() {
      super(); this.upload = new EventTarget(); this.responseType = ""; this.withCredentials = false; this.timeout = 0;
      this._state = 0; this._status = 0; this._text = ""; this._response = null; this._statusText = ""; this._responseUrl = "";
    }
    get readyState() { return this._legacy?.readyState ?? this._state; }
    get status() { return this._legacy?.status ?? this._status; }
    get statusText() { return this._legacy?.statusText ?? this._statusText; }
    get responseText() { return this._legacy?.responseText ?? this._text; }
    get response() { return this._legacy?.response ?? this._response; }
    get responseURL() { return this._legacy?.responseURL ?? this._responseUrl; }
    get responseXML() { return this._legacy?.responseXML ?? null; }
    _setState(value) { this._state = value; emit(this, "readystatechange"); }
    open(method, input, asynchronous = true, ...credentials) {
      this._url = new URL(input, baseUrl);
      this._headers = new Headers();
      this._method = String(method).toUpperCase();
      if (Original && !this._url.pathname.startsWith("/api/")) {
        this._legacy = new Original();
        for (const type of ["readystatechange", "loadstart", "load", "loadend", "error", "abort", "timeout", "progress"]) this._legacy.addEventListener(type, () => emit(this, type));
        for (const type of ["loadstart", "load", "loadend", "error", "abort", "timeout", "progress"]) this._legacy.upload.addEventListener(type, (event) => emit(this.upload, type, { loaded: event.loaded, total: event.total, lengthComputable: event.lengthComputable }));
        this._legacy.open(method, input, asynchronous, ...credentials);
        return;
      }
      if (!asynchronous || credentials.some((value) => value != null)) throw new DOMException("原生 API 请求必须异步发送，且不能包含 URL 凭据", "NotSupportedError");
      this._setState(1);
    }
    setRequestHeader(name, value) { if (this._legacy) this._legacy.setRequestHeader(name, value); else this._headers.append(name, value); }
    getResponseHeader(name) { return this._legacy ? this._legacy.getResponseHeader(name) : this._responseHeaders?.get(name) ?? null; }
    getAllResponseHeaders() { return this._legacy ? this._legacy.getAllResponseHeaders() : [...(this._responseHeaders ?? [])].map(([key, value]) => `${key}: ${value}\r\n`).join(""); }
    overrideMimeType(value) { this._legacy?.overrideMimeType(value); }
    abort() { if (this._legacy) this._legacy.abort(); else this._controller?.abort(); }
    send(body = null) {
      if (this._legacy) { this._legacy.responseType = this.responseType; this._legacy.timeout = this.timeout; this._legacy.withCredentials = this.withCredentials; return this._legacy.send(body); }
      if (this.readyState !== 1 || this._controller) throw new DOMException("请求状态无效", "InvalidStateError");
      this._controller = new AbortController();
      emit(this, "loadstart");
      emit(this.upload, "loadstart");
      const timeout = this.timeout > 0 ? setTimeout(() => { this._timedOut = true; this._controller.abort(); }, this.timeout) : null;
      void (async () => {
        try {
          const response = await fetchImpl(this._url.href, { method: this._method, headers: this._headers, body: ["GET", "HEAD"].includes(this._method) ? undefined : body, signal: this._controller.signal, onUploadProgress: ({ loaded, total }) => emit(this.upload, "progress", { loaded, total, lengthComputable: total > 0 }) });
          this._status = response.status; this._statusText = response.statusText; this._responseUrl = response.url || this._url.href; this._responseHeaders = response.headers;
          this._setState(2);
          emit(this.upload, "load"); emit(this.upload, "loadend");
          this._setState(3);
          const bytes = await response.arrayBuffer();
          this._text = new TextDecoder().decode(bytes);
          this._response = this.responseType === "arraybuffer" ? bytes : this.responseType === "blob" ? new Blob([bytes], { type: response.headers.get("content-type") ?? "" }) : this.responseType === "json" ? JSON.parse(this._text || "null") : this._text;
          emit(this, "progress", { loaded: bytes.byteLength, total: bytes.byteLength, lengthComputable: true });
          this._setState(4); emit(this, "load");
        } catch {
          this._status = 0; this._setState(4);
          const type = this._timedOut ? "timeout" : this._controller.signal.aborted ? "abort" : "error";
          emit(this.upload, type); emit(this.upload, "loadend"); emit(this, type);
        } finally { if (timeout) clearTimeout(timeout); emit(this, "loadend"); }
      })();
    }
  };
}

export function createNativeEventSource(fetchImpl, { baseUrl, Original = null } = {}) {
  return class NativeEventSource extends EventTarget {
    static CONNECTING = 0; static OPEN = 1; static CLOSED = 2;
    constructor(input, options = {}) {
      super();
      this.url = new URL(input, baseUrl).href;
      if (Original && !new URL(this.url).pathname.startsWith("/api/")) return new Original(input, options);
      this.withCredentials = options.withCredentials === true;
      this.readyState = 0; this._retry = 3000; this._lastId = ""; this._closed = false;
      void this._connect();
    }
    async _connect() {
      while (!this._closed) {
        this._controller = new AbortController();
        try {
          const response = await fetchImpl(this.url, { headers: { Accept: "text/event-stream", ...(this._lastId ? { "Last-Event-ID": this._lastId } : {}) }, signal: this._controller.signal });
          if (!response.ok || !response.body || !response.headers.get("content-type")?.includes("text/event-stream")) throw new Error("实时连接不可用");
          if (this._closed) return;
          this.readyState = 1; emit(this, "open");
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";
          while (!this._closed) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/gu, "\n");
            if (buffer.length > 4 * 1024 * 1024) throw new Error("实时事件过大");
            let boundary;
            while ((boundary = buffer.indexOf("\n\n")) !== -1) {
              const frame = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2);
              let type = "message"; const data = [];
              for (const line of frame.split("\n")) {
                const separator = line.indexOf(":");
                const field = separator < 0 ? line : line.slice(0, separator);
                const value = separator < 0 ? "" : line.slice(separator + 1).replace(/^ /u, "");
                if (field === "event") type = value;
                if (field === "data") data.push(value);
                if (field === "id" && !value.includes("\0")) this._lastId = value;
                if (field === "retry" && /^\d+$/u.test(value)) this._retry = Math.max(250, Math.min(60000, Number(value)));
              }
              if (data.length) emit(this, type, { data: data.join("\n"), lastEventId: this._lastId, origin: new URL(this.url).origin });
            }
          }
        } catch { /* 网络断开时沿用 EventSource 的自动重连行为 */ }
        if (this._closed) return;
        this.readyState = 0; emit(this, "error");
        await new Promise((resolve) => { this._wake = resolve; this._timer = setTimeout(resolve, this._retry); });
      }
    }
    close() { this._closed = true; this.readyState = 2; this._controller?.abort(); if (this._timer) clearTimeout(this._timer); this._wake?.(); }
  };
}
