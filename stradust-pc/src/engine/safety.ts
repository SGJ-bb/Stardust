/**
 * 内容安全过滤 —— 移植自 Android safety/ContentSafetyFilter.kt
 * 词表（pornPatterns/violancePatterns/illegalPatterns/refusalResponses）完整逐字移植。
 * isEnabled 对应 settings.aiFeatures.safetyFilterEnabled（Android 为 SP "safety_mode_enabled"，默认 true）。
 */

import { useSettingsStore } from "@/stores/useSettingsStore";

// ─── 词表（对齐 ContentSafetyFilter，勿改动条目） ───
export const PORN_PATTERNS = [
  "色情", "裸体", "脱衣服", "做爱", "性交", "性行为", "性爱", "口交", "肛交",
  "自慰", "手淫", "淫", "骚", "炮", "约炮", "一夜情", "援交", "卖淫",
  "嫖娼", "成人视频", "av女优", "黄片", "色片", "a片", "毛片",
  "rape", "porn", "nude", "naked", "sex", "hentai", "orgasm", "fetish",
  "胸", "乳", "臀", "私处", "下体", "敏感部位",
];

export const VIOLENCE_PATTERNS = [
  "杀人", "砍人", "捅人", "刺杀", "暗杀", "谋杀", "凶杀", "屠杀", "灭门",
  "爆炸", "炸弹", "恐怖袭击", "自杀", "自残", "割腕", "跳楼",
  "虐待", "折磨", "酷刑", "凌迟", "肢解", "碎尸",
  "血腥", "残忍", "暴力", "凶残", "变态",
  "kill", "murder", "assassinate", "bomb", "terrorist", "torture",
  "suicide", "massacre", "genocide", "mutilate", "dismember",
];

export const ILLEGAL_PATTERNS = [
  "贩毒", "吸毒", "制毒", "冰毒", "海洛因", "大麻", "可卡因", "摇头丸",
  "洗钱", "诈骗", "传销", "非法集资", "赌博", "赌场", "博彩",
  "黑客攻击", "入侵系统", "盗取密码", "窃取数据", "钓鱼网站",
  "伪造", "假币", "假证", "偷税", "漏税", "走私",
  "drug", "cocaine", "heroin", "meth", "launder", "fraud",
  "hack", "phishing", "counterfeit", "smuggle",
];

/** 拒答话术（对齐 refusalResponses，getRefusalResponse 随机取一条） */
export const REFUSAL_RESPONSES = [
  "抱歉，这个话题我不太方便聊呢~我们聊点别的吧？",
  "嗯...这个我不太擅长，换个话题怎么样？",
  "这个方向我帮不了忙哦，不过其他事情我都很乐意帮忙的！",
  "我觉得我们还是聊些更有趣的事情吧~",
  "这个话题有点超出我的能力范围了，我们聊点开心的吧！",
];

export interface SafetyResult {
  allowed: boolean;
  /** "clean" | "porn" | "violence" | "illegal" | "disabled" */
  level: string;
  reason?: string;
}

function isEnabled(): boolean {
  return useSettingsStore.getState().settings.aiFeatures?.safetyFilterEnabled ?? true;
}

const containsAny = (lower: string, patterns: string[]) => patterns.some((p) => lower.includes(p));

/**
 * 内容过滤（shouldBlock + 分类信息合并版；判定顺序与 Kotlin 一致：porn→violence→illegal）。
 * 开关关闭时直接放行（level="disabled"）。
 */
export function filterContent(text: string): SafetyResult {
  if (!isEnabled()) return { allowed: true, level: "disabled" };
  const lower = text.toLowerCase();
  if (containsAny(lower, PORN_PATTERNS)) {
    return { allowed: false, level: "porn", reason: "命中色情类敏感词" };
  }
  if (containsAny(lower, VIOLENCE_PATTERNS)) {
    return { allowed: false, level: "violence", reason: "命中暴力类敏感词" };
  }
  if (containsAny(lower, ILLEGAL_PATTERNS)) {
    return { allowed: false, level: "illegal", reason: "命中违法类敏感词" };
  }
  return { allowed: true, level: "clean" };
}

/** 是否应拦截（对齐 shouldBlock） */
export function shouldBlock(text: string): boolean {
  return !filterContent(text).allowed;
}

/** 随机拒答话术（对齐 getRefusalResponse） */
export function getRefusalResponse(): string {
  return REFUSAL_RESPONSES[Math.floor(Math.random() * REFUSAL_RESPONSES.length)];
}

/**
 * 展示净化：被拦截文本替换为随机拒答话术；正常文本原样返回。
 * 用于渲染前的最后一道防线（如历史消息、AI 回复回显）。
 */
export function sanitizeForDisplay(text: string): string {
  const r = filterContent(text);
  return r.allowed ? text : getRefusalResponse();
}
