import { APP_APPEARANCE_KEY, APP_FONT_SIZES, DEFAULT_APP_FONT_SIZE, readAppFontSize, writeAppFontSize, scaleTypographyValue } from "./app-appearance-settings.js";

const root = document.documentElement;
let fontSize = readAppFontSize(localStorage);
const processed = new WeakSet();
let pendingStyles = false;
let dialog;

function scaleRules(rules) {
  for (const rule of rules) {
    if (rule.style && !processed.has(rule.style)) {
      for (const property of ["font-size", "line-height"]) {
        const before = rule.style.getPropertyValue(property);
        const after = scaleTypographyValue(before, property);
        if (after !== before.trim()) rule.style.setProperty(property, after, rule.style.getPropertyPriority(property));
      }
      processed.add(rule.style);
    }
    if (rule.cssRules) scaleRules(rule.cssRules);
    if (rule.styleSheet) { try { scaleRules(rule.styleSheet.cssRules); } catch { /* 外部样式表由其来源管理。 */ } }
  }
}

function refreshStyles() {
  pendingStyles = false;
  for (const sheet of document.styleSheets) { try { scaleRules(sheet.cssRules); } catch { /* 尚未就绪的样式表在加载后重试。 */ } }
}

function scheduleStyles() {
  if (pendingStyles) return;
  pendingStyles = true;
  queueMicrotask(refreshStyles);
}

function applyFontSize(value) {
  fontSize = value;
  root.style.setProperty("--app-font-scale", String(value / 100));
  root.dataset.appFontSize = String(value);
  const input = document.querySelector("#app-font-size");
  if (input) input.value = String(value);
  const output = document.querySelector("#app-font-size-value");
  if (output) output.textContent = `${value}%${value === DEFAULT_APP_FONT_SIZE ? " · 默认" : ""}`;
  for (const [id, disabled] of [["app-font-smaller", value === APP_FONT_SIZES[0]], ["app-font-larger", value === APP_FONT_SIZES.at(-1)]]) {
    const button = document.getElementById(id);
    if (button) button.disabled = disabled;
  }
  requestAnimationFrame(() => window.dispatchEvent(new Event("resize")));
}

function changeFontSize(value) {
  const status = document.querySelector("#app-font-save-status");
  try {
    writeAppFontSize(localStorage, value);
    applyFontSize(value);
    if (status) status.textContent = "已保存，所有页面立即生效";
  } catch {
    applyFontSize(fontSize);
    if (status) status.textContent = "保存失败，请检查设备存储空间后重试";
  }
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function createDialog() {
  if (dialog) return dialog;
  dialog = element("dialog", "app-settings-dialog");
  dialog.id = "app-settings-dialog";
  dialog.setAttribute("aria-labelledby", "app-settings-title");
  const header = element("header", "app-settings-header");
  const brand = element("div", "app-settings-brand");
  const logo = element("img", ""); logo.src = "/icon.svg"; logo.alt = "";
  const identity = element("div", "");
  const title = element("h2", "", "叙界"); title.id = "app-settings-title";
  const version = element("small", "", "App 设置");
  identity.append(title, version); brand.append(logo, identity);
  const close = element("button", "app-settings-close", "×"); close.type = "button"; close.setAttribute("aria-label", "关闭应用设置"); close.addEventListener("click", () => dialog.close());
  header.append(brand, close);
  const content = element("div", "app-settings-content");
  const section = element("section", "app-font-settings"); section.setAttribute("aria-labelledby", "app-font-title");
  const fontTitle = element("h3", "", "全局 App 字号"); fontTitle.id = "app-font-title";
  const description = element("p", "", "同时调整界面、正文、设定库、AI 和本机设置。原有的独立字号设置仍然有效。");
  const preview = element("div", "app-font-preview"); preview.append(element("strong", "", "让故事继续"), element("p", "", "正文与设定库 · Scriverse 123"));
  const controls = element("div", "app-font-controls");
  const smaller = element("button", "", "A−"); smaller.id = "app-font-smaller"; smaller.type = "button"; smaller.setAttribute("aria-label", "缩小全局字号");
  const larger = element("button", "", "A＋"); larger.id = "app-font-larger"; larger.type = "button"; larger.setAttribute("aria-label", "放大全局字号");
  const range = element("input", ""); range.id = "app-font-size"; range.type = "range"; range.min = "80"; range.max = "120"; range.step = "10"; range.setAttribute("aria-label", "全局 App 字号");
  range.addEventListener("input", () => changeFontSize(Number(range.value)));
  smaller.addEventListener("click", () => changeFontSize(APP_FONT_SIZES[Math.max(0, APP_FONT_SIZES.indexOf(fontSize) - 1)]));
  larger.addEventListener("click", () => changeFontSize(APP_FONT_SIZES[Math.min(APP_FONT_SIZES.length - 1, APP_FONT_SIZES.indexOf(fontSize) + 1)]));
  controls.append(smaller, range, larger);
  const meta = element("div", "app-font-meta");
  const output = element("output", ""); output.id = "app-font-size-value"; output.setAttribute("for", range.id);
  const reset = element("button", "app-settings-text-button", "恢复默认"); reset.id = "app-font-reset"; reset.type = "button"; reset.addEventListener("click", () => changeFontSize(DEFAULT_APP_FONT_SIZE));
  meta.append(output, reset);
  const status = element("p", "app-font-status", "默认 90%，比原字号小一级；设置保存在这台设备上。"); status.id = "app-font-save-status"; status.setAttribute("role", "status");
  section.append(fontTitle, description, preview, controls, meta, status);
  let ai = document.querySelector("#local-ai-open");
  if (!ai) {
    ai = element("button", ""); ai.id = "local-ai-open";
    ai.addEventListener("click", () => { dialog.close(); if (location.pathname.endsWith("workspace.html")) localStorage.setItem("scriverse.mobile.last-workspace-url", location.pathname + location.search + location.hash); location.assign("/local-ai/index.html" + (location.pathname.endsWith("workspace.html") ? "" : "?from=launcher")); });
  }
  ai.className = "app-settings-action"; ai.type = "button"; ai.textContent = "本机 AI 设置"; ai.removeAttribute("aria-label");
  content.append(section, ai);
  dialog.append(header, content);
  document.body.append(dialog);
  applyFontSize(fontSize);
  void fetch("/app-version.json").then((response) => response.json()).then((data) => { if (typeof data.version === "string") version.textContent = `App ${data.version}`; }).catch(() => undefined);
  return dialog;
}

function open() { createDialog().showModal(); document.querySelector("#app-font-size")?.focus(); }
applyFontSize(fontSize);
document.addEventListener("load", (event) => { if (event.target instanceof HTMLLinkElement) scheduleStyles(); }, true);
new MutationObserver((records) => { if (records.some((record) => [...record.addedNodes].some((node) => node.nodeType === 1 && (node.matches?.("style, link[rel=stylesheet]") || node.querySelector?.("style, link[rel=stylesheet]"))))) scheduleStyles(); }).observe(root, { childList: true, subtree: true });
document.addEventListener("DOMContentLoaded", () => {
  refreshStyles(); createDialog();
  const auth = document.querySelector("#auth-view");
  if (auth) { const more = element("button", "app-auth-more", "更多"); more.type = "button"; more.dataset.appSettings = ""; more.setAttribute("aria-haspopup", "dialog"); more.setAttribute("aria-controls", "app-settings-dialog"); auth.append(more); }
});
document.addEventListener("click", (event) => { if (event.target.closest?.("[data-app-settings]")) open(); });
window.addEventListener("storage", (event) => { if (event.key === APP_APPEARANCE_KEY || event.key === null) applyFontSize(readAppFontSize(localStorage)); });
window.addEventListener("pageshow", () => applyFontSize(readAppFontSize(localStorage)));
globalThis.scriverseMobileBack ??= () => { if (!dialog?.open) return false; dialog.close(); return true; };
globalThis.scriverseAppAppearance = { open };
scheduleStyles();
