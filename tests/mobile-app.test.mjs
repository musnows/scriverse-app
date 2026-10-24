import test from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

test("mobile launcher preserves cloud connection and multi-Server management", async () => {
  const html = await readFile(new URL("../www/index.html", import.meta.url), "utf8");
  const launcher = await readFile(new URL("../www/launcher.js", import.meta.url), "utf8");
  assert.match(launcher, /searchParams\.set\("scriverseMobile", "1"\)/u);
  assert.match(launcher, /scriverse\.mobile\.server-profiles\.v1/u);
  assert.match(html, /保存并连接/u);
  assert.doesNotMatch(html, /scriverse\.top/u);
  assert.doesNotMatch(html, /SCRIVERSE_SERVER_URL/u);
  assert.match(html, /src="\/launcher\.js\?v=/u);
  assert.doesNotThrow(() => new Function(launcher));
});

test("Capacitor package leaves the remote Server choice to the launcher", async () => {
  const config = await readFile(new URL("../capacitor.config.ts", import.meta.url), "utf8");
  assert.doesNotMatch(config, /allowNavigation:\s*\["\*"\]/u);
  assert.match(config, /webDir: "dist\/public"/u);
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
  const launcher = await readFile(new URL("../www/launcher.js", import.meta.url), "utf8");
  assert.match(launcher, /fetch\("\.\/app-version\.json"/u);
  assert.match(gradle, /project\.findProperty\('appVersionName'\) \?: packageVersion/u);
});

test("every app surface and the offline module graph share a content cache version", async () => {
  const generated = JSON.parse(await readFile(new URL("../dist/public/app-version.json", import.meta.url), "utf8"));
  assert.match(generated.build, /^[a-f0-9]{16}$/u);
  for (const path of ["index.html", "workspace.html", "local-ai/index.html", "app.js", "native-bootstrap.js"]) {
    const source = await readFile(new URL(`../dist/public/${path}`, import.meta.url), "utf8");
    assert.ok(source.includes(`mobileBuild=${generated.build}`), path);
  }
  const font = await readFile(new URL("../dist/public/fonts/scriverse-mono-latin.woff2", import.meta.url));
  assert.equal(font.subarray(0, 4).toString("ascii"), "wOF2");
});

test("the app security policy is active before appearance scripts execute on every entry page", async () => {
  for (const path of ["index.html", "workspace.html", "local-ai/index.html"]) {
    const html = await readFile(new URL(`../dist/public/${path}`, import.meta.url), "utf8");
    const policy = html.indexOf('http-equiv="Content-Security-Policy"');
    const script = html.indexOf('src="/app-appearance.js');
    assert.ok(policy >= 0 && script > policy, path);
    assert.match(html, /script-src 'self'/u);
  }
});
