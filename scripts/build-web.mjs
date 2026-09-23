import { existsSync, readFileSync, writeFileSync } from "node:fs";

const requiredFiles = ["www/index.html", "www/icon.svg", "www/site.webmanifest"];
for (const file of requiredFiles) {
  if (!existsSync(file)) throw new Error(`Missing mobile web asset: ${file}`);
}

const index = readFileSync("www/index.html", "utf8");
const packageJson = JSON.parse(readFileSync("package.json", "utf8"));
if (typeof packageJson.version !== "string" || !packageJson.version.trim()) {
  throw new Error("The mobile app package version is missing");
}
if (!index.includes('searchParams.set("scriverseMobile", "1")')) {
  throw new Error("Mobile entry must preserve the Scriverse mobile query marker");
}
if (index.includes("scriverse.top") || index.includes("SCRIVERSE_SERVER_URL")) {
  throw new Error("Mobile entry must not hardcode a remote Server or depend on a build-time Server environment variable");
}

writeFileSync("www/app-version.json", `${JSON.stringify({ version: packageJson.version })}\n`);
process.stdout.write("Mobile web assets are ready\n");
