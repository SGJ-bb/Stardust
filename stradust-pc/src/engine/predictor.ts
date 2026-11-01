/**
 * 用户回复预测 —— 移植自 Android predict/ChatPredictor.kt（predictPrivateChat）
 * LLM 生成"用户接下来可能说的话"，尊重 settings.aiFeatures.replyPredictEnabled 开关；
 * 失败/关闭时返回内置兜底。提示词逐字对齐 Kotlin systemPrompt。
 */

import { simpleCompletion } from "./llmClient";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { usePersonaStore } from "@/stores/usePersonaStore";

const MIN_PREDICTIONS = 2; // ChatPredictor.MIN_PREDICTIONS
const MAX_PREDICTIONS = 6; // ChatPredictor.MAX_PREDICTIONS

/** 内置兜底（任务契约要求；Kotlin 失败时返回空列表，由 UI 隐藏） */
export const FALLBACK_PREDICTIONS = ["好呀", "哈哈真的吗", "然后呢"];

/** 可选角色信息（Kotlin 由调用方传 identity.name/personality；缺省时从 personaStore 读取） */
export interface PredictorPersona {
  name?: string;
  personality?: string;
}

/** 提取 JSON 数组（对齐 extractJsonArray：取首尾方括号） */
function extractJsonArray(text: string): string | null {
  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start < 0 || end <= start) return null;
  return text.slice(start, end + 1);
}

/** 解析预测列表（对齐 parsePredictions：少于 MIN 条视为失败） */
function parsePredictions(raw: string | null): string[] {
  if (!raw || !raw.trim()) return [];
  const jsonStr = extractJsonArray(raw.trim());
  if (!jsonStr) return [];
  try {
    const arr = JSON.parse(jsonStr);
    if (!Array.isArray(arr)) return [];
    const result: string[] = [];
    for (let i = 0; i < Math.min(arr.length, MAX_PREDICTIONS); i++) {
      const item = String(arr[i] ?? "").trim();
      if (item) result.push(item);
    }
    return result.length < MIN_PREDICTIONS ? [] : result;
  } catch {
    return [];
  }
}

/**
 * 预测用户可能的回复（对齐 predictPrivateChat）。
 * recentMessages 建议传最近 10 条；personaId 用于读取角色名/性格（store 缺失时用兜底）。
 */
export async function predictReplies(
  personaId: string,
  recentMessages: { role: string; content: string }[],
  persona?: PredictorPersona,
): Promise<string[]> {
  const enabled = useSettingsStore.getState().settings.aiFeatures?.replyPredictEnabled ?? true;
  if (!enabled) return [];
  if (recentMessages.length === 0) return [];
  try {
    let personaName = persona?.name ?? "星尘";
    let personaPersonality = persona?.personality ?? "";
    if (!persona?.name) {
      try {
        const p = usePersonaStore.getState().personas.find((x) => x.id === personaId);
        if (p) {
          personaName = p.name || personaName;
          personaPersonality = p.personality || personaPersonality;
        }
      } catch {
        /* store 未就绪时用默认 */
      }
    }
    const historyBlock = recentMessages
      .slice(-10)
      .map((m) => (m.role === "user" ? `用户: ${m.content}` : `${personaName}: ${m.content}`))
      .join("\n");
    const systemPrompt =
      "你是用户回复预测器。根据对话上下文，预测用户接下来可能说的话。" +
      "要求：返回2-6个简短预测，每个15字以内，覆盖不同方向（如追问、表达情感、转移话题、用()做动作等）。" +
      "部分预测可以包含()动作描写，如(摸摸头)你真棒、(歪头)怎么了？" +
      '严格返回JSON数组格式，如：["预测1","预测2","预测3"]' +
      "不要返回任何其他内容。";
    const userPrompt = `对话对象：${personaName}（${personaPersonality}）\n最近对话：\n${historyBlock}\n请预测用户接下来可能说的话：`;
    const text = await simpleCompletion(userPrompt, { system: systemPrompt, temperature: 0.7, topP: 0.9, maxTokens: 300 });
    const parsed = parsePredictions(text);
    return parsed.length > 0 ? parsed : [...FALLBACK_PREDICTIONS];
  } catch {
    return [...FALLBACK_PREDICTIONS];
  }
}
