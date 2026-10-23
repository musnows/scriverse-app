import express from "express";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { createRuntime } from "@musnows/scriverse/dist/app.js";

if (process.env.NODE_ENV !== "development" || process.env.APP_DEV_SKIP_AUTH !== "true") throw new Error("Isolated preview requires development authentication bypass");
const directory = resolve(".ai-docs/mobile-e2e");
mkdirSync(directory, { recursive: true });
const runtime = createRuntime({ databasePath: resolve(directory, "novel.db"), masterSecret: "mobile-e2e-only-master-key-at-least-32-characters", serveUi: false, revealCaptchaAnswer: true, devAuthBypass: true, disableUserAuth: true, security: { allowRegistration: true } });
if (!runtime.auth.hasUsers()) runtime.auth.register({ username: "mobile-e2e", password: "mobile-e2e-only-password", displayName: "测试作者" });
const user = runtime.auth.listUsers()[0];
let work = runtime.store.listWorks()[0];
let entities = {};
if (!work) {
  work = runtime.store.createWork({ title: "星海纪事", author: "测试作者", description: "专用安卓验收作品，所有内容均为测试数据。" }, user.userId);
  runtime.store.setWorkOfflineAccess(work.id, true);
  const volume = runtime.store.createVolume(work.id, { title: "第一卷 · 远航" });
  const chapter = runtime.store.createChapter(work.id, { volumeId: volume.id, title: "第一章 · 风起星港", content: "林舟站在星港的尽头，听见潮汐引擎缓缓苏醒。\n\n一道蓝色的光划过天际，远航即将开始。", chapterType: "正文" });
  runtime.store.createChapter(work.id, { volumeId: volume.id, title: "番外 · 灯塔守望者", content: "灯塔中保存着旧日航线。", chapterType: "番外" });
  const setting = runtime.store.createSetting(work.id, { title: "潮汐引擎", category: "科技", content: "利用星际潮汐提供动力，冷却时间为三小时。", locked: true });
  const race = runtime.store.createRace(work.id, { name: "星港人", description: "居住在星港的人类。" });
  const organization = runtime.store.createOrganization(work.id, { name: "远航议会", description: "负责远航的组织。" });
  const character = runtime.store.createCharacter(work.id, { name: "林舟", gender: "male", raceId: race.id, organizationIds: [organization.id], profile: { 简介: "年轻的领航员" } });
  const second = runtime.store.createCharacter(work.id, { name: "安澜", gender: "female", profile: { 简介: "灯塔守望者" } });
  const track = runtime.store.createTimelineTrack(work.id, { name: "远航主线" });
  const event = runtime.store.createTimelineEvent(work.id, { trackId: track.id, name: "启航", timeLabel: "星历一日", chapterIds: [chapter.id], participantIds: [character.id] });
  const relationship = runtime.store.createRelationship(work.id, { fromCharacterId: character.id, toCharacterId: second.id, category: "social", subtype: "伙伴", currentStatus: "互相信任" });
  const draft = runtime.store.createDraft(work.id, { title: "第二次潮汐", draftType: "prose", content: "第二次潮汐也许会带来新的线索。", volumeId: volume.id });
  const outline = runtime.store.upsertChapterOutline(chapter.id, { goal: "离开星港", conflict: "引擎故障", turningPoint: "发现备用核心", notes: "保留悬念", status: "draft" });
  const foreshadow = runtime.store.createForeshadow(work.id, { title: "蓝色密钥", description: "林舟的口袋里有一枚蓝色密钥。", occurrences: [{ chapterId: chapter.id, role: "setup", note: "启航前出现" }] });
  entities = { chapter: chapter.id, setting: setting.id, character: character.id, race: race.id, organization: organization.id, "timeline-track": track.id, "timeline-event": event.id, relationship: relationship.id, draft: draft.id, "chapter-outline": chapter.id, foreshadow: foreshadow.id };
} else {
  const volume = runtime.store.getWorkTree(work.id).volumes[0];
  entities.chapter = volume.chapters[0]?.id;
}
if (runtime.store.listForeshadows(work.id).length === 0) runtime.store.createForeshadow(work.id, { title: "蓝色密钥", description: "口袋里的旧密钥。" });
const chapters = runtime.store.getWorkTree(work.id).volumes.flatMap((volume) => volume.chapters);
entities = { chapter: chapters[0].id, setting: runtime.store.listSettings(work.id)[0].id, character: runtime.store.listCharacters(work.id)[0].id, race: runtime.store.listRaces(work.id)[0].id, organization: runtime.store.listOrganizations(work.id)[0].id, "timeline-track": runtime.store.listTimelineTracks(work.id)[0].id, "timeline-event": runtime.store.listTimelineEvents(work.id)[0].id, relationship: runtime.store.listRelationships(work.id)[0].id, draft: runtime.store.listDrafts(work.id)[0].id, "chapter-outline": chapters[0].id, foreshadow: runtime.store.listForeshadows(work.id)[0].id };
const app = express();
let llmCalls = 0;
let observedWorkContext = false;
let observedLockedSetting = false;
let observedOfflineToolResult = false;
app.use(express.static(resolve("dist/public"), { index: "index.html" }));
const mockLlm = (request, response) => {
  llmCalls += 1;
  const content = JSON.stringify(request.body);
  observedWorkContext ||= content.includes("星海纪事");
  observedLockedSetting ||= content.includes("潮汐引擎");
  const messages = Array.isArray(request.body.messages) ? request.body.messages : [];
  observedOfflineToolResult ||= messages.some((message) => message.role === "tool" && String(message.content).includes("星海纪事"));
  if (Array.isArray(request.body.tools) && content.includes("OFFLINE_TOOL_ACCEPTANCE") && !messages.some((message) => message.role === "tool")) {
    response.json({ choices: [{ message: { role: "assistant", content: "", tool_calls: [{ id: "offline-story-index", type: "function", function: { name: "story_index", arguments: "{}" } }] }, finish_reason: "tool_calls" }], usage: { prompt_tokens: 30, completion_tokens: 10 } });
    return;
  }
  const text = "本机模型已读取离线副本。林舟望向星港，蓝色密钥在掌心微微发亮。";
  if (request.body.stream === true) {
    response.setHeader("Content-Type", "text/event-stream");
    response.write(`data: ${JSON.stringify({ choices: [{ delta: { content: text }, index: 0 }], model: "mobile-test-model" })}\n\n`);
    response.end('data: [DONE]\n\n');
  } else if (request.path.endsWith("/responses")) response.json({ output_text: text, output: [{ type: "message", content: [{ type: "output_text", text }] }], usage: { input_tokens: 30, output_tokens: 20 } });
  else if (request.path.endsWith("/messages")) response.json({ content: [{ type: "text", text }], usage: { input_tokens: 30, output_tokens: 20 }, stop_reason: "end_turn" });
  else response.json({ choices: [{ message: { role: "assistant", content: text }, finish_reason: "stop" }], usage: { prompt_tokens: 30, completion_tokens: 20 }, model: "mobile-test-model" });
};
const llmApp = express();
const llmPaths = ["/v1/chat/completions", "/v1/responses", "/v1/messages"];
app.post(llmPaths, express.json({ limit: "8mb" }), mockLlm);
llmApp.post(llmPaths, express.json({ limit: "8mb" }), mockLlm);
const llmStatus = (_request, response) => response.json({ calls: llmCalls, observedWorkContext, observedLockedSetting, observedOfflineToolResult });
app.get("/__e2e/llm-status", llmStatus);
llmApp.get("/__e2e/llm-status", llmStatus);
app.post("/__e2e/reset-llm", (_request, response) => { llmCalls = 0; observedWorkContext = false; observedLockedSetting = false; observedOfflineToolResult = false; response.sendStatus(204); });
app.get("/api/__e2e/authorization", (request, response) => response.json({ authorizationPresent: Boolean(request.headers.authorization) }));
app.use(runtime.app);
let llmPort = Math.max(10001, Number(process.env.LLM_PORT) || 24401);
let llmServer;
let port = Math.max(10001, Number(process.env.PORT) || 24400);
function listen() {
  const server = app.listen(port, "127.0.0.1", () => {
    writeFileSync(resolve(directory, "fixture.json"), JSON.stringify({ port, llmPort, llmOrigin: `http://127.0.0.1:${llmPort}`, baseUrl: `http://127.0.0.1:${port}`, databasePath: resolve(directory, "novel.db"), userId: user.userId, workId: work.id, entities }, null, 2));
    console.log(`Isolated mobile test server listening at http://127.0.0.1:${port}`);
  });
  server.once("error", (error) => { if (error.code === "EADDRINUSE" && port < 60000) { port += 1; listen(); } else throw error; });
  for (const signal of ["SIGTERM", "SIGINT"]) process.once(signal, () => { server.closeAllConnections(); llmServer?.closeAllConnections(); llmServer?.close(); server.close(async () => { await runtime.close(); process.exit(0); }); });
}
function listenLlm() {
  llmServer = llmApp.listen(llmPort, "127.0.0.1", listen);
  llmServer.once("error", (error) => { if (error.code === "EADDRINUSE" && llmPort < 60000) { llmPort += 1; listenLlm(); } else throw error; });
}
listenLlm();
