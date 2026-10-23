import { createRuntime } from "@musnows/scriverse/dist/app.js";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const directory = resolve(".ai-docs/mobile-e2e-auth");
mkdirSync(directory, { recursive: true });
const runtime = createRuntime({ databasePath: resolve(directory, "novel.db"), masterSecret: "native-auth-e2e-only-master-secret-over-32-characters", serveUi: false, revealCaptchaAnswer: true, security: { allowRegistration: true } });
if (!runtime.auth.hasUsers()) runtime.auth.register({ username: "native-auth", password: "native-auth-only-password" });
const user = runtime.auth.listUsers()[0];
if (runtime.store.listWorks().length === 0) runtime.store.createWork({ title: "Authenticated Native Workspace" }, user.userId);
let port = Math.max(10001, Number(process.env.PORT) || 24500);
function listen() {
  const server = runtime.app.listen(port, "127.0.0.1", () => {
    writeFileSync(resolve(directory, "fixture.json"), JSON.stringify({ port, baseUrl: `http://127.0.0.1:${port}`, userId: user.userId, databasePath: resolve(directory, "novel.db") }));
    console.log(`Isolated native authentication server listening at http://127.0.0.1:${port}`);
  });
  server.once("error", (error) => { if (error.code === "EADDRINUSE" && port < 60000) { port += 1; listen(); } else throw error; });
  for (const signal of ["SIGTERM", "SIGINT"]) process.once(signal, () => { server.closeAllConnections(); server.close(async () => { await runtime.close(); process.exit(0); }); });
}
listen();
