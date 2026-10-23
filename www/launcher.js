
      const PROFILE_KEY = "scriverse.mobile.server-profiles.v1";
      const ACTIVE_PROFILE_KEY = "scriverse.mobile.active-server-profile.v1";
      const MAX_PROFILES = 50;
      const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;
      const serverList = document.querySelector("#server-list");
      const serverSummary = document.querySelector("#server-summary");
      const serverForm = document.querySelector("#server-form");
      const serverEditor = document.querySelector("#server-editor");
      const serverFormTitle = document.querySelector("#server-form-title");
      const serverFormError = document.querySelector("#server-form-error");
      const serverName = document.querySelector("#server-name");
      const serverOrigin = document.querySelector("#server-origin");
      const appVersionLabel = document.querySelector("#app-version");
      let editingId = null;

      async function renderAppVersion() {
        try {
          const response = await fetch("./app-version.json", { cache: "no-store" });
          if (!response.ok) throw new Error("Could not read application version");
          const { version } = await response.json();
          if (typeof version !== "string" || !version.trim()) throw new Error("Application version is invalid");
          appVersionLabel.textContent = `App 版本 ${version}`;
        } catch {
          appVersionLabel.textContent = "App 版本不可用";
        }
      }

      function storageGet(key) {
        try { return localStorage.getItem(key); } catch { return null; }
      }

      function storageSet(key, value) {
        try { localStorage.setItem(key, value); } catch { throw new Error("无法保存工作区，请检查手机存储空间"); }
      }

      function createId() {
        if (typeof crypto?.randomUUID === "function") return crypto.randomUUID();
        const bytes = new Uint8Array(16);
        if (typeof crypto?.getRandomValues === "function") crypto.getRandomValues(bytes);
        else for (let index = 0; index < bytes.length; index += 1) bytes[index] = Math.floor(Math.random() * 256);
        bytes[6] = (bytes[6] & 0x0f) | 0x40;
        bytes[8] = (bytes[8] & 0x3f) | 0x80;
        const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
        return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
      }

      function normalizeOrigin(value) {
        const trimmed = String(value ?? "").trim();
        if (!trimmed || trimmed.length > 2_048) throw new Error("Server 地址长度无效");
        let url;
        try { url = new URL(trimmed); } catch { throw new Error("Server 地址格式无效"); }
        if (url.protocol !== "http:" && url.protocol !== "https:") throw new Error("Server 地址只允许 HTTP 或 HTTPS");
        if (url.username || url.password) throw new Error("Server 地址不能包含用户名或密码");
        if (url.pathname !== "/" || url.search || url.hash) throw new Error("Server 地址只能填写 origin，不能包含路径、查询参数或片段");
        return url.origin;
      }

      function parseProfile(value) {
        if (!value || typeof value !== "object" || Array.isArray(value)) return null;
        const id = typeof value.id === "string" && UUID_PATTERN.test(value.id) ? value.id : null;
        const name = typeof value.name === "string" ? value.name.trim() : "";
        if (!id || !name || name.length > 80) return null;
        let origin;
        try { origin = normalizeOrigin(value.origin); } catch { return null; }
        return { id, name, origin, lastUsedAt: typeof value.lastUsedAt === "string" ? value.lastUsedAt : null };
      }

      function loadProfiles() {
        let raw;
        try { raw = JSON.parse(storageGet(PROFILE_KEY) || "[]"); } catch { raw = []; }
        if (!Array.isArray(raw)) return [];
        const origins = new Set();
        return raw.map(parseProfile).filter((profile) => {
          if (!profile || origins.has(profile.origin)) return false;
          origins.add(profile.origin);
          return true;
        }).slice(0, MAX_PROFILES);
      }

      function saveProfiles(profiles) {
        storageSet(PROFILE_KEY, JSON.stringify(profiles));
      }

      function activeProfileId() {
        const value = storageGet(ACTIVE_PROFILE_KEY);
        return typeof value === "string" ? value : null;
      }

      function setActiveProfile(profile) {
        const profiles = loadProfiles().map((candidate) => candidate.id === profile.id
          ? { ...candidate, lastUsedAt: new Date().toISOString() }
          : candidate);
        saveProfiles(profiles);
        storageSet(ACTIVE_PROFILE_KEY, profile.id);
      }

      function offlineSummary(profile) {
        try {
          const summary = JSON.parse(storageGet(`scriverse.mobile.offline-summary.${profile.id}`) || "null");
          const account = JSON.parse(storageGet(`scriverse.mobile.profile-user.${profile.id}`) || "null");
          return account?.origin === profile.origin && account?.user?.userId === summary?.userId ? summary : null;
        } catch { return null; }
      }

      function connect(profile, offline = false) {
        if (offline && !(offlineSummary(profile)?.works > 0)) {
          serverSummary.textContent = "请先在线登录并下载作品，再离线进入工作区。";
          return;
        }
        setActiveProfile(profile);
        const url = new URL("/workspace.html", location.origin);
        url.searchParams.set("profileId", profile.id);
        if (offline) url.searchParams.set("offline", "1");
        url.searchParams.set("scriverseMobile", "1");
        window.location.assign(url.toString());
      }

      function text(element, value) {
        element.textContent = value;
        return element;
      }

      function renderProfiles() {
        const profiles = loadProfiles();
        const activeId = activeProfileId();
        serverList.replaceChildren();
        serverSummary.textContent = profiles.length === 0
          ? "先添加你的 Server，开始创作。"
          : `${profiles.length} 个工作区 · 选择在线或离线进入`;
        if (profiles.length === 0) {
          const empty = document.createElement("div");
          empty.className = "empty-workspaces";
          empty.append(text(document.createElement("strong"), "故事从这里开始"), text(document.createElement("p"), "添加 Server，连接你的创作世界。"));
          serverList.append(empty);
        }
        for (const profile of profiles) {
          const card = document.createElement("article");
          card.className = "server-card";
          card.dataset.profileId = profile.id;
          card.dataset.active = String(profile.id === activeId);
          const heading = document.createElement("div");
          heading.className = "server-card-heading";
          heading.append(text(document.createElement("strong"), profile.name));
          if (profile.id === activeId) {
            const active = text(document.createElement("span"), "最近使用");
            active.className = "active-label";
            heading.append(active);
          }
          const origin = text(document.createElement("code"), profile.origin);
          origin.className = "server-origin";
          const actions = document.createElement("div");
          actions.className = "server-actions";
          const connectButton = text(document.createElement("button"), "进入工作区");
          connectButton.type = "button";
          connectButton.dataset.action = "connect";
          const editButton = text(document.createElement("button"), "编辑");
          editButton.type = "button";
          editButton.className = "secondary";
          editButton.dataset.action = "edit";
          const deleteButton = text(document.createElement("button"), "删除");
          deleteButton.type = "button";
          deleteButton.className = "danger";
          deleteButton.dataset.action = "delete";
          const offlineButton = text(document.createElement("button"), "离线进入");
          offlineButton.type = "button";
          offlineButton.className = "secondary";
          offlineButton.dataset.action = "offline";
          const summary = offlineSummary(profile);
          offlineButton.disabled = !(summary?.works > 0);
          offlineButton.title = summary?.works > 0 ? "使用手机本地副本，不连接 Server" : "请先在线登录并下载作品";
          const cache = document.createElement("span");
          cache.className = "workspace-cache-status";
          cache.dataset.ready = String(summary?.works > 0);
          cache.textContent = summary?.works > 0 ? `${summary.works} 部作品可离线使用${summary.pending > 0 ? ` · ${summary.pending} 项待同步` : ""}${summary.conflicts > 0 ? ` · ${summary.conflicts} 项冲突` : ""}` : "尚未下载离线副本";
          const tools = document.createElement("div");
          tools.className = "workspace-card-tools";
          editButton.textContent = "编辑连接";
          deleteButton.textContent = "移除工作区";
          tools.append(editButton, deleteButton);
          actions.append(connectButton, offlineButton);
          card.append(heading, origin, cache, actions, tools);
          serverList.append(card);
        }
        serverList.setAttribute("aria-busy", "false");
      }

      function openForm(profile = null) {
        editingId = profile?.id ?? null;
        serverFormTitle.textContent = profile ? "编辑工作区" : "添加工作区";
        serverName.value = profile?.name ?? "";
        serverOrigin.value = profile?.origin ?? "";
        serverFormError.hidden = true;
        if (!serverEditor.open) serverEditor.showModal();
        serverName.focus();
      }

      function closeForm() {
        editingId = null;
        serverForm.reset();
        serverFormError.hidden = true;
        serverEditor.close();
      }

      document.querySelector("#add-server").addEventListener("click", () => openForm());
      document.querySelector("#cancel-server").addEventListener("click", closeForm);
      serverList.addEventListener("click", (event) => {
        try {
        const button = event.target.closest("button[data-action]");
        if (!button) return;
        const profile = loadProfiles().find((candidate) => candidate.id === button.closest("[data-profile-id]")?.dataset.profileId);
        if (!profile) return;
        if (button.dataset.action === "connect") {
          connect(profile);
        } else if (button.dataset.action === "offline") {
          connect(profile, true);
        } else if (button.dataset.action === "edit") {
          openForm(profile);
        } else if (button.dataset.action === "delete" && globalThis.confirm(`删除“${profile.name}”的 Server 配置？`)) {
          saveProfiles(loadProfiles().filter((candidate) => candidate.id !== profile.id));
          if (activeProfileId() === profile.id) storageSet(ACTIVE_PROFILE_KEY, "");
          renderProfiles();
        }
        } catch (error) { serverSummary.textContent = error.message; }
      });
      serverEditor.addEventListener("cancel", closeForm);
      serverForm.addEventListener("submit", (event) => {
        event.preventDefault();
        serverFormError.hidden = true;
        try {
          const name = serverName.value.trim();
          if (!name || name.length > 80) throw new Error("显示名称长度必须在 1 到 80 个字符之间");
          const origin = normalizeOrigin(serverOrigin.value);
          const profiles = loadProfiles();
          const duplicate = profiles.find((profile) => profile.origin === origin && profile.id !== editingId);
          if (duplicate) throw new Error("该 Server 已经存在");
          if (!editingId && profiles.length >= MAX_PROFILES) throw new Error("保存的 Server 数量已达到上限");
          const profile = editingId
            ? { ...profiles.find((candidate) => candidate.id === editingId), name, origin }
            : { id: createId(), name, origin, lastUsedAt: null };
          const next = editingId
            ? profiles.map((candidate) => candidate.id === editingId ? profile : candidate)
            : [...profiles, profile];
          saveProfiles(next);
          connect(profile);
        } catch (error) {
          serverFormError.textContent = error instanceof Error ? error.message : "Server 配置无效";
          serverFormError.hidden = false;
        }
      });

      document.querySelector("#local-ai-open").addEventListener("click", () => location.assign("/local-ai/index.html?from=launcher"));
      renderProfiles();
      void renderAppVersion();
      if (new URL(location.href).searchParams.get("changeServer") === "1") openForm();
    