import { cpSync, existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { join } from "node:path";

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
const launcher = readFileSync("www/index.html", "utf8");
if (launcher.includes("scriverse.top") || launcher.includes("SCRIVERSE_SERVER_URL")) throw new Error("Mobile entry must not hardcode a remote Server");
writeFileSync(join(target, "app-version.json"), `${JSON.stringify({ version: packageJson.version, serverVersion: serverPackage.version })}\n`);
writeFileSync("www/app-version.json", `${JSON.stringify({ version: packageJson.version })}\n`);
for (const file of ["app.js", "workspace.html", "desktop-offline-api.js", "desktop-sync-store.js", "vendor/vditor/dist/index.min.js"]) {
  if (!existsSync(join(target, file))) throw new Error(`Missing bundled mobile workspace asset: ${file}`);
}
const syntax = spawnSync(process.execPath, ["--check", join(target, "app.js")], { encoding: "utf8" });
if (syntax.status !== 0) throw new Error(syntax.stderr.trim());
process.stdout.write("Mobile launcher and offline workspace assets are ready\n");
