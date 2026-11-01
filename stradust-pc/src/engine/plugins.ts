/**
 * 内置工具插件 —— 移植自 Android plugin/BuiltinPlugins.kt + PluginRegistry.kt
 * （PhoneOperationPlugin 依赖 Android 无障碍服务，Web 无等价物，由 screen.ts 屏幕感知替代）
 */

import type { ToolSpec } from "./llmClient";
import { useSettingsStore } from "@/stores/useSettingsStore";
import * as wakeup from "./wakeup";
import * as context from "./context";
import * as nicknames from "./nicknames";
import * as stickers from "./stickers";
import * as imagegen from "./imagegen";
import * as websearch from "./websearch";
import { getRecentTrend } from "./emotion";

export interface ToolRunContext {
  personaId: string;
  /**
   * ask 权限下的确认回调（由 chatEngine 注入 toolConfirm.requestToolConfirm）。
   * 返回 allow=本次允许 / always=允许并改为自动 / deny=拒绝。
   */
  confirm?: (toolName: string, argsSummary: string) => Promise<"allow" | "always" | "deny">;
}

// ─── 工具权限三档（借鉴 Operit：自动允许 / 每次询问 / 拒绝） ───

export type ToolPermission = "auto" | "ask" | "deny";

/** 工具中文名（确认弹窗与设置页共用） */
export const TOOL_LABELS: Record<string, string> = {
  set_alarm: "设置提醒",
  set_alarm_at_time: "设置闹钟",
  add_schedule: "添加日程",
  search_web: "联网搜索",
  search_memory: "检索记忆",
  search_diary: "检索日记",
  get_current_time: "查看时间",
  summarize_nicknames: "记录新昵称",
  send_sticker: "发送表情包",
  generate_image: "生成图片",
};

/** 读取某工具的权限（默认 auto，保持既有体验；用户可在设置中改为询问/拒绝） */
export function getToolPermission(name: string): ToolPermission {
  const map = useSettingsStore.getState().settings.toolPermissions;
  return map?.[name] ?? "auto";
}

/** 修改某工具权限并持久化 */
export function setToolPermission(name: string, perm: ToolPermission): void {
  const store = useSettingsStore.getState();
  const current = store.settings.toolPermissions ?? {};
  store.updateSettings({ toolPermissions: { ...current, [name]: perm } });
}

/** 工具执行结果：text 回填给 LLM；sideEffect 描述给 UI（表情包/图片上屏等） */
export interface ToolRunResult {
  text: string;
  sideEffect?: { type: "sticker" | "image" | "alarm" | "schedule"; payload: Record<string, unknown> };
}

const WEEKDAYS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];

export const TOOL_DEFINITIONS: ToolSpec[] = [
  {
    name: "set_alarm",
    description: "设置一个多少分钟后的提醒。当用户说「N分钟后提醒我」「待会儿叫我」时使用。",
    parameters: {
      type: "object",
      properties: {
        minutes: { type: "integer", description: "多少分钟后触发提醒" },
        label: { type: "string", description: "提醒的标签，如「喝水」「开会」" },
      },
      required: ["minutes", "label"],
    },
  },
  {
    name: "set_alarm_at_time",
    description: "设置在具体时刻的闹钟/提醒。当用户说了明确的时间点（如「7点叫我起床」）时使用。",
    parameters: {
      type: "object",
      properties: {
        hour: { type: "integer", description: "小时（24小时制，0-23）" },
        minute: { type: "integer", description: "分钟（0-59）" },
        label: { type: "string", description: "提醒标签，如「起床」「午休结束」" },
      },
      required: ["hour", "minute", "label"],
    },
  },
  {
    name: "add_schedule",
    description: "添加日程安排。当用户说「明天下午三点开会」「帮我记一下周六看电影」时使用。",
    parameters: {
      type: "object",
      properties: {
        description: { type: "string", description: "日程内容描述" },
        datetime: { type: "string", description: "日程时间，格式为「yyyy-MM-dd HH:mm」，如「2026-05-16 15:00」" },
      },
      required: ["description", "datetime"],
    },
  },
  {
    name: "search_web",
    description: "搜索互联网获取最新信息。当用户问到你不确定的事实、新闻、天气、汇率等实时信息时使用。",
    parameters: {
      type: "object",
      properties: {
        query: { type: "string", description: "搜索关键词，用简短精确的词组" },
      },
      required: ["query"],
    },
  },
  {
    name: "search_memory",
    description: "搜索你与用户的长期记忆。当你需要回忆用户之前说过的事情、喜好、约定时使用。",
    parameters: {
      type: "object",
      properties: {
        query: { type: "string", description: "搜索关键词，如「喜欢吃什么」「约定」「工作」等" },
      },
      required: ["query"],
    },
  },
  {
    name: "search_diary",
    description: "搜索你的日记内容。当你想回忆自己和用户经历过的事情、某天的心情时使用。",
    parameters: {
      type: "object",
      properties: {
        query: { type: "string", description: "搜索关键词，如「上周的心情」「最近开心的事」「关于工作的记录」等" },
        top_k: { type: "integer", description: "返回结果数量，默认3" },
      },
      required: ["query"],
    },
  },
  {
    name: "get_current_time",
    description: "获取当前的日期、时间和星期。当用户问到现在几点、今天几号，或你需要时间上下文时使用。",
    parameters: { type: "object", properties: {}, required: [] },
  },
  {
    name: "summarize_nicknames",
    description: "当你想给主人起新昵称或总结对主人的称呼时使用。系统会记录这些昵称并在之后的对话中使用。",
    parameters: {
      type: "object",
      properties: {
        nicknames: {
          type: "array",
          items: { type: "string" },
          description: "你为主人总结的称呼列表，每个称呼建议应简洁自然。可以基于聊天中提到的名字、身份特征或亲密关系来创造。",
        },
      },
      required: ["nicknames"],
    },
  },
  {
    name: "send_sticker",
    description: "发送一张表情包贴纸来表达情感。当你想用图片表达情绪（撒娇、吐槽、偷笑等）而不是用文字时使用。",
    parameters: {
      type: "object",
      properties: {
        emotion: { type: "string", description: "你想表达的情感，如：开心、难过、撒娇、吐槽、发呆、偷笑、慌张、好奇、害羞、调侃、坏笑、嫌弃等" },
      },
      required: ["emotion"],
    },
  },
  {
    name: "generate_image",
    description: "根据描述生成一张图片。当用户要求你画画、生成图片、或你想分享一个视觉场景时使用。",
    parameters: {
      type: "object",
      properties: {
        prompt: { type: "string", description: "图片描述，用英文描述效果更好，如：a girl standing under cherry blossoms, anime style, soft lighting" },
        style: { type: "string", description: "风格提示，可选。如：anime style, realistic, watercolor, pixel art等" },
      },
      required: ["prompt"],
    },
  },
];

/** 启用的工具定义（对齐 PluginRegistry.getEnabledDefinitions：搜索/图片未配置时剔除） */
export function getEnabledToolDefinitions(): ToolSpec[] {
  const s = useSettingsStore.getState().settings;
  const ai = s.aiFeatures;
  let defs = [...TOOL_DEFINITIONS];
  if (ai && ai.searchEnabled === false) defs = defs.filter((d) => d.name !== "search_web");
  if (!imagegen.isImageGenConfigured()) defs = defs.filter((d) => d.name !== "generate_image");
  return defs;
}

function safeArgs(raw: string): Record<string, unknown> {
  try { return JSON.parse(raw || "{}") as Record<string, unknown>; } catch { return {}; }
}

/** 执行工具（对齐 PluginRegistry.executePlugin + 各 Plugin.execute 的结果文案）。
 *  执行前经过权限门：deny 直接拒绝；ask 弹确认。 */
export async function executeTool(name: string, argsJson: string, ctx: ToolRunContext): Promise<ToolRunResult> {
  const args = safeArgs(argsJson);

  // ── 权限门 ──
  const perm = getToolPermission(name);
  if (perm === "deny") {
    return { text: `用户不允许执行该操作（${TOOL_LABELS[name] ?? name} 已被设置为"拒绝"）。请礼貌告知用户，并继续对话。` };
  }
  if (perm === "ask" && ctx.confirm) {
    const decision = await ctx.confirm(name, summarizeArgs(args));
    if (decision === "deny") {
      return { text: `用户拒绝了本次「${TOOL_LABELS[name] ?? name}」操作。不要重复尝试，礼貌地继续对话。` };
    }
    if (decision === "always") setToolPermission(name, "auto");
  }

  try {
    switch (name) {
      case "set_alarm": {
        const minutes = Number(args.minutes ?? 0);
        const label = String(args.label ?? "提醒");
        if (minutes <= 0) return { text: "失败：分钟数必须大于0" };
        const at = new Date(Date.now() + minutes * 60000);
        await wakeup.addAlarm({ hour: at.getHours(), minute: at.getMinutes(), label: `${label}（${minutes}分钟后）`, personaId: ctx.personaId });
        return {
          text: `已设置提醒：${minutes}分钟后「${label}」（${at.getHours()}:${String(at.getMinutes()).padStart(2, "0")}）`,
          sideEffect: { type: "alarm", payload: { label, at: at.toISOString() } },
        };
      }
      case "set_alarm_at_time": {
        const hour = Number(args.hour ?? -1);
        const minute = Number(args.minute ?? -1);
        const label = String(args.label ?? "闹钟");
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return { text: "失败：时间参数无效" };
        await wakeup.addAlarm({ hour, minute, label, personaId: ctx.personaId });
        return {
          text: `已设置闹钟：${hour}:${String(minute).padStart(2, "0")}「${label}」`,
          sideEffect: { type: "alarm", payload: { label, hour, minute } },
        };
      }
      case "add_schedule": {
        const description = String(args.description ?? "");
        const datetime = String(args.datetime ?? "");
        const parsed = parseDateTime(datetime);
        if (!parsed) return { text: `失败：时间格式无效「${datetime}」，应为 yyyy-MM-dd HH:mm` };
        await wakeup.addSchedule(ctx.personaId, parsed.toISOString(), description);
        return {
          text: `已添加日程：${description}（${datetime}）`,
          sideEffect: { type: "schedule", payload: { description, time: parsed.toISOString() } },
        };
      }
      case "search_web": {
        const query = String(args.query ?? "");
        if (!query) return { text: "失败：缺少搜索关键词" };
        const summary = await websearch.searchAndSummarize(query);
        return { text: summary };
      }
      case "search_memory": {
        const query = String(args.query ?? "");
        const facts = await context.searchMemories(ctx.personaId, query);
        if (facts.length === 0) return { text: "没有找到相关记忆" };
        return { text: facts.slice(0, 8).map((f) => `- ${f.fact}`).join("\n") };
      }
      case "search_diary": {
        const query = String(args.query ?? "");
        const topK = Number(args.top_k ?? 3);
        // 动态导入避免与 diary→chatEngine 的循环依赖
        const { searchDiaries } = await import("./diary");
        const hits = await searchDiaries(ctx.personaId, query, topK);
        if (hits.length === 0) return { text: "日记里没有相关内容" };
        return { text: hits.map((d) => `【${d.date}】${d.content.slice(0, 200)}`).join("\n\n") };
      }
      case "get_current_time": {
        const now = new Date();
        return {
          text: `当前时间：${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")} ${WEEKDAYS[now.getDay()]} ${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`,
        };
      }
      case "summarize_nicknames": {
        const list = Array.isArray(args.nicknames) ? (args.nicknames as unknown[]).map(String).filter(Boolean) : [];
        if (list.length === 0) return { text: "失败：昵称列表为空" };
        await nicknames.addDiscoveredBatch(ctx.personaId, list, "llm");
        return { text: `已记录对主人的新称呼：${list.join("、")}` };
      }
      case "send_sticker": {
        const emotion = String(args.emotion ?? "");
        const hit = await stickers.matchSticker(emotion, ctx.personaId);
        if (!hit) return { text: "没有匹配的表情包，改用文字表达情感吧" };
        await stickers.incrementUse(hit.id);
        return {
          text: `已发送表情包「${hit.name}」`,
          sideEffect: { type: "sticker", payload: { stickerId: hit.id, name: hit.name, dataUrl: hit.dataUrl } },
        };
      }
      case "generate_image": {
        const prompt = String(args.prompt ?? "");
        const style = args.style ? String(args.style) : "";
        if (!prompt) return { text: "失败：缺少图片描述" };
        const url = await imagegen.generateImage(style ? `${prompt}, ${style}` : prompt);
        return {
          text: "图片已生成",
          sideEffect: { type: "image", payload: { url, prompt } },
        };
      }
      default:
        return { text: `未知工具：${name}` };
    }
  } catch (e) {
    return { text: `工具 ${name} 执行失败：${(e as Error).message}` };
  }
}

/** 参数摘要（确认弹窗展示用） */
export function summarizeArgs(args: Record<string, unknown>): string {
  return Object.entries(args)
    .filter(([, v]) => v !== undefined && v !== null && String(v).trim())
    .map(([k, v]) => `${k}=${Array.isArray(v) ? v.join("、") : String(v)}`)
    .join("，")
    .slice(0, 160);
}

function parseDateTime(s: string): Date | null {
  const m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})[ T](\d{1,2}):(\d{2})/);
  if (!m) return null;
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), Number(m[4]), Number(m[5]));
  return isNaN(d.getTime()) ? null : d;
}

/** 情绪趋势摘要（供 chatEngine 注入主体性/关怀判断，对齐 EmotionGuardian 消费点） */
export async function getEmotionHint(personaId: string): Promise<string> {
  try {
    return await getRecentTrend(personaId, 24);
  } catch {
    return "";
  }
}
