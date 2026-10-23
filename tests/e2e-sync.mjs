import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { IDBFactory, IDBKeyRange } from "fake-indexeddb";
import { DesktopSyncStore } from "../runtime-overlay/public/desktop-sync-store.js";
import { DesktopSyncClient } from "../runtime-overlay/public/desktop-sync-client.js";

const fixture = JSON.parse(await readFile(new URL("../.ai-docs/mobile-e2e/fixture.json", import.meta.url), "utf8"));
const baseUrl = process.env.E2E_BASE_URL ?? fixture.baseUrl;
if (!/^http:\/\/127\.0\.0\.1:\d{5}$/u.test(baseUrl)) throw new Error("Real sync E2E requires the isolated loopback fixture");
globalThis.IDBKeyRange = IDBKeyRange;
const store = new DesktopSyncStore({ profileId: crypto.randomUUID(), userId: fixture.userId, indexedDBImpl: new IDBFactory() });
const client = new DesktopSyncClient({ bridge: { shell: { reportLeaveState: async () => ({ ok: true }) } }, profile: { connectionMode: "online" }, user: { userId: fixture.userId }, store, fetchImpl: async (path, init) => { const response = await fetch(new URL(path, baseUrl), init); if (response.status === 400) console.log(JSON.stringify((await response.clone().json()).error, null, 2)); return response; }, pollIntervalMs: 0 });
const cases = [
  ["chapter", "chapters", "content"], ["setting", "settings", "content"], ["draft", "drafts", "content"], ["character", "characters", "code"], ["race", "races", "description"], ["organization", "organizations", "description"], ["timeline-track", "timeline-tracks", "description"], ["timeline-event", "timeline", "description"], ["relationship", "relationships", "currentStatus"], ["chapter-outline", "chapters", "notes"], ["foreshadow", "foreshadows", "description"]
];
const pathFor = (type, resource, id) => `/api/${resource}/${encodeURIComponent(id)}${type === "chapter-outline" ? "/outline" : ""}`;
const marker = `phone-e2e-${Date.now()}`;
const verified = [];
try {
  await client.downloadWork(await client.request(`/api/works/${fixture.workId}`));
  const records = [];
  for (const [type, resource, field] of cases) {
    const entity = (await store.listEntities(fixture.workId, type)).find((item) => item.serverVersionNo > 0);
    assert.ok(entity, `Missing downloaded ${type}`);
    const value = field === "content" ? `${entity.snapshot[field] ?? ""}\n${marker}` : marker;
    await store.saveLocalEntity(fixture.workId, type, entity.entityId, { ...entity.snapshot, [field]: value });
    records.push({ type, resource, field, id: entity.entityId, version: entity.serverVersionNo, value });
  }
  const uploaded = await client.syncWork(fixture.workId);
  assert.equal(uploaded.summary.pending + uploaded.summary.syncing + uploaded.summary.conflicts + uploaded.summary.rejected, 0);
  for (const record of records) {
    const remote = await client.request(pathFor(record.type, record.resource, record.id));
    assert.equal(remote[record.field], record.value, `Server did not persist ${record.type}`);
    assert.equal(remote.versionNo, record.version + 1, `Server version did not advance for ${record.type}`);
    verified.push({ module: record.type, serverVersion: remote.versionNo });
  }
  const character = records.find((item) => item.type === "character");
  const local = await store.getEntity(fixture.workId, "character", character.id);
  await store.saveLocalEntity(fixture.workId, "character", character.id, { ...local.snapshot, code: "phone-conflict" });
  await client.request(pathFor("character", "characters", character.id), { method: "PATCH", body: { code: "server-conflict", expectedVersionNo: local.serverVersionNo } });
  const conflicted = await client.syncWork(fixture.workId);
  assert.equal(conflicted.summary.conflicts, 1);
  const [conflict] = await store.listConflicts(fixture.workId);
  assert.equal(conflict.localSnapshot.code, "phone-conflict");
  assert.equal(conflict.serverSnapshot.code, "server-conflict");
  await store.resolveConflict(conflict.mutationId, { ...conflict.serverSnapshot, code: "phone-server-merged" });
  const resolved = await client.syncWork(fixture.workId);
  assert.equal(resolved.summary.conflicts + resolved.summary.pending, 0);
  const merged = await client.request(pathFor("character", "characters", character.id));
  assert.equal(merged.code, "phone-server-merged");
  assert.equal(merged.versionNo, local.serverVersionNo + 2);
  const draft = records.find((item) => item.type === "draft");
  const cachedDraft = await store.getEntity(fixture.workId, "draft", draft.id);
  await store.saveLocalEntity(fixture.workId, "draft", draft.id, { ...cachedDraft.snapshot, content: "retained-after-access-disabled" });
  await client.request(`/api/works/${fixture.workId}/offline-access`, { method: "PATCH", body: { enabled: false } });
  try {
    await assert.rejects(client.syncWork(fixture.workId), { code: "OFFLINE_ACCESS_DISABLED" });
    assert.equal((await store.getEntity(fixture.workId, "draft", draft.id)).snapshot.content, "retained-after-access-disabled");
    assert.notEqual((await client.request(pathFor("draft", "drafts", draft.id))).content, "retained-after-access-disabled");
  } finally { await client.request(`/api/works/${fixture.workId}/offline-access`, { method: "PATCH", body: { enabled: true } }); }
  await client.syncWork(fixture.workId);
  console.log(JSON.stringify({ result: "passed", modules: verified, conflictResolvedAtVersion: merged.versionNo, permissionRevocationPreservedLocalEdits: true, databasePath: fixture.databasePath }, null, 2));
} finally { client.dispose(); }
