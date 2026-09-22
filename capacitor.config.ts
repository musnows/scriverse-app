import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "top.scriverse.app",
  appName: "叙界",
  webDir: "www",
  server: {
    allowNavigation: ["*"],
    cleartext: true
  }
};

export default config;
