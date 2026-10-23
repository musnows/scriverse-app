import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { IDBFactory, IDBKeyRange } from "fake-indexeddb";
import { DesktopSyncStore } from "../runtime-overlay/public/desktop-sync-store.js";
import { DesktopOfflineApi } from "../runtime-overlay/public/desktop-offline-api.js";
import { syncMutationSnapshot } from "../runtime-overlay/public/desktop-sync-client.js";

globalThis.IDBKeyRange = IDBKeyRange;
const types = ["chapter", "setting", "draft", "character", "race", "organization", "timeline-track", "timeline-event", "relationship", "chapter-outline", "foreshadow"];
async function fixture() {
  const store = new DesktopSyncStore({ profileId: crypto.randomUUID(), userId: crypto.randomUUID(), indexedDBImpl: new IDBFactory() });
  await store.replaceSnapshot({ workId: "work-1", cutoffCursor: 7, items: [
    { entityType: "work", entityId: "work-1", data: { id: "work-1", title: "星海" } },
    ...types.map((entityType) => ({ entityType, entityId: entityType + "-1", versionNo: 3, data: { id: entityType + "-1", title: "初稿", name: "初稿", content: "旧内容", versionNo: 3, workId: "work-1", chapterType: "番外" } }))
  ] });
  return store;
}

for (const type of types) test(`offline ${type} edits persist with their original Server version`, async () => {
  const store = await fixture();
  const api = new DesktopOfflineApi({ store, client: { emitStatus: async () => undefined } });
  await api.saveEntity(type, type + "-1", { title: "手机修改", name: "手机修改", content: "手机修改", expectedVersionNo: 3 });
  store.close();
  const restored = await store.getEntity("work-1", type, type + "-1");
  assert.equal(restored.snapshot.content, "手机修改");
  assert.equal(restored.serverVersionNo, 3);
  const batch = await store.preparePushBatch("work-1");
  assert.equal(batch.mutations.length, 1);
  assert.equal(batch.mutations[0].baseVersionNo, 3);
  assert.equal(batch.mutations[0].entityType, type);
  assert.doesNotThrow(() => syncMutationSnapshot(type, batch.mutations[0].localSnapshot));
  store.close();
});

test("retry keeps a mutation identity and an acknowledged save preserves later edits", async () => {
  const store = await fixture();
  await store.saveLocalEntity("work-1", "chapter", "chapter-1", { title: "版本一", content: "版本一" });
  const first = await store.preparePushBatch("work-1");
  await store.returnSyncingToPending(first.mutationIds, null);
  const retry = await store.preparePushBatch("work-1");
  assert.equal(retry.mutations[0].mutationId, first.mutations[0].mutationId);
  await store.saveLocalEntity("work-1", "chapter", "chapter-1", { title: "版本二", content: "版本二" });
  await store.applyPushResults("work-1", [{ mutationId: first.mutationIds[0], status: "applied", appliedVersionNo: 4, serverSnapshot: { id: "chapter-1", title: "版本一", content: "版本一", versionNo: 4 } }]);
  assert.equal((await store.getEntity("work-1", "chapter", "chapter-1")).snapshot.content, "版本二");
  const next = await store.preparePushBatch("work-1");
  assert.equal(next.mutations[0].baseVersionNo, 4);
  store.close();
});

test("conflict resolution creates a new mutation based on the current Server version", async () => {
  const store = await fixture();
  await store.saveLocalEntity("work-1", "character", "character-1", { id: "character-1", name: "本机" });
  const batch = await store.preparePushBatch("work-1");
  await store.applyPushResults("work-1", [{ mutationId: batch.mutationIds[0], status: "conflict", baseVersionNo: 3, conflictVersionNo: 5, baseSnapshot: { name: "初稿" }, localSnapshot: { name: "本机" }, serverSnapshot: { id: "character-1", name: "线上", versionNo: 5 } }]);
  assert.equal((await store.statusSummary("work-1")).conflicts, 1);
  await store.resolveConflict(batch.mutationIds[0], { id: "character-1", name: "合并" });
  const merged = await store.preparePushBatch("work-1");
  assert.notEqual(merged.mutations[0].mutationId, batch.mutationIds[0]);
  assert.equal(merged.mutations[0].baseVersionNo, 5);
  assert.equal(merged.mutations[0].localSnapshot.name, "合并");
  store.close();
});

test("refresh cannot overwrite unsynchronized local changes", async () => {
  const store = await fixture();
  await store.saveLocalEntity("work-1", "chapter", "chapter-1", { title: "保留", content: "不能覆盖" });
  await assert.rejects(store.replaceSnapshot({ workId: "work-1", items: [] }), { code: "SYNC_SNAPSHOT_DIRTY" });
  assert.equal(syncMutationSnapshot("chapter", { chapterType: "番外" }).chapterType, "番外");
  store.close();
});

test("bundled workspace includes every startup control and editor assets", async () => {
  const html = await readFile(new URL("../dist/public/workspace.html", import.meta.url), "utf8");
  for (const id of ["desktop-sync-menu-button", "desktop-sync-button", "desktop-switch-button", "desktop-sync-status-button", "desktop-sync-dialog", "desktop-conflict-dialog", "desktop-network-guidance-toast"]) assert.ok(html.includes(`id="${id}"`), id);
  assert.ok((await readFile(new URL("../dist/public/vendor/vditor/dist/index.min.js", import.meta.url), "utf8")).length > 1000);
});

test("forced offline workspaces never poll or upload when the device is online", async () => {
  const { createDesktopSyncClient } = await import("../runtime-overlay/public/desktop-sync-client.js");
  const previous = globalThis.indexedDB;
  globalThis.indexedDB = new IDBFactory();
  let requests = 0;
  const client = await createDesktopSyncClient({ user: { userId: crypto.randomUUID() }, bridge: { shell: { getCapabilities: async () => ({ ok: true, data: { profileId: crypto.randomUUID(), connectionMode: "offline", capabilities: { syncProtocol: { min: 1, max: 1 } } } }) } }, fetchImpl: async () => { requests += 1; throw new Error("Unexpected online request"); } });
  assert.deepEqual(await client.syncAll(), []);
  assert.equal(requests, 0);
  client.dispose();
  globalThis.indexedDB = previous;
});

test("an outline without a Server version cannot block valid pending edits", async () => {
  const store = await fixture();
  await store.replaceSnapshot({ workId: "empty-outline-work", items: [
    { entityType: "work", entityId: "empty-outline-work", data: { id: "empty-outline-work", title: "未建大纲" } },
    { entityType: "chapter-outline", entityId: "chapter-without-outline", versionNo: 0, data: { id: "chapter-without-outline", chapterId: "chapter-without-outline", versionNo: 0, goal: "", createdAt: null } }
  ] });
  await assert.rejects(store.saveLocalEntity("empty-outline-work", "chapter-outline", "chapter-without-outline", { goal: "尚未在线创建" }), { code: "SYNC_ENTITY_NOT_CREATED" });
  assert.equal((await store.statusSummary("empty-outline-work")).pending, 0);
  await store.saveLocalEntity("work-1", "chapter", "chapter-1", { title: "正常版本", content: "可以同步" });
  assert.equal((await store.preparePushBatch("work-1")).mutations.length, 1);
  store.close();
});

test("downloads use a matching authoritative outline body and version when the package omits its version", async () => {
  const { DesktopSyncClient } = await import("../runtime-overlay/public/desktop-sync-client.js");
  let stored = null;
  const client = new DesktopSyncClient({ store: { replaceSnapshot: async (value) => { stored = value; return value; }, close() {} }, user: {}, bridge: {}, pollIntervalMs: 0 });
  client.request = async (path, options) => {
    if (options?.method === "DELETE") return null;
    if (path.endsWith("/snapshots")) return { snapshotId: "snapshot-1", cutoffCursor: 4 };
    if (path.includes("/items?")) return { items: [{ entityType: "chapter-outline", entityId: "chapter-1", versionNo: 0, data: { goal: "下载开始时的内容" } }], hasMore: false };
    if (path.endsWith("/outline")) return { chapterId: "chapter-1", goal: "完整权威内容", versionNo: 6 };
    throw new Error("Unexpected test request");
  };
  client.emitStatus = async () => undefined;
  client.refreshOfflineAgentCorpus = async () => undefined;
  await client.downloadWork({ id: "work-1", offlineAccessEnabled: true });
  assert.equal(stored.items[0].versionNo, 6);
  assert.equal(stored.items[0].data.versionNo, 6);
  assert.equal(stored.items[0].data.goal, "完整权威内容");
  client.dispose();
});
