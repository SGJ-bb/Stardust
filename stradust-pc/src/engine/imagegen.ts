/**
 * 图片生成 / 视觉识别 —— 移植自 Android network/ProviderAdapter.kt 的
 * buildImageRequest / buildImageHeaders / parseImageResponse / pollAliyunTask。
 *
 * Web 等价替代说明：
 * - Android 把 b64_json 解码写临时文件返回路径；Web 统一转为 dataURL 返回。
 * - 响应里给的是远程 URL 时，尝试 CORS 下载转 dataURL（避免后续展示被防盗链/CSP 拦截），
 *   下载失败则退回直链。
 * - aliyun 异步任务轮询照搬 pollAliyunTask（GET {origin}/api/v1/tasks/{task_id}，3s×20 次）。
 * - Kling 分支（JWT 签名）在 Android 侧存在但 presets.ts 未收录 kling formatType，暂不移植。
 */

import { useSettingsStore } from "@/stores/useSettingsStore";
import type { ServiceEndpointConfig } from "@/types/settings";
import { IMAGE_GEN_PRESETS, IMAGE_RECOG_PRESETS, findPreset, supportsVision } from "./presets";
import { chatCompletion, getActiveProvider, type ChatMsg } from "./llmClient";

// ═══════════════════════ 配置读取 ═══════════════════════

function getImageGenConfig(): ServiceEndpointConfig | null {
  const c = useSettingsStore.getState().settings.imageGen;
  return c?.baseUrl ? c : null;
}

function getImageRecogConfig(): ServiceEndpointConfig | null {
  const c = useSettingsStore.getState().settings.imageRecog;
  return c?.baseUrl ? c : null;
}

/** 图片生成服务是否已配置（页面用于决定是否启用生图工具） */
export function isImageGenConfigured(): boolean {
  return !!getImageGenConfig();
}

function formatTypeOf(cfg: ServiceEndpointConfig): string {
  const preset = findPreset(IMAGE_GEN_PRESETS, cfg.presetId);
  if (preset?.formatType) return preset.formatType;
  // URL 猜测兜底
  if (/dashscope\.aliyuncs\.com/i.test(cfg.baseUrl)) return "aliyun_async";
  if (/siliconflow/i.test(cfg.baseUrl)) return "siliconflow";
  return "openai";
}

// ═══════════════════════ 请求构造（照搬 ProviderAdapter） ═══════════════════════

/** 照搬 buildImageRequest：siliconflow / aliyun_async / openai(默认) 三分支 */
function buildImageRequest(formatType: string, model: string, prompt: string, n: number, size: string): Record<string, unknown> {
  if (formatType === "siliconflow") {
    return { model, prompt, image_size: size, batch_size: n, num_inference_steps: 20 };
  }
  if (formatType === "aliyun_async") {
    const dashscopeSize = size.replace("x", "*");
    const supportedSizes = new Set(["1024*1024", "768*1024", "1024*768", "768*768"]);
    return {
      model,
      input: {
        messages: [{ role: "user", content: [{ text: prompt }] }],
      },
      parameters: { n, size: supportedSizes.has(dashscopeSize) ? dashscopeSize : "1024*1024" },
    };
  }
  return { model, prompt, n, size };
}

/** 照搬 buildImageHeaders（web 无 secretKey HMAC 分支，与 Kotlin apiKey.startsWith("sk-")||secretKey 为空的常规路径一致） */
function buildImageHeaders(formatType: string, apiKey: string): Record<string, string> {
  if (formatType === "aliyun_async") {
    return {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "X-DashScope-Async": "enable",
    };
  }
  return { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" };
}

// ═══════════════════════ 结果归一化 ═══════════════════════

async function blobToDataUrl(blob: Blob): Promise<string | null> {
  try {
    return await new Promise<string>((resolve, reject) => {
      const fr = new FileReader();
      fr.onload = () => resolve(String(fr.result));
      fr.onerror = () => reject(fr.error);
      fr.readAsDataURL(blob);
    });
  } catch {
    return null;
  }
}

function b64ToDataUrl(b64: string, mime = "image/png"): string {
  return b64.startsWith("data:") ? b64 : `data:${mime};base64,${b64}`;
}

/** URL → dataURL（CORS 下载）；失败返回原直链（对齐"失败返回直链"要求） */
export async function urlToDataUrl(url: string): Promise<string> {
  if (!url || url.startsWith("data:")) return url;
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(30000) });
    if (!res.ok) return url;
    const blob = await res.blob();
    return (await blobToDataUrl(blob)) ?? url;
  } catch {
    return url;
  }
}

/** 照搬 pollAliyunTask：GET {protocol}://{host}/api/v1/tasks/{task_id}，3s 间隔最多 20 次 */
async function pollAliyunTask(apiUrl: string, apiKey: string, taskId: string): Promise<string | null> {
  let baseUrl: string;
  try {
    const u = new URL(apiUrl);
    baseUrl = `${u.protocol}//${u.host}`;
  } catch {
    baseUrl = apiUrl.split("/api/v1/")[0];
  }
  const taskUrl = `${baseUrl}/api/v1/tasks/${taskId}`;
  const headers = buildImageHeaders("aliyun_async", apiKey);
  for (let attempt = 0; attempt < 20; attempt++) {
    await new Promise((r) => setTimeout(r, 3000));
    try {
      const res = await fetch(taskUrl, { method: "GET", headers, signal: AbortSignal.timeout(15000) });
      const body = await res.text();
      const json = JSON.parse(body) as Record<string, any>;
      const status = json?.output?.task_status ?? "";
      if (status === "SUCCEEDED") {
        const url = json?.output?.results?.[0]?.url;
        return typeof url === "string" && url.trim() ? url : null;
      }
      if (status === "FAILED") {
        console.warn("[imagegen] Aliyun task failed:", json?.output?.message ?? "Task failed");
        return null;
      }
      // PENDING/RUNNING → 继续轮询
    } catch (e) {
      console.warn("[imagegen] poll aliyun task error:", (e as Error).message);
    }
  }
  return null;
}

interface FirstImage { url?: string; b64?: string }

/** 照搬 parseImageResponse 各分支，返回 url 或 base64（其一） */
async function parseImageResponse(
  formatType: string,
  responseBody: string,
  apiUrl: string,
  apiKey: string,
): Promise<FirstImage | null> {
  let json: Record<string, any>;
  try {
    json = JSON.parse(responseBody);
  } catch {
    return null;
  }
  if (formatType === "siliconflow") {
    const img = json?.images?.[0] ?? json?.data?.[0];
    if (typeof img?.url === "string" && img.url.trim()) return { url: img.url };
    if (typeof img?.b64_json === "string" && img.b64_json.trim()) return { b64: img.b64_json };
    return null;
  }
  if (formatType === "aliyun_async") {
    const output = json?.output;
    const status = output?.task_status ?? "";
    if (status === "SUCCEEDED") {
      const url = output?.results?.[0]?.url;
      return typeof url === "string" && url.trim() ? { url } : null;
    }
    if (status === "FAILED") {
      console.warn("[imagegen] Aliyun task failed immediately:", output?.message ?? "Task failed");
      return null;
    }
    const taskId = output?.task_id ?? "";
    if (taskId.trim()) {
      const url = await pollAliyunTask(apiUrl, apiKey, taskId);
      return url ? { url } : null;
    }
    console.warn("[imagegen] Aliyun async: no task_id and no results, status=", status);
    return null;
  }
  // openai 默认
  const first = json?.data?.[0];
  if (typeof first?.url === "string" && first.url.trim()) return { url: first.url };
  if (typeof first?.b64_json === "string" && first.b64_json.trim()) return { b64: first.b64_json };
  return null;
}

// ═══════════════════════ 对外 API ═══════════════════════

/**
 * 生成图片。返回 dataURL（优先）；远程图 CORS 下载失败时返回直链；失败返回空串。
 * size 形如 "1024x1024"。
 */
export async function generateImage(prompt: string, size = "1024x1024"): Promise<string> {
  const cfg = getImageGenConfig();
  if (!cfg) throw new Error("未配置图片生成服务，请先在设置中添加");
  const formatType = formatTypeOf(cfg);
  const model = cfg.model || findPreset(IMAGE_GEN_PRESETS, cfg.presetId)?.defaultModel || "dall-e-3";
  const body = buildImageRequest(formatType, model, prompt, 1, size);
  const headers = buildImageHeaders(formatType, cfg.apiKey);
  const res = await fetch(cfg.baseUrl, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(180000), // 含 aliyun 轮询预留长超时
  });
  if (!res.ok) {
    const errBody = await res.text().catch(() => "");
    throw new Error(`图片生成失败 HTTP ${res.status}: ${errBody.slice(0, 300)}`);
  }
  const parsed = await parseImageResponse(formatType, await res.text(), cfg.baseUrl, cfg.apiKey);
  if (!parsed) return "";
  if (parsed.b64) return b64ToDataUrl(parsed.b64);
  return urlToDataUrl(parsed.url!);
}

// ═══════════════════════ 视觉识别 ═══════════════════════

/**
 * 图像问答：走 chatCompletion 带 images 消息。
 * 模型优先取 settings.imageRecog（构造临时 ProviderProfile）；未配置时回退主聊天 provider
 * （要求 supportsVision，否则给出明确提示）。history 为最近的 [role, content] 文本对。
 */
export async function visionAnalyze(imageDataUrl: string, question: string, history?: { role: "user" | "assistant"; content: string }[]): Promise<string> {
  const recog = getImageRecogConfig();
  let providerOverride = undefined;
  if (recog) {
    providerOverride = {
      id: `vision-${recog.presetId}`,
      name: "vision",
      provider: "custom" as const,
      apiKey: recog.apiKey,
      baseUrl: recog.baseUrl,
      modelId: recog.model || findPreset(IMAGE_RECOG_PRESETS, recog.presetId)?.defaultModel || "gpt-4o",
      params: { temperature: 0.7, topP: 0.9, maxTokens: 1024, presencePenalty: 0, frequencyPenalty: 0, stopSequences: [] },
      isDefault: false,
    };
  } else {
    const active = getActiveProvider();
    if (!active) throw new Error("未配置 AI 模型，无法进行图像识别");
    // 回退主聊天 provider：要求厂商参数档案标记 supportsVision
    if (!supportsVision(active.provider)) {
      throw new Error(`当前聊天模型（${active.provider}）不支持视觉识别，请在设置中配置图片识别服务`);
    }
  }

  const messages: ChatMsg[] = [
    { role: "system", content: "你是用户的贴心伴侣，擅长观察图片并自然描述所见内容。用简洁口语化的中文回答。" },
  ];
  for (const h of history ?? []) {
    messages.push({ role: h.role, content: h.content });
  }
  messages.push({
    role: "user",
    content: question?.trim() || "看看这张图片，描述一下你看到了什么？",
    images: [imageDataUrl],
  });

  const r = await chatCompletion(messages, providerOverride ? { providerOverride } : undefined);
  return r.text.trim();
}
