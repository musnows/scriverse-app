import type { LocalAiProviderSummary, LocalAiModelSummary } from "./contract.js";

export type LocalAiModelCredential = {
  provider: LocalAiProviderSummary & { apiKey: string };
  model: LocalAiModelSummary;
  systemPrompt: string;
};
