import { installGraphPresentation } from "./graph-presentation.js";

const paths = {
  shelf: '<path d="M3 4h5v16H3zm7 0h5v16h-5zm7 1 4-1 3 15-4 1z"/>',
  prose: '<path d="M3 4h6a4 4 0 0 1 3 3v14a4 4 0 0 0-3-3H3zm18 0h-6a4 4 0 0 0-3 3v14a4 4 0 0 1 3-3h6z"/>',
  library: '<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>',
  ai: '<path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z"/>',
  more: '<circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>'
};
function icon(kind) {
  const holder = document.createElement("span");
  holder.innerHTML = `<svg viewBox="0 0 26 24" aria-hidden="true">${paths[kind] ?? paths.library}</svg>`;
  return holder.firstElementChild;
}
function element(tag, className, text) {
  const node = document.createElement(tag);
  node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

export function installMobileInterface({ profile, offline, requestSwitch, onAiWorkspaceChange = () => undefined, onGraphPresentation = async () => undefined }) {
  const app = document.querySelector("#app");
  if (!app || document.querySelector("#mobile-bottom-nav")) return;
  const nav = element("nav", "mobile-bottom-nav");
  nav.id = "mobile-bottom-nav";
  nav.setAttribute("aria-label", "主导航");
  const buttons = new Map();
  for (const [key, label] of [["shelf", "书架"], ["prose", "正文"], ["library", "设定库"], ["ai", "AI"], ["more", "更多"]]) {
    const button = element("button", "mobile-tab");
    button.type = "button";
    button.dataset.destination = key;
    button.append(icon(key), element("span", "", label));
    if (key === "library" || key === "more") { button.setAttribute("aria-haspopup", "dialog"); button.setAttribute("aria-expanded", "false"); button.setAttribute("aria-controls", "mobile-command-sheet"); }
    buttons.set(key, button);
    nav.append(button);
  }
  document.body.append(nav);

  const sheet = element("dialog", "mobile-command-sheet");
  sheet.id = "mobile-command-sheet";
  sheet.setAttribute("aria-labelledby", "mobile-sheet-title");
  const header = element("header", "mobile-sheet-header");
  const heading = element("div", "");
  heading.append(element("span", "eyebrow", profile.name));
  const title = element("h2", ""); title.id = "mobile-sheet-title";
  heading.append(title);
  const close = element("button", "mobile-sheet-close", "×");
  close.type = "button";
  close.setAttribute("aria-label", "关闭导航面板");
  close.addEventListener("click", () => sheet.close());
  header.append(heading, close);
  const commands = element("div", "mobile-sheet-commands");
  let directoryTree = null;
  let directoryHome = null;
  let directoryNext = null;
  sheet.append(header, commands);
  document.body.append(sheet);
  const welcomeDirectory = element("button", "ghost-button", "查看目录");
  welcomeDirectory.id = "welcome-directory-button";
  welcomeDirectory.type = "button";
  welcomeDirectory.hidden = true;
  welcomeDirectory.setAttribute("aria-haspopup", "dialog");
  welcomeDirectory.setAttribute("aria-expanded", "false");
  welcomeDirectory.setAttribute("aria-controls", sheet.id);
  welcomeDirectory.addEventListener("click", () => openSheet("directory"));
  const welcomeNewChapter = document.querySelector("#welcome-new-work");
  if (welcomeNewChapter) {
    const actions = element("div", "mobile-welcome-actions");
    welcomeNewChapter.before(actions);
    actions.append(welcomeNewChapter, welcomeDirectory);
  }
  sheet.addEventListener("close", () => {
    if (directoryTree && directoryHome) { directoryHome.insertBefore(directoryTree, directoryNext?.parentElement === directoryHome ? directoryNext : null); directoryTree = null; }
    welcomeDirectory.setAttribute("aria-expanded", "false");
 buttons.get("prose").setAttribute("aria-expanded", "false"); buttons.get("library").setAttribute("aria-expanded", "false"); buttons.get("more").setAttribute("aria-expanded", "false"); });
  sheet.addEventListener("click", (event) => { if (event.target === sheet) { const rect = sheet.getBoundingClientRect(); if (event.clientY < rect.top || event.clientY > rect.bottom || event.clientX < rect.left || event.clientX > rect.right) sheet.close(); } });

  function command(label, action, { source = null, detail = "", disabled = false, kind = "library" } = {}) {
    const button = element("button", "mobile-command");
    button.type = "button";
    button.disabled = disabled;
    const sourceIcon = source?.querySelector("svg");
    button.append(sourceIcon ? sourceIcon.cloneNode(true) : icon(kind));
    const copy = element("span", "mobile-command-copy");
    copy.append(element("strong", "", label));
    if (detail) copy.append(element("small", "", detail));
    button.append(copy);
    button.addEventListener("click", () => { sheet.close(); action(); });
    commands.append(button);
  }
  function sourceCommand(selector, label, detail = "", kind = "library") {
    const source = document.querySelector(selector);
    if (source?.classList.contains("hidden") && !source.classList.contains("module-nav-secondary")) return;
    if (source) command(label, () => source.click(), { source, detail, disabled: source.disabled, kind });
  }
  function openSheet(kind) {
    title.textContent = kind === "library" ? "设定库" : kind === "directory" ? "正文目录" : "更多";
    commands.replaceChildren();
    commands.classList.toggle("is-library", kind === "library");
    commands.classList.remove("is-directory");
    if (kind === "directory") {
      directoryTree = document.querySelector("#novel-tree");
      if (directoryTree) { directoryHome = directoryTree.parentElement; directoryNext = directoryTree.nextSibling; commands.append(directoryTree); }
      sourceCommand("#new-volume-button", "新建分卷");
      commands.classList.add("is-directory");
    } else if (kind === "library") {
      for (const source of document.querySelectorAll('#module-nav button[data-module]:not([data-module="editor"]):not([data-module="tasks"]):not([data-module="ai-settings"])')) {
        if (source.classList.contains("hidden") && !source.classList.contains("module-nav-secondary")) continue;
        command(source.textContent.trim(), () => source.click(), { source, disabled: source.disabled });
      }
      sourceCommand("#writing-progress-button", "写作目标");
    } else {
      const hasWork = Boolean(new URLSearchParams(location.hash.slice(1)).get("work"));
      const identity = element("div", "mobile-more-identity");
      const logo = element("img", ""); logo.src = "/icon.svg"; logo.alt = "";
      const copy = element("div", "mobile-more-identity-copy"); copy.append(element("strong", "", "叙界"), element("small", "", profile.name));
      identity.append(logo, copy, chip); commands.append(identity);
      command("显示与字号", () => globalThis.scriverseAppAppearance?.open(), { detail: "全局调整 App 字体大小", kind: "more" });
      sourceCommand("#desktop-sync-status-button", "离线与同步", "下载作品、上传修改与处理版本冲突", "prose");
      sourceCommand("#mobile-local-ai-button", "本机 AI", "配置手机或局域网中的模型 endpoint", "ai");
      if (hasWork) sourceCommand("#top-search-button", "全局搜索");
      if (hasWork) sourceCommand("#reader-open-button", "沉浸阅读");
      if (hasWork) sourceCommand('#module-nav button[data-module="tasks"]', "AI 分析");
      if (hasWork) sourceCommand('#module-nav button[data-module="ai-settings"]', "作品 AI 设置");
      if (hasWork) sourceCommand("#module-nav [data-work-settings]", "当前作品设置");
      if (hasWork) sourceCommand("#import-file", "导入 TXT / DOCX");
      sourceCommand("#settings-button", "设置", "显示、导出、协作与 Server 管理");
      sourceCommand("#theme-toggle", "切换明暗主题");
      sourceCommand("#account-button", "账户");
      sourceCommand("#im-open-button", "消息与协作");
      sourceCommand("#background-task-button", "后台任务");
      command("切换工作区", requestSwitch, { detail: "选择 Server，或主动离线进入", kind: "shelf" });
    }
    welcomeDirectory.setAttribute("aria-expanded", String(kind === "directory"));
    buttons.get(kind === "directory" ? "prose" : kind).setAttribute("aria-expanded", "true");
    sheet.showModal();
    close.focus();
  }
  buttons.get("shelf").addEventListener("click", () => document.querySelector("#home-button")?.click());
  buttons.get("library").addEventListener("click", () => openSheet("library"));
  buttons.get("more").addEventListener("click", () => openSheet("more"));
  buttons.get("ai").addEventListener("click", () => document.querySelector("#ai-assistant-entry")?.click());
  buttons.get("prose").setAttribute("aria-expanded", "false");
  buttons.get("prose").setAttribute("aria-haspopup", "dialog");
  buttons.get("prose").setAttribute("aria-controls", "mobile-command-sheet");
  buttons.get("prose").addEventListener("click", () => openSheet("directory"));
  commands.addEventListener("click", (event) => { if (event.target.closest("[data-chapter-id]")) sheet.close(); });

  const chip = element("button", "mobile-connection-chip");
  chip.id = "mobile-connection-chip";
  chip.type = "button";
  chip.addEventListener("click", () => { if (sheet.open) sheet.close(); document.querySelector("#desktop-sync-status-button")?.click(); });
  document.querySelector(".top-actions")?.prepend(chip);
  const wideMore = element("button", "app-wide-more", "更多"); wideMore.type = "button"; wideMore.dataset.appSettings = ""; wideMore.setAttribute("aria-haspopup", "dialog"); wideMore.setAttribute("aria-controls", "app-settings-dialog");
  document.querySelector(".top-actions")?.append(wideMore);
  let status = {};
  try { status = JSON.parse(localStorage.getItem(`scriverse.mobile.offline-summary.${profile.id}`) || "{}"); } catch { /* 首次连接没有缓存状态 */ }
  function updateConnection(detail = {}) {
    status = { ...status, ...detail };
    const pending = Number(status.pendingMutations ?? status.pending ?? 0);
    const attention = Number(status.conflicts ?? 0) + Number(status.rejected ?? 0);
    chip.dataset.state = attention > 0 ? "attention" : offline ? "offline" : "online";
    chip.textContent = attention > 0 ? `${attention} 项待处理` : pending > 0 ? `待同步 ${pending}` : offline ? "离线创作" : "在线";
    chip.setAttribute("aria-label", `离线与同步：${chip.textContent}`);
    buttons.get("more").dataset.syncAttention = String(attention > 0 || pending > 0);
    buttons.get("more").setAttribute("aria-description", chip.textContent);
  }
  updateConnection();
  window.addEventListener("scriverse-desktop-sync-state", (event) => {
    updateConnection(event.detail);
    const dialog = document.querySelector("#desktop-sync-dialog");
    const refresh = document.querySelector("#desktop-sync-refresh");
    if (dialog?.open && refresh && !refresh.disabled && ["downloaded", "ready", "conflict", "read-only", "failed"].includes(event.detail?.status)) refresh.click();
  });

  let aiWorkspaceVisible = false;
  function updateNavigation() {
    const route = new URLSearchParams(location.hash.slice(1));
    const hasWork = Boolean(route.get("work"));
    welcomeDirectory.hidden = !hasWork;
    welcomeDirectory.disabled = !hasWork;
    const aiVisible = app.classList.contains("ai-workspace-mode");
    if (aiVisible !== aiWorkspaceVisible) { aiWorkspaceVisible = aiVisible; onAiWorkspaceChange(aiVisible); }
    for (const key of ["prose", "library", "ai"]) buttons.get(key).disabled = !hasWork;
    const current = app.classList.contains("ai-workspace-mode") ? "ai" : ["module", "entity-editor"].includes(route.get("view")) ? "library" : ["editor", "welcome"].includes(route.get("view")) ? "prose" : route.get("view") === "shelf" ? "shelf" : "more";
    for (const [key, button] of buttons) { if (key === current) button.setAttribute("aria-current", "page"); else button.removeAttribute("aria-current"); }
  }
  for (const method of ["pushState", "replaceState"]) {
    const original = history[method].bind(history);
    history[method] = (...args) => { const result = original(...args); updateNavigation(); return result; };
  }
  new MutationObserver(updateNavigation).observe(app, { attributes: true, attributeFilter: ["class"] });
  window.addEventListener("hashchange", updateNavigation);
  updateNavigation();
  installGraphPresentation(onGraphPresentation);
  document.addEventListener("focusin", (event) => { if (event.target.matches?.('input:not([readonly]):not([disabled]), textarea:not([readonly]):not([disabled]), [contenteditable="true"]')) document.documentElement.classList.add("mobile-input-active"); });
  document.addEventListener("focusout", () => setTimeout(() => { if (!document.activeElement?.matches?.('input, textarea, [contenteditable="true"]')) document.documentElement.classList.remove("mobile-input-active"); }, 0));
  window.scriverseMobileBack = () => {
    const dialog = [...document.querySelectorAll("dialog[open]")].at(-1);
    if (dialog) { const canClose = dialog.dispatchEvent(new Event("cancel", { cancelable: true })); if (canClose && dialog.open) dialog.close(); return true; }
    if (app.classList.contains("ai-workspace-mode")) { document.querySelector("#ai-panel-toggle")?.click(); return true; }
    if (!app.classList.contains("left-panel-collapsed") && !app.classList.contains("shelf-mode")) { document.querySelector("#mobile-panel-backdrop")?.click(); return true; }
    return false;
  };
}
