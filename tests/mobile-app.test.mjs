import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

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
