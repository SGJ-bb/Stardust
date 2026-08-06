/**
 * Web 模式 API 服务层
 * 在非 Tauri 环境下，直接通过 fetch 调用 LLM API
 * 支持 OpenAI 兼容、Anthropic、Ollama 等多种格式的 SSE 流式输出
 */

import { useSettingsStore } from "@/stores/useSettingsStore";
import type { ProviderProfile, LlmProvider } from "@/types/settings";

/** 从 settings store 获取当前活跃的 provider */
function getActiveProvider(): ProviderProfile | null {
  const { settings } = useSettingsStore.getState();
  if (!settings.activeProviderId) return null;
  return settings.providers.find(p => p.id === settings.activeProviderId) ?? null;
}

/** 构建 Authorization 头 */
function buildAuthHeaders(provider: ProviderProfile): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };

  if (provider.provider === "local") {
    // Ollama 不需要 auth
  } else if (provider.provider === "anthropic") {
    headers["x-api-key"] = provider.apiKey;
    headers["anthropic-version"] = "2023-06-01";
  } else if (provider.provider === "google") {
    headers["Authorization"] = `Bearer ${provider.apiKey}`;
  } else {
    headers["Authorization"] = `Bearer ${provider.apiKey}`;
  }

  return headers;
}

/** 获取 Ollama 基础地址（去除 /v1 后缀） */
function getOllamaBaseUrl(baseUrl: string): string {
  let url = baseUrl.replace(/\/+$/, "");
  // Ollama 内置模板可能带 /v1，需要去掉
  if (url.endsWith("/v1")) url = url.slice(0, -3);
  return url;
}

/**
 * 流式聊天回调接口
 */
export interface StreamChatCallbacks {
  onToken: (token: string) => void;
  onDone: () => void;
  onError: (error: string) => void;
}

/**
 * 流式聊天 — 自动识别 provider 类型并使用对应格式
 */
export async function webStreamChat(
  params: {
    personaId: string;
    content: string;
    attachments?: string[];
  },
  callbacks: StreamChatCallbacks,
): Promise<void> {
  const provider = getActiveProvider();

  if (!provider) {
    callbacks.onError("未配置 AI 模型，请先在设置中添加 LLM 提供商");
    return;
  }

  if (provider.provider === "local") {
    await streamOllama(provider, params.content, callbacks);
  } else if (provider.provider === "anthropic") {
    await streamAnthropic(provider, params.content, callbacks);
  } else {
    await streamOpenAICompatible(provider, params.content, callbacks);
  }
}

// ════════════════════════════════════════
// Anthropic Claude API（专用格式）
// ════════════════════════════════════════
async function streamAnthropic(
  provider: ProviderProfile,
  content: string,
  callbacks: StreamChatCallbacks,
): Promise<void> {
  let endpoint = provider.baseUrl.replace(/\/+$/, "");
  // Anthropic 端点：/v1/messages
  if (!endpoint.endsWith("/messages")) {
    if (endpoint.endsWith("/v1")) {
      endpoint += "/messages";
    } else {
      endpoint += "/v1/messages";
    }
  }

  const payload = {
    model: provider.modelId,
    max_tokens: provider.params?.maxTokens ?? 4096,
    temperature: provider.params?.temperature ?? 0.7,
    system: "你是一个友好、温暖的 AI 伴侣。",
    messages: [
      { role: "user", content },
    ],
    stream: true,
  };

  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: buildAuthHeaders(provider),
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(120000),
    });

    if (!response.ok) {
      let errMsg = `Anthropic 错误 (${response.status})`;
      try {
        const errData = await response.json() as Record<string, unknown>;
        const errorObj = errData.error as Record<string, unknown> | undefined;
        errMsg = (errorObj?.message as string) || errMsg;
      } catch {}
      callbacks.onError(errMsg);
      return;
    }

    const reader = response.body?.getReader();
    if (!reader) { callbacks.onError("无法读取响应流"); return; }

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

        // Anthropic SSE 格式：event: xxx\ndata: {...}
        if (trimmed.startsWith("data: ")) {
          const data = trimmed.slice(6).trim();
          if (data === "[DONE]") continue;

          try {
            const parsed = JSON.parse(data) as Record<string, unknown>;
            // event: content_block_delta → 提取文本 token
            const type = parsed.type as string | undefined;
            if (type === "content_block_delta") {
              const delta = parsed.delta as Record<string, unknown> | undefined;
              const text = delta?.text as string | undefined;
              if (text) callbacks.onToken(text);
            }
          } catch {}
        }
      }
    }

    callbacks.onDone();
  } catch (err: unknown) {
    const error = err as Error;
    callbacks.onError(error.name === "TimeoutError" ? "请求超时" : error.message || "网络错误");
  }
}

// ════════════════════════════════════════
// OpenAI 兼容 API（DeepSeek/Moonshot/通义/智谱 等）
// ════════════════════════════════════════
async function streamOpenAICompatible(
  provider: ProviderProfile,
  content: string,
  callbacks: StreamChatCallbacks,
): Promise<void> {
  let endpoint = provider.baseUrl.replace(/\/+$/, "");
  if (!endpoint.endsWith("/chat/completions")) {
    if (endpoint.endsWith("/v1")) {
      endpoint += "/chat/completions";
    } else {
      endpoint += "/v1/chat/completions";
    }
  }

  const payload = {
    model: provider.modelId,
    messages: [{ role: "user", content }],
    stream: true,
    max_tokens: provider.params?.maxTokens ?? 2048,
    temperature: provider.params?.temperature ?? 0.7,
    top_p: provider.params?.topP ?? 0.9,
  };

  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: buildAuthHeaders(provider),
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(120000),
    });

    if (!response.ok) {
      let errMsg = `API 错误 (${response.status})`;
      try {
        const errData = await response.json() as Record<string, unknown>;
        const errorObj = errData.error as Record<string, unknown> | undefined;
        if (errorObj?.message) {
          errMsg = Array.isArray(errorObj.message)
            ? errorObj.message[0] as string
            : errorObj.message as string;
        }
      } catch {}
      callbacks.onError(errMsg);
      return;
    }

    const reader = response.body?.getReader();
    if (!reader) { callbacks.onError("无法读取响应流"); return; }

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

        if (trimmed.startsWith("data: ")) {
          const data = trimmed.slice(6).trim();
          if (data === "[DONE]") { callbacks.onDone(); return; }

          try {
            const parsed = JSON.parse(data) as Record<string, unknown>;
            const choices = parsed.choices as Array<Record<string, unknown>> | undefined;
            const delta = choices?.[0]?.delta as Record<string, unknown> | undefined;
            const token = delta?.content as string | undefined;
            if (token) callbacks.onToken(token);

            const reason = choices?.[0]?.finish_reason as string | undefined;
            if (reason && reason !== "null" && !token) { callbacks.onDone(); return; }
          } catch {}
        }
      }
    }

    callbacks.onDone();
  } catch (err: unknown) {
    const error = err as Error;
    if (error.name === "TimeoutError") {
      callbacks.onError("请求超时（120秒）");
    } else if (error.name === "AbortError") {
      callbacks.onError("请求被取消");
    } else {
      callbacks.onError(error.message || "网络错误");
    }
  }
}

// ════════════════════════════════════════
// Ollama 本地模型
// ════════════════════════════════════════
async function streamOllama(
  provider: ProviderProfile,
  content: string,
  callbacks: StreamChatCallbacks,
): Promise<void> {
  const baseUrl = getOllamaBaseUrl(provider.baseUrl);
  const url = `${baseUrl}/api/chat`;

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: provider.modelId,
        messages: [{ role: "user", content }],
        stream: true,
      }),
      signal: AbortSignal.timeout(120000),
    });

    if (!response.ok) {
      let errMsg = `Ollama 错误 (${response.status})`;
      try {
        const errData = (await response.json()) as Record<string, unknown>;
        errMsg = (errData.error as string) || errMsg;
      } catch {}
      callbacks.onError(errMsg);
      return;
    }

    const reader = response.body?.getReader();
    if (!reader) { callbacks.onError("无法读取响应流"); return; }

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
        if (!trimmed) continue;

        try {
          const parsed = JSON.parse(trimmed) as Record<string, unknown>;
          const message = parsed.message as Record<string, unknown> | undefined;
          const token = message?.content as string | undefined;
          if (token) callbacks.onToken(token);
          if (parsed.done as boolean) { callbacks.onDone(); return; }
        } catch {}
      }
    }

    callbacks.onDone();
  } catch (err: unknown) {
    const error = err as Error;
    callbacks.onError(error.name === "TimeoutError" ? "Ollama 连接超时" : error.message || "无法连接 Ollama");
  }
}

/** 测试 Ollama 连接（供 LLMSettings 使用） */
export async function testOllamaConnection(baseUrl: string): Promise<{ ok: boolean; message: string }> {
  const url = `${getOllamaBaseUrl(baseUrl)}/api/tags`;
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
    if (res.ok) {
      const data = (await res.json()) as Record<string, unknown>;
      const models = data.models as Array<Record<string, string>> | undefined;
      return { ok: true, message: `Ollama 运行中，已安装 ${models?.length ?? 0} 个模型` };
    }
    return { ok: false, message: `Ollama 返回 ${res.status}` };
  } catch (err) {
    return { ok: false, message: `无法连接 Ollama: ${(err as Error).message}` };
  }
}
