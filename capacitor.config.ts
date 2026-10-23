import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "top.scriverse.app",
  appName: "叙界",
  webDir: "dist/public",
  server: {
    allowNavigation: ["*"],
    cleartext: true
  }
};

export default config;
