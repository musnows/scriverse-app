import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "top.scriverse.app",
  appName: "叙界",
  loggingBehavior: "none",
  webDir: "dist/public",
  server: {
    cleartext: true
  }
};

export default config;
