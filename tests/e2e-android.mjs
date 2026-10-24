import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const device = process.env.ANDROID_SERIAL ?? "emulator-5554";
if (!/^emulator-\d+$/u.test(device)) throw new Error("Native E2E requires an Android emulator");
const packageName = "top.scriverse.app.e2e";
const directory = resolve(".ai-docs/mobile-e2e/android");
mkdirSync(directory, { recursive: true });
const fixture = JSON.parse(readFileSync(resolve(".ai-docs/mobile-e2e/fixture.json"), "utf8"));
const auth = JSON.parse(readFileSync(resolve(".ai-docs/mobile-e2e-auth/fixture.json"), "utf8"));
assert.equal(fixture.databasePath, resolve(".ai-docs/mobile-e2e/novel.db"));
assert.equal(auth.databasePath, resolve(".ai-docs/mobile-e2e-auth/novel.db"));
for (const origin of [fixture.baseUrl, fixture.llmOrigin, auth.baseUrl]) {
  const url = new URL(origin);
  assert.equal(url.hostname, "127.0.0.1");
  assert.ok(Number(url.port) >= 10001);
}
for (const origin of [fixture.baseUrl, auth.baseUrl]) assert.equal((await fetch(`${origin}/api/health`)).status, 200);

function run(command, args, { capture = false } = {}) {
  const result = spawnSync(command, args, { encoding: "utf8", stdio: capture ? "pipe" : "inherit", maxBuffer: 8 * 1024 * 1024 });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `${command} failed: ${result.stderr ?? ""}`);
  return result.stdout ?? "";
}
const adb = (...args) => run(process.env.ADB ?? "adb", ["-s", device, ...args], { capture: true });
const ports = [...new Set([fixture.port, fixture.llmPort, auth.port])];
const existingForwards = adb("reverse", "--list");
const ownedForwards = ports.filter((port) => !existingForwards.split("\n").some((line) => line.includes(`tcp:${port} `)));
const instrumentationArgs = ["-e", "serverOrigin", fixture.baseUrl, "-e", "llmOrigin", fixture.llmOrigin, "-e", "authOrigin", auth.baseUrl];
const phases = [];
function instrument(className, label) {
  console.log(`Checking ${label}`);
  const output = adb("shell", "am", "instrument", "-w", "-e", "class", className, ...instrumentationArgs, `${packageName}.test/androidx.test.runner.AndroidJUnitRunner`);
  writeFileSync(resolve(directory, `${label}.log`), output);
  assert.match(output, /OK \(\d+ tests?\)/u, output);
  assert.doesNotMatch(output, /FAILURES|INSTRUMENTATION_FAILED|Process crashed/u);
  phases.push(label);
}
function workflow(method) { instrument(`top.scriverse.app.MobileWorkflowTest#${method}`, method); }

try {
  run("npm", ["run", "cap:sync"]);
  run("./android/gradlew", ["-p", "android", "-PandroidE2e=true", ":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain"]);
  console.log(adb("install", "-r", "android/app/build/outputs/apk/debug/app-debug.apk").trim());
  console.log(adb("install", "-r", "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk").trim());
  for (const port of ports) adb("reverse", `tcp:${port}`, `tcp:${port}`);
  instrument("top.scriverse.app.NativeWorkspaceTest,com.getcapacitor.myapp.ExampleInstrumentedTest", "nativeSecurityAndAuthentication");
  workflow("prepareOfflineEdit");
  adb("shell", "am", "force-stop", packageName);
  adb("reverse", "--remove", `tcp:${fixture.port}`);
  workflow("verifyOfflinePersistence");
  adb("reverse", `tcp:${fixture.port}`, `tcp:${fixture.port}`);
  workflow("verifyPersistenceAndSync");
  const preferences = adb("shell", "run-as", packageName, "cat", "shared_prefs/e2e-workflow.xml");
  const marker = preferences.match(/<string name="marker">([^<]+)<\/string>/u)?.[1];
  assert.ok(marker, "Native offline marker is missing");
  const chapter = (await (await fetch(`${fixture.baseUrl}/api/chapters/${fixture.entities.chapter}`)).json()).data;
  assert.ok(chapter.content.includes(marker), "Server did not persist the native edit");
  workflow("configurePhoneModel");
  assert.equal((await fetch(`${fixture.baseUrl}/__e2e/reset-llm`, { method: "POST" })).status, 204);
  adb("reverse", "--remove", `tcp:${fixture.port}`);
  workflow("offlinePhoneModelChat");
  const llm = await (await fetch(`${fixture.llmOrigin}/__e2e/llm-status`)).json();
  assert.ok(llm.calls >= 2, "The native chat did not complete its offline tool round trip");
  for (const property of ["observedWorkContext", "observedLockedSetting", "observedOfflineToolResult"]) assert.equal(llm[property], true, property);
  workflow("libraryNavigationAndNativeBack");
  workflow("globalFontSizingAcrossPages");
  adb("shell", "am", "force-stop", packageName);
  workflow("globalFontSizeSurvivesRestart");
  for (const name of ["android-offline-editor", "android-offline-restored", "android-online-synced", "android-local-ai-config", "android-offline-ai-chat", "android-characters", "android-font-markdown-large", "android-font-settings-large", "android-font-settings-default"]) {
    adb("pull", `/sdcard/Android/data/${packageName}/files/${name}.png`, resolve(directory, `${name}.png`));
  }
  const result = { result: "passed", device, packageName, phases, uploadedChapterVersion: chapter.versionNo, offlineModel: llm };
  writeFileSync(resolve(directory, "result.json"), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
} finally {
  adb("shell", "am", "force-stop", packageName);
  for (const port of ports) {
    const exists = adb("reverse", "--list").includes(`tcp:${port} `);
    if (ownedForwards.includes(port) && exists) adb("reverse", "--remove", `tcp:${port}`);
    if (!ownedForwards.includes(port) && !exists) adb("reverse", `tcp:${port}`, `tcp:${port}`);
  }
}
