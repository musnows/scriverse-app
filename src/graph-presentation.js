import { createGraphPresentationSession } from "./graph-presentation-session.js";

export function installGraphPresentation(apply) {
  const map = document.getElementById("relationship-map-dialog");
  const galaxy = document.getElementById("relationship-galaxy-dialog");
  if (!map || !galaxy) return;
  const root = document.documentElement;
  const dialogs = new Map([["map", map], ["galaxy", galaxy]]);

  function update(value) {
    root.classList.toggle("mobile-graph-presenting", value.active);
    root.dataset.graphPresentation = value.galaxy ? "galaxy" : value.map ? "map" : "";
    for (const [kind, dialog] of dialogs) dialog.classList.toggle("mobile-graph-fullscreen", value[kind]);
    for (const button of document.querySelectorAll("[data-mobile-graph-toggle]")) {
      const kind = button.dataset.mobileGraphToggle;
      const active = value[kind];
      const text = active ? "退出横屏" : "横屏全屏";
      if (button.textContent !== text) button.textContent = text;
      button.setAttribute("aria-label", active ? `退出${kind === "map" ? "关系图" : "银河图"}横屏全屏` : `横屏全屏查看${kind === "map" ? "关系图" : "银河图"}`);
      if (button.id === "mobile-map-presentation-open") button.setAttribute("aria-expanded", String(active));
      else button.setAttribute("aria-pressed", String(active));
    }
    requestAnimationFrame(() => window.dispatchEvent(new Event("resize")));
  }

  const session = createGraphPresentationSession(apply, {
    onChange: update,
    onError() {
      const dialog = galaxy.open ? galaxy : map.open ? map : null;
      if (!dialog) return;
      const status = document.createElement("p"); status.className = "mobile-graph-presentation-error"; status.setAttribute("role", "alert");
      status.textContent = "无法切换横屏全屏，请重试。";
      dialog.append(status);
    }
  });

  function toggle(kind) {
    document.querySelectorAll(".mobile-graph-presentation-error").forEach((node) => node.remove());
    if (session.state()[kind]) { void session.exit(); return; }
    if (dialogs.get(kind).open) void session.enter(kind);
  }

  function button(kind, id) {
    const control = document.createElement("button");
    control.id = id; control.type = "button"; control.className = "ghost-button mobile-graph-presentation-toggle";
    control.dataset.mobileGraphToggle = kind;
    return control;
  }

  function installMapActions(node) {
    if (node.nodeType !== 1) return;
    const actions = node.matches(".relationship-map-actions") ? [node] : [...node.querySelectorAll(".relationship-map-actions")];
    let changed = false;
    for (const host of actions) {
      if (host.querySelector("[data-mobile-graph-toggle]")) continue;
      const expanded = Boolean(host.closest("#relationship-map-dialog"));
      const control = button("map", expanded ? "mobile-map-presentation-toggle" : "mobile-map-presentation-open");
      if (!expanded) { control.setAttribute("aria-haspopup", "dialog"); control.setAttribute("aria-controls", map.id); }
      control.addEventListener("click", () => {
        if (!expanded) host.querySelector('[data-testid="relationship-map-expand"]')?.click();
        toggle("map");
      });
      host.append(control);
      changed = true;
    }
    if (changed) update(session.state());
  }

  const mapObserver = new MutationObserver((records) => {
    for (const record of records) for (const node of record.addedNodes) installMapActions(node);
  });
  for (const host of [document.getElementById("module-content"), document.getElementById("relationship-map-expanded-host")]) {
    if (host) { mapObserver.observe(host, { childList: true, subtree: true }); installMapActions(host); }
  }

  const galaxyToggle = button("galaxy", "mobile-galaxy-presentation-toggle");
  galaxyToggle.classList.add("mobile-galaxy-presentation-toggle");
  galaxyToggle.addEventListener("click", () => toggle("galaxy"));
  galaxy.querySelector(".galaxy-shell").append(galaxyToggle);
  const dialogObserver = new MutationObserver((records) => {
    for (const record of records) {
      const kind = record.target === map ? "map" : "galaxy";
      if (record.target.open) void session.opened(kind);
      else void session.closed(kind);
    }
  });
  for (const [kind, dialog] of dialogs) {
    dialogObserver.observe(dialog, { attributes: true, attributeFilter: ["open"] });
    dialog.addEventListener("close", () => { void session.closed(kind); });
  }
  window.addEventListener("pagehide", () => { void session.exit(); });
  update(session.state());
}
