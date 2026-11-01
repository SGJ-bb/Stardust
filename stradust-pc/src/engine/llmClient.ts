/**
 * 统一 LLM 客户端 —— 对齐 Android network/ApiClient.kt 的核心能力：
 * - OpenAI 兼容 / Anthropic / Ollama(local) 三种协议
 * - 流式 SSE + 非流式
 * - tools（function calling）与 tool 结果回填
 * - 厂商参数 profile 裁剪（presets.ts）
 * - 结构化 JSON 回复解析（{reply, emotion, action, ...}）
 * 浏览器与 Tauri WebView 通用（直接 fetch 用户配置的 API，与 Android 行为一致）。
 */

import { useSettingsStore } from "@/stores/useSettingsStore";
import type { ProviderProfile } from "@/types/settings";
import { getProfile, shouldSendFreqPenalty, shouldSendPresPenalty } from "./presets";

export interface ChatMsg {
  role: "system" | "user" | "assistant" | "tool";
  content: string;
  /** assistant 发起工具调用 */
  toolCalls?: { id: string; name: string; arguments: string }[];
  /** role=tool 时对应调用 */
  toolCallId?: string;
  /** 多模态图片（dataURL/url） */
  images?: string[];
}

export interface ToolSpec {
  name: string;
  description: string;
  parameters: Record<string, unknown>;
}

export interface LlmOptions {
  temperature?: number;
  topP?: number;
  frequencyPenalty?: number;
  presencePenalty?: number;
  maxTokens?: number;
  tools?: ToolSpec[];
  signal?: AbortSignal;
  /** 覆盖提供商（多角色群聊等） */
  providerOverride?: ProviderProfile;
}

export interface LlmResult {
  text: string;
  toolCalls: { id: string; name: string; arguments: string }[];
  reasoningContent?: string;
  finishReason?: string;
  raw?: unknown;
}

export function getActiveProvider(): ProviderProfile | null {
  const { settings } = useSettingsStore.getState();
  if (settings.activeProviderId) {
    const p = settings.providers.find((x) => x.id === settings.activeProviderId);
    if (p) return p;
  }
  return settings.providers.find((x) => x.isDefault) ?? settings.providers[0] ?? null;
}

function buildAuthHeaders(provider: ProviderProfile): Record<string, string> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (provider.provider === "local") return headers;
  if (provider.provider === "anthropic") {
    headers["x-api-key"] = provider.apiKey;
    headers["anthropic-version"] = "2023-06-01";
    headers["Authorization"] = `Bearer ${provider.apiKey}`;
  } else {
    headers["Authorization"] = `Bearer ${provider.apiKey}`;
  }
  return headers;
}

function effectiveParams(provider: ProviderProfile, opts?: LlmOptions) {
  const s = useSettingsStore.getState().settings;
  const gp = s.chatParams;
  const profile = getProfile(provider.provider);
  const temperature = opts?.temperature ?? gp?.temperature ?? provider.params?.temperature ?? profile.defaultTemp;
  const topP = opts?.topP ?? gp?.topP ?? provider.params?.topP ?? profile.defaultTopP;
  const maxTokens = opts?.maxTokens ?? gp?.maxTokens ?? provider.params?.maxTokens ?? profile.defaultMaxTokens;
  const frequencyPenalty = opts?.frequencyPenalty ?? gp?.frequencyPenalty ?? 0;
  const presencePenalty = opts?.presencePenalty ?? gp?.presencePenalty ?? 0;
  const clamped = {
    temperature: Math.min(profile.tempMax, Math.max(profile.tempMin, temperature)),
    topP: Math.min(profile.topPMax, Math.max(profile.topPMin, topP)),
    maxTokens: Math.min(profile.maxTokensLimit, Math.max(1, maxTokens)),
  };
  return { ...clamped, frequencyPenalty, presencePenalty, profile };
}

const OPENAI_SPECIAL_PATHS = ["/chat/completions", "/v1"];

function openaiEndpoint(baseUrl: string): string {
  let url = baseUrl.replace(/\/+$/, "");
  if (!OPENAI_SPECIAL_PATHS.some((p) => url.endsWith(p))) {
    url = url.endsWith("/v1") ? `${url}/chat/completions` : `${url}/v1/chat/completions`;
  }
  return url;
}

function ollamaBase(baseUrl: string): string {
  let url = baseUrl.replace(/\/+$/, "");
  if (url.endsWith("/v1")) url = url.slice(0, -3);
  return url;
}

/** 将 ChatMsg 转 OpenAI messages（含多模态与 tool 协议） */
function toOpenaiMessages(messages: ChatMsg[]): object[] {
  const out: object[] = [];
  for (const m of messages) {
    if (m.role === "tool") {
      out.push({ role: "tool", tool_call_id: m.toolCallId, content: m.content });
    } else if (m.role === "assistant" && m.toolCalls?.length) {
      out.push({
        role: "assistant",
        content: m.content || null,
        tool_calls: m.toolCalls.map((tc) => ({
          id: tc.id,
          type: "function",
          function: { name: tc.name, arguments: tc.arguments },
        })),
      });
    } else if (m.images?.length) {
      out.push({
        role: m.role,
        content: [
          { type: "text", text: m.content },
          ...m.images.map((img) => ({ type: "image_url", image_url: { url: img } })),
        ],
      });
    } else {
      out.push({ role: m.role, content: m.content });
    }
  }
  return out;
}

function toOpenaiTools(tools?: ToolSpec[]) {
  if (!tools?.length) return undefined;
  return tools.map((t) => ({ type: "function", function: { name: t.name, description: t.description, parameters: t.parameters } }));
}

async function fetchJson(url: string, init: RequestInit, timeoutMs = 180000): Promise<Response> {
  const signal = init.signal ?? AbortSignal.timeout(timeoutMs);
  return fetch(url, { ...init, signal });
}

/** 非流式对话 */
export async function chatCompletion(messages: ChatMsg[], opts?: LlmOptions): Promise<LlmResult> {
  const provider = opts?.providerOverride ?? getActiveProvider();
  if (!provider) throw new Error("未配置 AI 模型，请先在设置中添加 LLM 提供商");
  const { temperature, topP, maxTokens, frequencyPenalty, presencePenalty, profile } = effectiveParams(provider, opts);

  if (provider.provider === "local") {
    const res = await fetchJson(`${ollamaBase(provider.baseUrl)}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ model: provider.modelId, messages: messages.map((m) => ({ role: m.role, content: m.content })), stream: false }),
      signal: opts?.signal,
    });
    if (!res.ok) throw new Error(`Ollama 错误 (${res.status})`);
    const data = (await res.json()) as { message?: { content?: string } };
    return { text: data.message?.content ?? "", toolCalls: [] };
  }

  if (provider.provider === "anthropic") {
    const systemMsg = messages.find((m) => m.role === "system")?.content;
    const rest = messages.filter((m) => m.role !== "system");
    let endpoint = provider.baseUrl.replace(/\/+$/, "");
    if (!endpoint.endsWith("/messages")) {
      endpoint = endpoint.endsWith("/v1") ? `${endpoint}/messages` : `${endpoint}/v1/messages`;
    }
    const res = await fetchJson(endpoint, {
      method: "POST",
      headers: buildAuthHeaders(provider),
      body: JSON.stringify({
        model: provider.modelId,
        max_tokens: maxTokens,
        temperature,
        system: systemMsg,
        messages: rest.map((m) => ({ role: m.role === "assistant" ? "assistant" : "user", content: m.content })),
      }),
      signal: opts?.signal,
    });
    if (!res.ok) throw new Error(await readError(res, "Anthropic"));
    const data = (await res.json()) as { content?: { type: string; text?: string }[] };
    const text = (data.content ?? []).filter((c) => c.type === "text").map((c) => c.text).join("");
    return { text, toolCalls: [] };
  }

  // OpenAI 兼容
  const body: Record<string, unknown> = {
    model: provider.modelId,
    messages: toOpenaiMessages(messages),
    stream: false,
    max_tokens: maxTokens,
    temperature,
    top_p: topP,
  };
  if (shouldSendFreqPenalty(provider.provider)) body.frequency_penalty = frequencyPenalty;
  if (shouldSendPresPenalty(provider.provider)) body.presence_penalty = presencePenalty;
  const tools = toOpenaiTools(opts?.tools);
  if (tools) body.tools = tools;

  const res = await fetchJson(openaiEndpoint(provider.baseUrl), {
    method: "POST",
    headers: buildAuthHeaders(provider),
    body: JSON.stringify(body),
    signal: opts?.signal,
  });
  if (!res.ok) throw new Error(await readError(res, "API"));
  const data = (await res.json()) as {
    choices?: { message?: { content?: string; reasoning_content?: string; tool_calls?: { id?: string; function?: { name?: string; arguments?: string } }[] }; finish_reason?: string }[];
  };
  const choice = data.choices?.[0];
  const toolCalls = (choice?.message?.tool_calls ?? []).map((tc, i) => ({
    id: tc.id ?? `call-${Date.now()}-${i}`,
    name: tc.function?.name ?? "",
    arguments: tc.function?.arguments ?? "{}",
  }));
  return {
    text: choice?.message?.content ?? "",
    toolCalls,
    reasoningContent: choice?.message?.reasoning_content,
    finishReason: choice?.finish_reason,
    raw: data,
  };
}

export interface StreamHandlers {
  onToken: (token: string) => void;
  onDone?: (full: LlmResult) => void;
  onError?: (err: string) => void;
  signal?: AbortSignal;
}

/** 流式对话（OpenAI 兼容 / Anthropic / Ollama SSE）
 * - OpenAI 兼容通道支持 tools：按 index 累加流式 tool_calls 分片（对齐 Android ApiClient.executeStream）
 * - 流式通道不可用（HTTP 非 200 且带 tools / 零 SSE 帧）时自动降级非流式 */
export async function chatCompletionStream(messages: ChatMsg[], handlers: StreamHandlers, opts?: LlmOptions): Promise<LlmResult> {
  const provider = opts?.providerOverride ?? getActiveProvider();
  if (!provider) {
    handlers.onError?.("未配置 AI 模型，请先在设置中添加 LLM 提供商");
    return { text: "", toolCalls: [] };
  }

  const { temperature, topP, maxTokens, frequencyPenalty, presencePenalty } = effectiveParams(provider, opts);
  let endpoint: string;
  let body: Record<string, unknown>;
  if (provider.provider === "local") {
    endpoint = `${ollamaBase(provider.baseUrl)}/api/chat`;
    body = { model: provider.modelId, messages: messages.map((m) => ({ role: m.role, content: m.content })), stream: true };
  } else if (provider.provider === "anthropic") {
    endpoint = provider.baseUrl.replace(/\/+$/, "");
    if (!endpoint.endsWith("/messages")) {
      endpoint = endpoint.endsWith("/v1") ? `${endpoint}/messages` : `${endpoint}/v1/messages`;
    }
    const systemMsg = messages.find((m) => m.role === "system")?.content;
    body = {
      model: provider.modelId, max_tokens: maxTokens, temperature, system: systemMsg, stream: true,
      messages: messages.filter((m) => m.role !== "system").map((m) => ({ role: m.role === "assistant" ? "assistant" : "user", content: m.content })),
    };
  } else {
    endpoint = openaiEndpoint(provider.baseUrl);
    body = { model: provider.modelId, messages: toOpenaiMessages(messages), stream: true, max_tokens: maxTokens, temperature, top_p: topP };
    if (shouldSendFreqPenalty(provider.provider)) body.frequency_penalty = frequencyPenalty;
    if (shouldSendPresPenalty(provider.provider)) body.presence_penalty = presencePenalty;
    const tools = toOpenaiTools(opts?.tools);
    if (tools) body.tools = tools;
  }

  let full = "";
  // index → { id, name, args }（流式 tool_calls 分片累加，对齐 Kotlin tcFragments）
  const tcFragments = new Map<number, { id: string; name: string; args: string }>();
  let frameCount = 0;
  const fallbackNonStream = async (): Promise<LlmResult> => {
    const r = await chatCompletion(messages, opts);
    handlers.onToken(r.text);
    handlers.onDone?.(r);
    return r;
  };
  try {
    const res = await fetch(endpoint, {
      method: "POST",
      headers: buildAuthHeaders(provider),
      body: JSON.stringify(body),
      signal: handlers.signal ?? AbortSignal.timeout(180000),
    });
    if (!res.ok) {
      if (opts?.tools?.length) return await fallbackNonStream();
      const msg = await readError(res, "API");
      handlers.onError?.(msg);
      throw new Error(msg);
    }
    const reader = res.body?.getReader();
    if (!reader) throw new Error("无法读取响应流");
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() ?? "";
      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith(":")) continue;
        const payload = trimmed.startsWith("data:")
          ? trimmed.slice(5).trim()
          : trimmed.startsWith("{") ? trimmed : null;
        if (payload === null || payload === "" || payload === "[DONE]") continue;
        let parsed: Record<string, unknown>;
        try { parsed = JSON.parse(payload) as Record<string, unknown>; } catch { continue; }
        const srvErr = parsed.error as { message?: string } | undefined;
        if (srvErr?.message) { handlers.onError?.(srvErr.message); throw new Error(srvErr.message); }
        frameCount++;
        if (provider.provider === "local") {
          const token = (parsed.message as { content?: string } | undefined)?.content;
          if (token) { full += token; handlers.onToken(stripEmotionTagStreaming(full)); }
          continue;
        }
        if (provider.provider === "anthropic") {
          if (parsed.type === "content_block_delta") {
            const token = (parsed.delta as { text?: string } | undefined)?.text;
            if (token) { full += token; handlers.onToken(stripEmotionTagStreaming(full)); }
          }
          continue;
        }
        const choice = (parsed.choices as Record<string, unknown>[] | undefined)?.[0];
        const delta = (choice?.delta ?? choice?.message) as Record<string, unknown> | undefined;
        if (!delta) continue;
        const token = delta.content as string | undefined;
        if (token) { full += token; handlers.onToken(stripEmotionTagStreaming(full)); }
        const tcs = delta.tool_calls as Array<{ index?: number; id?: string; function?: { name?: string; arguments?: string } }> | undefined;
        if (tcs) {
          tcs.forEach((t, i) => {
            const idx = t.index ?? i;
            const holder = tcFragments.get(idx) ?? { id: "", name: "", args: "" };
            if (t.id) holder.id = t.id;
            if (t.function?.name) holder.name += t.function.name;
            if (t.function?.arguments) holder.args += t.function.arguments;
            tcFragments.set(idx, holder);
          });
        }
      }
    }
    if (frameCount === 0 && opts?.tools?.length) return await fallbackNonStream();
    const toolCalls = [...tcFragments.entries()]
      .sort((a, b) => a[0] - b[0])
      .filter(([, h]) => h.name)
      .map(([i, h], n) => ({ id: h.id || `call-${Date.now()}-${i}`, name: h.name, arguments: h.args || "{}" }));
    const result: LlmResult = { text: full, toolCalls };
    handlers.onDone?.(result);
    return result;
  } catch (e) {
    const err = e as Error;
    if (err.name === "TimeoutError") handlers.onError?.("请求超时（180秒）");
    else if (err.name === "AbortError") handlers.onError?.("请求被取消");
    else handlers.onError?.(err.message || "网络错误");
    throw e;
  }
}

// ─── 情绪标记协议（对齐 Android ApiClient 的 [[emotion:xxx]] 文本内标签） ───

const EMOTION_TAG_RE = /\[\[emotion:(\w+)\]\]/i;

/** 流式显示用：剥掉完整情绪标记 + 只收到一半的标记中间态（如 "[[emot"） */
export function stripEmotionTagStreaming(raw: string): string {
  const withoutComplete = raw.replace(/\[\[emotion:\w+\]\]/gi, "");
  return withoutComplete.replace(/\[\[[a-zA-Z:_]{0,15}$/, "");
}

/** 提取情绪并返回干净文本；映射失败默认 happy（对齐 Kotlin extractEmotionAction） */
export function extractEmotionAction(raw: string): { cleanText: string; emotion: string; action: string } {
  const match = raw.match(EMOTION_TAG_RE);
  const emotion = match ? match[1].toLowerCase() : "happy";
  const cleanText = raw.replace(EMOTION_TAG_RE, "").trim();
  const actionMap: Record<string, string> = {
    happy: "tail_flick", sad: "idle", angry: "ear_twitch", surprised: "stretch",
    tsundere: "blush", neutral: "idle", shy: "blush", excited: "bounce",
    comfort: "cuddle", proud: "bounce", confused: "question", sleepy: "sleep", cry: "shake",
  };
  return { cleanText, emotion, action: actionMap[emotion] ?? "idle" };
}

async function readError(res: Response, label: string): Promise<string> {
  let msg = `${label} 错误 (${res.status})`;
  try {
    const data = (await res.json()) as Record<string, unknown>;
    const errorObj = data.error as Record<string, unknown> | string | undefined;
    if (typeof errorObj === "string") msg = errorObj;
    else if (errorObj?.message) {
      msg = Array.isArray(errorObj.message) ? String(errorObj.message[0]) : String(errorObj.message);
    } else if (typeof data.message === "string") msg = data.message;
  } catch { /* ignore */ }
  if (res.status === 401 || res.status === 403) msg += "（API Key 无效或未填）";
  return msg;
}

// ─── 结构化 JSON 回复解析（对齐 Android ApiClient 的 JSON 输出协议） ───

export interface ParsedReply {
  reply: string;
  emotion: string;
  action: string;
  memories: string[];
  imagePrompt?: string;
  raw: unknown;
}

/** 宽容解析：允许 markdown 代码块/前后杂质 */
export function parseStructuredReply(text: string): ParsedReply {
  const fallback: ParsedReply = { reply: text.trim(), emotion: "neutral", action: "idle", memories: [], raw: null };
  let candidate = text.trim();
  const fenceMatch = candidate.match(/```(?:json)?\s*([\s\S]*?)```/);
  if (fenceMatch) candidate = fenceMatch[1].trim();
  const braceStart = candidate.indexOf("{");
  const braceEnd = candidate.lastIndexOf("}");
  if (braceStart < 0 || braceEnd <= braceStart) return fallback;
  candidate = candidate.slice(braceStart, braceEnd + 1);
  try {
    const obj = JSON.parse(candidate) as Record<string, unknown>;
    const reply = String(obj.reply ?? obj.response ?? obj.text ?? obj.message ?? "");
    if (!reply) return fallback;
    const memories = Array.isArray(obj.memories) ? (obj.memories as unknown[]).map(String) : [];
    return {
      reply,
      emotion: String(obj.emotion ?? "neutral"),
      action: String(obj.action ?? "idle"),
      memories,
      imagePrompt: obj.image_prompt ? String(obj.image_prompt) : undefined,
      raw: obj,
    };
  } catch {
    return fallback;
  }
}

/** 简单文本补全（用于摘要/归纳类调用） */
export async function simpleCompletion(prompt: string, opts?: LlmOptions & { system?: string }): Promise<string> {
  const messages: ChatMsg[] = [];
  if (opts?.system) messages.push({ role: "system", content: opts.system });
  messages.push({ role: "user", content: prompt });
  const r = await chatCompletion(messages, opts);
  return r.text;
}
