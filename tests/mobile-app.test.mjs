import test from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

test("mobile launcher preserves cloud connection and multi-Server management", async () => {
  const html = await readFile(new URL("../www/index.html", import.meta.url), "utf8");
  assert.match(html, /searchParams\.set\("scriverseMobile", "1"\)/u);
  assert.match(html, /scriverse\.mobile\.server-profiles\.v1/u);
  assert.match(html, /保存并连接/u);
  assert.doesNotMatch(html, /scriverse\.top/u);
  assert.doesNotMatch(html, /SCRIVERSE_SERVER_URL/u);
  const inlineScript = html.match(/<script type="module">([\s\S]*?)<\/script>/u)?.[1];
  assert.ok(inlineScript);
  assert.doesNotThrow(() => new Function(inlineScript));
});

test("Capacitor package leaves the remote Server choice to the launcher", async () => {
  const config = await readFile(new URL("../capacitor.config.ts", import.meta.url), "utf8");
  assert.match(config, /allowNavigation:\s*\["\*"\]/u);
  assert.doesNotMatch(config, /server:\s*\{[^}]*url:/su);
  assert.doesNotMatch(config, /SCRIVERSE_SERVER_URL|scriverse\.top/u);
});

test("launcher reads and displays the package version generated for the app", async () => {
  const repositoryRoot = fileURLToPath(new URL("../", import.meta.url));
  execFileSync(process.execPath, ["scripts/build-web.mjs"], { cwd: repositoryRoot });
  const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
  const versionData = JSON.parse(await readFile(new URL("../www/app-version.json", import.meta.url), "utf8"));
  const html = await readFile(new URL("../www/index.html", import.meta.url), "utf8");
  const gradle = await readFile(new URL("../android/app/build.gradle", import.meta.url), "utf8");
  assert.equal(versionData.version, packageJson.version);
  assert.match(html, /id="app-version"/u);
  assert.match(html, /fetch\("\.\/app-version\.json"/u);
  assert.match(gradle, /project\.findProperty\('appVersionName'\) \?: packageVersion/u);
});
