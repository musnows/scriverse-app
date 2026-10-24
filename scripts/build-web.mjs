import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { join } from "node:path";
import { build } from "esbuild";

function filesIn(directory) {
  return readdirSync(directory, { withFileTypes: true }).sort((left, right) => left.name.localeCompare(right.name)).flatMap((entry) => (
    entry.isDirectory() ? filesIn(join(directory, entry.name)) : [join(directory, entry.name)]
  ));
}

const packageJson = JSON.parse(readFileSync("package.json", "utf8"));
const runtimePackage = "node_modules/@musnows/scriverse";
const serverPackage = JSON.parse(readFileSync(join(runtimePackage, "package.json"), "utf8"));
if (serverPackage.version !== packageJson.scriverseServerVersion) throw new Error("Bundled Server version does not match the app contract");
if (typeof packageJson.version !== "string" || !packageJson.version.trim()) throw new Error("The mobile app package version is missing");
const target = "dist/public";
mkdirSync(target, { recursive: true });
cpSync(join(runtimePackage, "dist/public"), target, { recursive: true });
const applied = spawnSync("git", ["apply", "--recount", "--directory=dist", "runtime-overlay/web.patch"], { encoding: "utf8" });
if (applied.status !== 0) throw new Error(`Mobile workspace overlay failed: ${applied.stderr.trim()}`);
cpSync("runtime-overlay/public", target, { recursive: true });
renameSync(join(target, "index.html"), join(target, "workspace.html"));
cpSync("www", target, { recursive: true });
await build({ entryPoints: ["src/native-bootstrap.js"], outfile: join(target, "native-bootstrap.js"), bundle: true, external: ["/desktop-sync-store.js"], format: "iife", target: "es2022", define: { __SERVER_VERSION__: JSON.stringify(serverPackage.version) } });
await build({ entryPoints: ["src/local-ai-store.js"], outfile: join(target, "local-ai-engine.js"), bundle: true, format: "esm", target: "es2022" });
await build({ entryPoints: ["src/app-appearance.js"], outfile: join(target, "app-appearance.js"), bundle: true, format: "iife", target: "es2022" });
let workspace = readFileSync(join(target, "workspace.html"), "utf8");
workspace = workspace.replace("<head>", `<head>\n    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; base-uri 'none'; connect-src 'self'; font-src 'self'; form-action 'self'; img-src 'self' data: blob:; media-src 'none'; object-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; worker-src 'self'">\n    <script src="/native-bootstrap.js?v=20261023-native-workspace-v1"></script>`);
workspace = workspace.replaceAll("Desktop 安全副本", "手机本地副本").replace('    <script src="/native-bootstrap.js', '    <script src="/app-appearance.js?v=20261025-app-font-presets-v2"></script>\n    <script src="/native-bootstrap.js').replace("</head>", '    <link rel="stylesheet" href="/mobile-ui.css?v=20261023-phone-navigation-v1">\n    <link rel="stylesheet" href="/app-appearance.css?v=20261025-app-font-presets-v2">\n  </head>');
writeFileSync(join(target, "workspace.html"), workspace);
const bundledAppPath = join(target, "app.js");
let bundledApp = readFileSync(bundledAppPath, "utf8");
bundledApp = bundledApp.replace('root.style.setProperty("--font-latin", latinFontStacks[normalized.latinFont]);', 'root.style.setProperty("--font-latin", `${latinFontStacks[normalized.latinFont]}, "Scriverse Mono"`);');
bundledApp = bundledApp.replace('preview.style.fontSize = `${settings.fontSize}px`;', 'preview.style.fontSize = `calc(${settings.fontSize}px * var(--app-font-scale, 0.9))`;');
for (const [before, after] of [["离线时沿用 Server 页面；已下载的正文和设定可编辑，其他功能恢复连接后可用。", "已下载的正文与设定库均可编辑。新建、删除和云端管理操作需重新在线进入；本机 AI 可继续使用。"], ["Desktop ·", "手机 ·"], ["Desktop AI 操作失败", "手机 AI 操作失败"], ["Desktop 离线访问", "App 离线访问"], ["Desktop 用户才能", "App 用户才能"], ["Desktop 同步冲突", "手机同步冲突"]]) bundledApp = bundledApp.replaceAll(before, after);
writeFileSync(bundledAppPath, bundledApp);
writeFileSync(join(target, "mobile-app-runtime.js"), "export const mobileOfflineRuntime = { enabled: false, offline: false, setSession() {}, async getOfflineSession() { return null; } };\n");
const launcher = readFileSync("www/index.html", "utf8");
if (launcher.includes("scriverse.top") || launcher.includes("SCRIVERSE_SERVER_URL")) throw new Error("Mobile entry must not hardcode a remote Server");
const buildHash = createHash("sha256");
for (const file of ["package.json", "package-lock.json", "runtime-overlay/web.patch", ...filesIn("src"), ...filesIn("www"), ...filesIn("runtime-overlay/public")]) {
  if (file.endsWith("app-version.json")) continue;
  buildHash.update(file).update("\0").update(readFileSync(file)).update("\0");
}
const assetVersion = buildHash.digest("hex").slice(0, 16);
for (const file of filesIn(target)) {
  if (!/\.(?:html|js|css)$/u.test(file) || file.includes("/vendor/")) continue;
  const source = readFileSync(file, "utf8");
  const versioned = source.replace(/(["'`])((?:\/(?!\/)|\.\.?\/)[^"'`\s<>]*?\.(?:m?js|css|svg|woff2)(?:\?[^"'`\s<>]*)?)\1/gu, (match, quote, url) => {
    if (url.startsWith("/api/")) return match;
    return `${quote}${url}${url.includes("?") ? "&" : "?"}mobileBuild=${assetVersion}${quote}`;
  });
  writeFileSync(file, versioned);
}
writeFileSync(join(target, "app-version.json"), `${JSON.stringify({ version: packageJson.version, serverVersion: serverPackage.version, build: assetVersion })}\n`);
writeFileSync("www/app-version.json", `${JSON.stringify({ version: packageJson.version })}\n`);
for (const file of ["app.js", "workspace.html", "desktop-offline-api.js", "desktop-sync-store.js", "vendor/vditor/dist/index.min.js"]) {
  if (!existsSync(join(target, file))) throw new Error(`Missing bundled mobile workspace asset: ${file}`);
}
const syntax = spawnSync(process.execPath, ["--check", join(target, "app.js")], { encoding: "utf8" });
if (syntax.status !== 0) throw new Error(syntax.stderr.trim());
process.stdout.write("Mobile launcher and offline workspace assets are ready\n");
