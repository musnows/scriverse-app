import { LocalAiClient } from "./local-ai/client.ts";
import {
  parseCreateLocalAiProviderInput, parseUpdateLocalAiProviderInput, parseRemoveLocalAiProviderInput,
  parseCreateLocalAiModelInput, parseUpdateLocalAiModelInput, parseRemoveLocalAiModelInput,
  parseLocalAiSystemPromptInput, parseLocalAiCompletionRequestInput, parseLocalAiAgentRoundInput
} from "./local-ai/contract.ts";

const KEY = "scriverse.mobile.local-ai.v1";
const failure = (code, message) => Object.assign(new Error(message), { code });
const timestamp = () => new Date().toISOString();
const withoutKey = ({ apiKey: _apiKey, ...value }) => value;

export class MobileLocalAiStore {
  constructor({ storage = globalThis.localStorage, secrets = null, fetchImpl = globalThis.fetch } = {}) {
    this.storage = storage;
    const temporarySecrets = new Map();
    this.secrets = secrets ?? { async secretSet({ id, value }) { temporarySecrets.set(id, value); }, async secretGet({ id }) { return { value: temporarySecrets.get(id) ?? "" }; } };
    this.fetch = fetchImpl;
    this.aiClient = new LocalAiClient((input, init) => this.fetch(input, { ...init, timeoutMs: 3600000 }));
    this.pending = Promise.resolve();
    this.requests = new Map();
    this.limits = new Map();
  }

  read() {
    const raw = this.storage.getItem(KEY);
    if (!raw) return { version: 1, providers: [], models: [], systemPrompt: "", updatedAt: timestamp() };
    let value;
    try { value = JSON.parse(raw); } catch { throw failure("LOCAL_AI_STORE_INVALID", "本机 AI 配置无法读取，请保留设备数据并检查存储"); }
    if (value?.version !== 1 || !Array.isArray(value.providers) || !Array.isArray(value.models) || typeof value.systemPrompt !== "string") throw failure("LOCAL_AI_STORE_INVALID", "本机 AI 配置格式无效");
    return value;
  }

  update(transform) {
    const operation = this.pending.catch(() => undefined).then(async () => {
      const document = this.read();
      const result = await transform(document);
      document.updatedAt = timestamp();
      const serialized = JSON.stringify(document);
      if (new TextEncoder().encode(serialized).length > 4 * 1024 * 1024) throw failure("LOCAL_AI_STORE_FULL", "本机 AI 配置已达到存储上限");
      this.storage.setItem(KEY, serialized);
      return result;
    });
    this.pending = operation;
    return operation;
  }

  configuration() {
    const document = this.read();
    const providers = document.providers.map((provider) => ({ ...provider, scope: "local", apiKey: "" }));
    const byId = new Map(providers.map((provider) => [provider.id, provider]));
    const models = document.models.flatMap((model) => {
      const provider = byId.get(model.providerId);
      if (!provider) return [];
      return [{ ...model, scope: "local", providerName: `local/${provider.name}`, providerProtocol: provider.protocol, providerMaxTokensParameter: provider.maxTokensParameter, providerThinkingType: provider.thinkingType, providerConcurrencyLimit: provider.concurrencyLimit, providerRpmLimit: provider.rpmLimit, providerAnalysisTimeoutSeconds: provider.analysisTimeoutSeconds, providerStatus: provider.status, providerConnectionStatus: provider.connectionStatus }];
    });
    return { providers, models, systemPrompt: document.systemPrompt, updatedAt: document.updatedAt };
  }

  async createProvider(value) {
    const input = parseCreateLocalAiProviderInput(value);
    return this.update(async (document) => {
      if (document.providers.length >= 64) throw failure("LOCAL_AI_PROVIDER_LIMIT", "最多配置 64 个本机供应商");
      const id = crypto.randomUUID();
      await this.secrets.secretSet({ id, value: input.apiKey });
      const provider = { ...withoutKey(input), id, hasApiKey: Boolean(input.apiKey), connectionStatus: "unchecked", lastError: null, lastSuccessAt: null, createdAt: timestamp(), updatedAt: timestamp() };
      document.providers.push(provider);
      return provider;
    });
  }

  async updateProvider(value) {
    const input = parseUpdateLocalAiProviderInput(value);
    return this.update(async (document) => {
      const current = document.providers.find((provider) => provider.id === input.providerId);
      if (!current) throw failure("LOCAL_AI_PROVIDER_NOT_FOUND", "本机 AI 供应商不存在");
      if (input.replaceApiKey) await this.secrets.secretSet({ id: current.id, value: input.apiKey });
      const { providerId: _providerId, replaceApiKey: _replaceApiKey, ...fields } = withoutKey(input);
      Object.assign(current, fields, { hasApiKey: input.replaceApiKey ? Boolean(input.apiKey) : current.hasApiKey, connectionStatus: "unchecked", lastError: null, updatedAt: timestamp() });
      return current;
    });
  }

  async removeProvider(value) {
    const { providerId } = parseRemoveLocalAiProviderInput(value);
    return this.update(async (document) => {
      if (!document.providers.some((provider) => provider.id === providerId)) throw failure("LOCAL_AI_PROVIDER_NOT_FOUND", "本机 AI 供应商不存在");
      document.providers = document.providers.filter((provider) => provider.id !== providerId);
      document.models = document.models.filter((model) => model.providerId !== providerId);
      await this.secrets.secretSet({ id: providerId, value: "" });
    });
  }

  async createModel(value) {
    const input = parseCreateLocalAiModelInput(value);
    return this.update((document) => {
      if (!document.providers.some((provider) => provider.id === input.providerId)) throw failure("LOCAL_AI_PROVIDER_NOT_FOUND", "请先创建本机供应商");
      if (document.models.length >= 512) throw failure("LOCAL_AI_MODEL_LIMIT", "最多配置 512 个本机模型");
      if (input.imageToolDefault) for (const model of document.models) model.imageToolDefault = false;
      const model = { ...input, id: crypto.randomUUID(), createdAt: timestamp(), updatedAt: timestamp() };
      document.models.push(model);
      return model;
    });
  }

  async updateModel(value) {
    const { localModelId, ...input } = parseUpdateLocalAiModelInput(value);
    return this.update((document) => {
      const current = document.models.find((model) => model.id === localModelId);
      if (!current || !document.providers.some((provider) => provider.id === input.providerId)) throw failure("LOCAL_AI_MODEL_NOT_FOUND", "本机模型或供应商不存在");
      if (input.imageToolDefault) for (const model of document.models) model.imageToolDefault = false;
      Object.assign(current, input, { updatedAt: timestamp() });
      return current;
    });
  }

  async removeModel(value) {
    const { modelId } = parseRemoveLocalAiModelInput(value);
    return this.update((document) => { document.models = document.models.filter((model) => model.id !== modelId); });
  }

  async updateSystemPrompt(value) {
    const { systemPrompt } = parseLocalAiSystemPromptInput(value);
    return this.update((document) => { document.systemPrompt = systemPrompt; });
  }

  async credential(modelId) {
    const config = this.configuration();
    const model = config.models.find((item) => item.id === modelId);
    const provider = config.providers.find((item) => item.id === model?.providerId);
    if (!model || !provider) throw failure("LOCAL_AI_MODEL_NOT_FOUND", "本机模型或供应商不存在");
    return { model, provider: { ...provider, apiKey: (await this.secrets.secretGet({ id: provider.id })).value ?? "" }, systemPrompt: config.systemPrompt };
  }

  client() {
    return this.aiClient;
  }

  async testModel({ modelId }) {
    const credential = await this.credential(modelId);
    const started = performance.now();
    try {
      const result = await this.client().testModel(credential);
      await this.connectionResult(credential.provider.id, null);
      return { ...result, ok: true, durationMs: Math.round(performance.now() - started) };
    } catch (error) {
      const message = this.safeMessage(error, credential.provider.apiKey);
      await this.connectionResult(credential.provider.id, message);
      throw failure(error.code ?? "LOCAL_AI_TEST_FAILED", message);
    }
  }

  async testProvider({ providerId }) {
    const model = this.configuration().models.find((item) => item.providerId === providerId && item.enabled);
    if (!model) throw failure("LOCAL_AI_MODEL_REQUIRED", "请先为供应商添加并启用一个模型，再测试连接");
    return this.testModel({ modelId: model.id });
  }

  connectionResult(providerId, message) {
    return this.update((document) => {
      const provider = document.providers.find((item) => item.id === providerId);
      if (provider) Object.assign(provider, { connectionStatus: message ? "error" : "success", lastError: message, ...(message ? {} : { lastSuccessAt: timestamp() }) });
    });
  }

  safeMessage(error, secret = "") {
    const message = typeof error?.message === "string" ? error.message : "本机 AI 请求失败";
    return (secret ? message.replaceAll(secret, "[REDACTED]") : message).slice(0, 300);
  }

  async acquire(provider, signal) {
    let state = this.limits.get(provider.id);
    if (!state) { state = { active: 0, times: [] }; this.limits.set(provider.id, state); }
    while (true) {
      signal.throwIfAborted();
      state.times = state.times.filter((time) => time > Date.now() - 60000);
      if (state.active < provider.concurrencyLimit && state.times.length < provider.rpmLimit) {
        state.active += 1;
        state.times.push(Date.now());
        return () => { state.active -= 1; };
      }
      await new Promise((resolve) => setTimeout(resolve, 50));
    }
  }

  async run(kind, value, listener) {
    const input = kind === "complete" ? parseLocalAiCompletionRequestInput({ remoteSystemPrompt: "", ...value }) : parseLocalAiAgentRoundInput(value);
    if (this.requests.has(input.requestId) || this.requests.size >= 64) throw failure("LOCAL_AI_REQUEST_BUSY", "AI 请求已存在或并发请求过多");
    const controller = new AbortController();
    this.requests.set(input.requestId, controller);
    let release = null;
    let key = "";
    try {
      const credential = await this.credential(input.modelId);
      key = credential.provider.apiKey;
      const taskType = input.taskType ?? "chat";
      if (!credential.model.purposes.includes(taskType)) throw failure("LOCAL_AI_TASK_TYPE_UNSUPPORTED", "当前模型未启用这项用途");
      release = await this.acquire(credential.provider, controller.signal);
      const { requestId: _requestId, ...completion } = input;
      return await this.client()[kind](credential, kind === "complete" ? completion : input, controller.signal, listener);
    } catch (error) {
      if (controller.signal.aborted) throw failure("LOCAL_AI_CANCELLED", "AI 请求已取消");
      throw failure(error.code ?? "LOCAL_AI_FAILED", this.safeMessage(error, key));
    } finally { release?.(); this.requests.delete(input.requestId); }
  }

  cancel({ requestId }) { this.requests.get(requestId)?.abort(); return { cancelled: true }; }
}

export function createMobileLocalAiBridge(options) {
  const store = new MobileLocalAiStore(options);
  const wrap = (operation) => async (...args) => {
    try { return { ok: true, data: await operation(...args) }; }
    catch (error) { return { ok: false, error: { code: error.code ?? "LOCAL_AI_FAILED", message: store.safeMessage(error) } }; }
  };
  return {
    configuration: wrap(() => store.configuration()), catalog: wrap(() => { const { models, updatedAt } = store.configuration(); return { models, updatedAt }; }),
    ...Object.fromEntries(["createProvider", "updateProvider", "removeProvider", "createModel", "updateModel", "removeModel", "updateSystemPrompt", "testModel", "testProvider"].map((method) => [method, wrap((value) => store[method](value))])),
    complete: wrap((input, listener) => store.run("complete", input, listener)),
    completeAgentRound: wrap((input, listener) => store.run("completeAgentRound", input, listener)),
    cancel: wrap((input) => store.cancel(input)), cancelAgentRound: wrap((input) => store.cancel(input))
  };
}
