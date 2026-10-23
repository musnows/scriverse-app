import { Capacitor, registerPlugin } from "@capacitor/core";
import { createNativeXmlHttpRequest, createNativeEventSource } from "./native-web-apis.js";
import { installMobileInterface } from "./mobile-interface.js";
import { cachedProfileUser } from "./profile-session.js";
import { createMobileLocalAiBridge } from "./local-ai-store.js";
import { createNativeFetch } from "./native-fetch.js";

document.documentElement.classList.add("scriverse-mobile-app");
const native = Capacitor.isNativePlatform() ? registerPlugin("NativeWorkspace") : null;
let conversationRenderingQueue = Promise.resolve();
function updateConversationRendering(active) {
  if (!native) return;
  conversationRenderingQueue = conversationRenderingQueue.catch(() => undefined).then(() => native.setConversationRendering({ active }));
  void conversationRenderingQueue.catch(() => console.warn("Could not update native conversation rendering"));
}
updateConversationRendering(false);
const rawFetch = globalThis.fetch.bind(globalThis);
const params = new URL(location.href).searchParams;
const profileId = params.get("profileId") ?? localStorage.getItem("scriverse.mobile.active-server-profile.v1");
let profiles = [];
try {
  const stored = JSON.parse(localStorage.getItem("scriverse.mobile.server-profiles.v1") || "[]");
  if (Array.isArray(stored)) profiles = stored;
} catch { /* 无效工作区配置由列表页重新填写 */ }
const profile = profiles.find((candidate) => candidate.id === profileId);
const isWorkspace = location.pathname.endsWith("workspace.html");
const forcedOffline = params.get("offline") === "1";
let user = null;
let leaveState = { dirty: false, activeAiRequests: 0, pendingMutations: 0, conflicts: 0, rejected: 0 };
const ok = (data = {}) => ({ ok: true, data });
const noopSubscription = () => () => undefined;
const sessionKey = (id) => `scriverse.mobile.profile-user.${id}`;
function cachedUser() {
  try { return cachedProfileUser(JSON.parse(localStorage.getItem(sessionKey(profileId)) || "null"), profile?.origin); } catch { return null; }
}
const ready = (async () => {
  user = cachedUser();
  if (native && profile && isWorkspace) {
    const result = await native.configure({ profileId: profile.id, origin: profile.origin, clientVersion: __SERVER_VERSION__, offline: forcedOffline });
    user = result.user ?? user;
  }
})();
const nativeFetch = native ? createNativeFetch(native, { ready }) : null;

const localAi = createMobileLocalAiBridge({ secrets: native, fetchImpl: nativeFetch ?? rawFetch });
globalThis.scriverseDesktop = { localAi, settings: { async get() { return ok({ colorTheme: localStorage.getItem("ai-novel-theme") ?? (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light") }); } } };

function saveUser(value) {
  if (!value?.userId) return;
  user = value;
  localStorage.setItem(sessionKey(profileId), JSON.stringify({ user, origin: profile?.origin, updatedAt: new Date().toISOString() }));
}

async function serverFetch(input, init = {}) {
  const url = new URL(typeof input === "string" || input instanceof URL ? input : input.url, location.href);
  if (url.origin !== location.origin || !url.pathname.startsWith("/api/")) return rawFetch(input, init);
  await ready;
  if (forcedOffline) return Response.json({ error: { code: "DESKTOP_OFFLINE", message: "当前处于离线工作区" } }, { status: 503 });
  const target = native ? new URL(url.pathname + url.search, profile.origin).href : url.href;
  const response = await (nativeFetch ?? rawFetch)(input instanceof Request ? new Request(target, input) : target, init);
  if (response.ok && url.pathname === "/api/auth/session" && String(init.method ?? "GET").toUpperCase() === "GET") {
    const session = await response.clone().json().catch(() => null);
    if (session?.data?.authenticated === true) saveUser(session.data.user);
  }
  if (response.ok && ["/api/auth/login", "/api/auth/register"].includes(url.pathname)) {
    const session = await response.clone().json().catch(() => null);
    saveUser(session?.data?.user);
  }
  if (response.ok && url.pathname === "/api/auth/session" && init.method === "DELETE") {
    user = null;
    localStorage.removeItem(sessionKey(profileId));
  }
  return response;
}

async function requestSwitch() {
  if (leaveState.dirty && !globalThis.confirm("当前编辑尚未保存，返回前请确认已保存正文或设定库内容。仍要返回工作区列表吗？")) return ok({ cancelled: true });
  location.assign("/index.html");
  return ok();
}

if (isWorkspace && profile) {
  globalThis.fetch = serverFetch;
  if (native) {
    globalThis.XMLHttpRequest = createNativeXmlHttpRequest(serverFetch, { baseUrl: location.href, Original: globalThis.XMLHttpRequest });
    globalThis.EventSource = createNativeEventSource(serverFetch, { baseUrl: location.href, Original: globalThis.EventSource });
  }
  globalThis.scriverseDesktopWorkspace = {
    shellProtocol: 1,
    syncProtocol: 1,
    localAi,
    shell: {
      async getCapabilities() {
        await ready;
        return ok({ profileId: profile.id, profileName: profile.name, profileKind: "remote", origin: profile.origin, capabilities: { syncProtocol: { min: 1, max: 1 } }, user, connectionMode: forcedOffline ? "offline" : "online" });
      },
      async reportLeaveState(state) {
        leaveState = { ...leaveState, ...state };
        if (user) {
          const { DesktopSyncStore } = await import("/desktop-sync-store.js");
          const store = new DesktopSyncStore({ profileId: profile.id, userId: user.userId });
          try {
            const works = await store.listWorks();
            localStorage.setItem(`scriverse.mobile.offline-summary.${profile.id}`, JSON.stringify({ userId: user.userId, works: works.length, pending: state.pendingMutations, conflicts: state.conflicts, rejected: state.rejected }));
          } finally { store.close(); }
        }
        return ok();
      },
      requestSwitch,
      confirmQuit: requestSwitch,
      async getNetworkStatus() { return ok({ online: !forcedOffline && navigator.onLine, monitoring: true }); },
      onNetworkStatus: noopSubscription,
      onExternalUrlRequest: noopSubscription,
      onMenuCommand: noopSubscription,
      async cacheWorkCover({ workId }) {
        if (native && !forcedOffline) await native.cacheMedia({ paths: [`/api/works/${encodeURIComponent(workId)}/cover`] });
        return ok();
      },
      async cacheWorkImages({ workId }) {
        if (native && !forcedOffline && user) {
          const { DesktopSyncStore } = await import("/desktop-sync-store.js");
          const store = new DesktopSyncStore({ profileId: profile.id, userId: user.userId });
          try {
            const images = await store.listEntities(workId, "setting-attachment");
            await native.cacheMedia({ paths: images.map((image) => `/api/attachments/${encodeURIComponent(image.entityId)}/content`) });
          } finally { store.close(); }
        }
        return ok();
      }
    }
  };
}

globalThis.scriverseNative = { native, ready, nativeFetch, requestSwitch, profile, saveUser };

if (native) document.addEventListener("click", (event) => {
  const anchor = event.target.closest?.("a[download]");
  if (!anchor) return;
  const url = new URL(anchor.href, location.href);
  if (url.protocol !== "blob:" && (url.origin !== location.origin || !url.pathname.startsWith("/api/"))) return;
  event.preventDefault();
  void (async () => {
    const response = await (url.protocol === "blob:" ? rawFetch(url) : serverFetch(url));
    if (!response.ok) throw new Error("导出失败，请重试");
    const bytes = new Uint8Array(await response.arrayBuffer());
    const { encodeBytes } = await import("./native-fetch.js");
    await native.saveFile({ name: anchor.download || "scriverse-export.json", mimeType: response.headers.get("content-type") || "application/octet-stream", body: encodeBytes(bytes) });
    showFileStatus("文件已保存到下载目录");
  })().catch((error) => showFileStatus(error.message));
}, { capture: true });

function showFileStatus(message) {
  let status = document.querySelector("#native-file-status");
  if (!status) { status = document.createElement("div"); status.id = "native-file-status"; status.className = "toast"; status.setAttribute("role", "status"); document.body.append(status); }
  status.textContent = message;
  status.hidden = false;
  setTimeout(() => { status.hidden = true; }, 5000);
}

if (location.pathname.includes("/local-ai/")) document.addEventListener("DOMContentLoaded", () => {
  const back = document.querySelector(".local-ai-back");
  const previous = localStorage.getItem("scriverse.mobile.last-workspace-url");
  if (params.get("from") !== "launcher" && back && previous && previous.startsWith("/workspace.html?")) back.href = previous;
});
if (isWorkspace) localStorage.setItem("scriverse.mobile.last-workspace-url", location.pathname + location.search + location.hash);

document.addEventListener("DOMContentLoaded", () => {
  if (!isWorkspace) return;
  const grid = document.querySelector(".settings-hub-grid");
  if (!grid) return;
  const button = document.createElement("button");
  button.id = "mobile-local-ai-button";
  button.className = "settings-hub-card";
  button.type = "button";
  const mark = document.createElement("span"); mark.className = "settings-card-mark"; mark.textContent = "AI";
  const title = document.createElement("strong"); title.textContent = "本机 AI";
  const detail = document.createElement("small"); detail.textContent = "手机本地模型、局域网 endpoint 与独立 API Key";
  button.append(mark, title, detail);
  button.addEventListener("click", () => { localStorage.setItem("scriverse.mobile.last-workspace-url", location.pathname + location.search + location.hash); location.assign("/local-ai/index.html"); });
  grid.prepend(button);
});

document.addEventListener("DOMContentLoaded", () => {
  if (isWorkspace && profile) installMobileInterface({ profile, offline: forcedOffline, requestSwitch, onAiWorkspaceChange: updateConversationRendering });
});
