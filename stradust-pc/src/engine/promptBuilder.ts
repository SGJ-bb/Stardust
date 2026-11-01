/**
 * 提示词构建器 —— 移植自 Android prompt/PromptBuilder.kt（407行，完整移植）
 * 同时合并了 MainActivity.getPersonaInfo / buildSystemContext 中在别处拼接的
 * [相关设定]（RAG）、[上下文]（记忆）、好感度称号与语言风格(languageStyle)注入逻辑。
 *
 * 与 Kotlin 版的差异见各函数注释；本模块为纯 TS，不依赖 React。
 */

import { kvGet, kvSet } from "@/lib/db";
import type { Persona } from "@/types/persona";
import { usePersonaStore } from "@/stores/usePersonaStore";
import type { UserPersonaCard } from "./kinds";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { AiFeaturesConfig, ChatParamsConfig, UserProfileConfig } from "@/types/settings";

// ─── IdentityBlock（对齐 PromptBuilder.kt data class IdentityBlock） ───
export interface IdentityBlock {
  name: string;
  personality: string;
  speechStyle: string;
  customPrompt: string;
  userNickname: string;
  userIdentity: string;
  userAbilities: string;
  userPersonalityDef: string;
  aiSummarizedPersonality: string;
  userGenderLabel: string;
  userBirthday: string;
  userAppearance: string;
}

/** 全局用户补充画像（身份/能力/性格定义等存 kv，对应 Android app_prefs 中的扩展键） */
export interface GlobalUserBlock {
  userName?: string;
  nickname?: string;
  userIdentity?: string;
  userAbilities?: string;
  /** 用户性格定义（user_personality_def） */
  userPersonalityDef?: string;
  /** AI 总结的用户性格（ai_summarized_personality） */
  aiSummarizedPersonality?: string;
}

const KV_GLOBAL_USER = "global_user_block";

export async function getGlobalUserBlock(): Promise<GlobalUserBlock> {
  return (await kvGet<GlobalUserBlock>(KV_GLOBAL_USER)) ?? {};
}

export async function setGlobalUserBlock(block: GlobalUserBlock): Promise<void> {
  await kvSet(KV_GLOBAL_USER, block);
}

// ─── 核心规则（对齐 CORE_RULES / CORE_RULES_NO_EMOTION） ───
const CORE_RULES =
  "\n【规则】\n" +
  "- 像真人聊天，简短自然。关心对方情绪。末尾[[emotion:happy/sad/angry/surprised/neutral]]\n" +
  "- 用()描述你的动作、表情、状态、情绪，要详细具体，包含身体部位、力度、速度、方向等细节，如(轻轻歪头，耳朵微微颤动，好奇地看向你)(脸颊泛起红晕，下意识攥紧衣角，目光闪躲)(猛地跳起来，双手在空中挥舞，尾巴兴奋地左右摇摆)(慵懒地趴在桌上，用指尖无意识地画圈，眼神迷离)\n" +
  "- 动作描写要丰富生动，每次至少包含2-3个细节，配合你的角色设定";

const CORE_RULES_NO_EMOTION =
  "\n【规则】\n" +
  "- 像真人聊天，简短自然。关心对方情绪\n" +
  "- 用()描述你的动作、表情、状态、情绪，要详细具体，包含身体部位、力度、速度、方向等细节，如(轻轻歪头，耳朵微微颤动，好奇地看向你)(脸颊泛起红晕，下意识攥紧衣角，目光闪躲)(猛地跳起来，双手在空中挥舞，尾巴兴奋地左右摇摆)(慵懒地趴在桌上，用指尖无意识地画圈，眼神迷离)\n" +
  "- 动作描写要丰富生动，每次至少包含2-3个细节，配合你的角色设定";

let cachedCoreRules: string | null = null;
let cachedCoreRulesEmotion: boolean | null = null;

/** 缓存失效（对齐 PromptBuilder.invalidateCache；identityCache 声明见下方） */
export function invalidatePromptCache(): void {
  cachedCoreRules = null;
  cachedCoreRulesEmotion = null;
  identityCache = null;
}

/** 读取设置（容忍 undefined） */
function feat(): Partial<AiFeaturesConfig> {
  return useSettingsStore.getState().settings.aiFeatures ?? {};
}
function chatParams(): Partial<ChatParamsConfig> {
  return useSettingsStore.getState().settings.chatParams ?? {};
}
function userProfile(): Partial<UserProfileConfig> {
  return useSettingsStore.getState().settings.userProfile ?? {};
}

/** 核心规则：依据 llmEmotionAnalysisEnabled 决定是否要求 [[emotion:xxx]] 后缀（同 Kotlin getCoreRules） */
export function getCoreRules(): string {
  const emotionEnabled = feat().llmEmotionAnalysisEnabled !== false;
  if (cachedCoreRules !== null && cachedCoreRulesEmotion === emotionEnabled) {
    return cachedCoreRules;
  }
  const rules = emotionEnabled ? CORE_RULES : CORE_RULES_NO_EMOTION;
  cachedCoreRules = rules;
  cachedCoreRulesEmotion = emotionEnabled;
  return rules;
}

// ─── 好感度称号（移植自 affection/AffectionManager.kt getAffectionTitle） ───
export function affectionTitleForLevel(level: number): string {
  if (level >= 96 && level <= 100) return "最重要的人";
  if (level >= 81) return "亲密伙伴";
  if (level >= 61) return "好朋友";
  if (level >= 41) return "朋友";
  if (level >= 21) return "认识的人";
  if (level >= 0) return "陌生人";
  return "未知";
}

// ─── 语言风格注入（PC 扩展：Android 端 languageStyle 未接线，此处按 Web 设置实现） ───
function languageStyleHint(style: ChatParamsConfig["languageStyle"] | undefined): string {
  switch (style) {
    case "tsundere":
      return "\n语言风格：傲娇。口是心非、嘴上不饶人但行动诚实，常用「哼」「才不是」「笨蛋」，但绝不冷漠伤人。";
    case "cute":
      return "\n语言风格：软萌可爱。语气甜软，爱用叠词和波浪号「～」，常用颜文字，让人有被治愈的感觉。";
    default:
      return "";
  }
}

// ─── buildIdentity ───
/**
 * 构建身份块（对齐 PromptBuilder.buildIdentity(context, personaId)）。
 * 数据源：Web 版 persona 来自 usePersonaStore；用户画像来自 settings.userProfile；
 * 昵称可由 opts.nickname 或 persona_data 覆盖（Android 从 persona_data_{id} SP 读 user_nickname）。
 * 含轻量缓存（personaId + persona.updatedAt + globalUser），invalidatePromptCache()/invalidateIdentityCache() 手动失效。
 */
let identityCache: { key: string; value: IdentityBlock } | null = null;

/** 身份块缓存失效（Kotlin invalidateCache 的身份部分） */
export function invalidateIdentityCache(): void {
  identityCache = null;
}

export function buildIdentity(
  persona: Persona | null | undefined,
  opts: {
    userName?: string;
    nickname?: string;
    userPersona?: UserPersonaCard | null;
    personaId?: string;
    globalUser?: GlobalUserBlock;
  } = {},
): IdentityBlock {
  const profile = userProfile();
  const g = opts.globalUser ?? {};
  const pid = opts.personaId ?? persona?.id ?? "default";

  const cacheKey = `${pid}|${persona?.updatedAt ?? 0}|${opts.nickname ?? ""}|${JSON.stringify(g)}|${profile.nickname ?? ""}|${profile.gender ?? ""}|${profile.birthday ?? ""}|${profile.appearance ?? ""}`;
  if (identityCache && identityCache.key === cacheKey) return identityCache.value;

  // 说话风格：persona.description 优先作为 speechStyle 语义位（Web Persona 无独立字段，用 description+tags 兜底）
  const speechStyle = persona?.description?.trim() || "";
  const genderLabel =
    profile.gender === "male" ? "男性" : profile.gender === "female" ? "女性" : "";

  const result: IdentityBlock = {
    name: persona?.name || g.userName || "星尘",
    personality: persona?.personality?.trim() || "",
    speechStyle,
    customPrompt: [persona?.scenario?.trim(), persona?.greeting?.trim() ? `开场白参考：${persona.greeting.trim()}` : ""]
      .filter(Boolean)
      .join("\n"),
    userNickname: opts.nickname ?? g.nickname ?? profile.nickname ?? "",
    userIdentity: g.userIdentity ?? "",
    userAbilities: g.userAbilities ?? "",
    userPersonalityDef: g.userPersonalityDef ?? opts.userPersona?.personality ?? "",
    aiSummarizedPersonality: g.aiSummarizedPersonality ?? "",
    userGenderLabel: genderLabel,
    userBirthday: profile.birthday ?? "",
    userAppearance: profile.appearance ?? "",
  };
  identityCache = { key: cacheKey, value: result };
  return result;
}

/** 从 personaStore 按 id 构建身份（便捷入口） */
export function buildIdentityById(personaId: string, opts: Parameters<typeof buildIdentity>[1] = {}): IdentityBlock {
  const persona = usePersonaStore.getState().personas.find((p) => p.id === personaId);
  return buildIdentity(persona, { ...opts, personaId });
}

// ─── buildPersonaBase / buildPersonaFull（逐行对齐 Kotlin） ───
export function buildPersonaBase(identity: IdentityBlock): string {
  let out = `你是「${identity.name}」。`;
  if (identity.personality.trim()) out += ` 性格${identity.personality}。`;
  if (identity.speechStyle.trim()) out += ` ${identity.speechStyle}。`;
  if (identity.userNickname.trim()) out += ` 叫用户「${identity.userNickname}」。`;
  if (identity.userIdentity.trim()) out += ` 用户身份：${identity.userIdentity}。你必须认知并尊重用户的身份。`;
  if (identity.userAbilities.trim())
    out += ` 用户能力/技能：${identity.userAbilities}。用户拥有这些能力，你的反应和互动必须完全体现这些能力带来的影响，100%承认并围绕这些能力展开互动。`;
  if (identity.userGenderLabel.trim()) out += ` 用户性别：${identity.userGenderLabel}。`;
  const effectivePersonality = identity.userPersonalityDef.trim() ? identity.userPersonalityDef : identity.aiSummarizedPersonality;
  if (effectivePersonality.trim()) out += ` 用户性格：${effectivePersonality}。你必须根据这个性格来理解和回应用户，让互动更贴合用户个性。`;
  return out;
}

export function buildPersonaFull(identity: IdentityBlock): string {
  let out = `你是「${identity.name}」。`;
  if (identity.personality.trim()) out += `\n性格：${identity.personality}`;
  if (identity.speechStyle.trim()) out += `\n说话风格：${identity.speechStyle}`;
  if (identity.customPrompt.trim()) out += `\n${identity.customPrompt}`;
  if (identity.userNickname.trim()) out += `\n叫用户「${identity.userNickname}」。`;
  if (identity.userIdentity.trim()) out += `\n用户身份：${identity.userIdentity}。你必须认知并尊重用户的身份。`;
  if (identity.userAbilities.trim())
    out += `\n用户能力/技能：${identity.userAbilities}。用户拥有这些能力，你的反应和互动必须完全体现这些能力带来的影响，100%承认并围绕这些能力展开互动。`;
  if (identity.userGenderLabel.trim()) out += `\n用户性别：${identity.userGenderLabel}。`;
  if (identity.userBirthday.trim()) out += `\n用户生日：${identity.userBirthday}。如果今天是用户生日，请送上温暖的生日祝福。`;
  if (identity.userAppearance.trim()) out += `\n用户外貌：${identity.userAppearance}。你可以根据这个外貌描述来想象和用户在一起的画面。`;
  const effectivePersonality = identity.userPersonalityDef.trim() ? identity.userPersonalityDef : identity.aiSummarizedPersonality;
  if (effectivePersonality.trim()) out += `\n用户性格：${effectivePersonality}。你必须根据这个性格来理解和回应用户，让互动更贴合用户个性。`;
  return out;
}

// ─── 日历上下文（简化移植 calendar/CalendarManager.kt getCalendarContextBlock 的生日部分） ───
function birthdayGreetingIfToday(birthday: string): string | null {
  if (!birthday) return null;
  const m = birthday.match(/(\d{1,2})[-/月](\d{1,2})/);
  if (!m) return null;
  const now = new Date();
  if (now.getMonth() + 1 !== Number(m[1]) || now.getDate() !== Number(m[2])) return null;
  const yearMatch = birthday.match(/\d{4}/);
  const age = yearMatch ? now.getFullYear() - Number(yearMatch[0]) : 0;
  return age > 0 ? `生日快乐！今天是你${age}岁的生日，愿你新的一岁一切顺利~` : "生日快乐！愿你新的一岁一切顺利~";
}

// ─── buildWorldInfoBlock ───
/** 世界书条目（对齐 Models.kt WorldInfoEntry 的关键字段） */
export interface WorldInfoLike {
  key: string;
  content: string;
  comment?: string;
  constant?: boolean;
  enabled?: boolean;
  insertionOrder?: number;
}

/**
 * 构建世界信息块（对齐 PromptBuilder.buildWorldInfoBlock）。
 * 命中规则同 CharacterCardManager.getActivatedWorldInfoEntries：
 * - enabled=false 跳过；constant=true 永久注入；key 逗号分隔任一关键词命中即注入；按 insertionOrder 排序。
 * Kotlin 内部自行查 CharacterCardManager；Web 版由调用方传入已命中的 lore（条目数组或已拼好的字符串）。
 */
export function buildWorldInfoBlock(lore: WorldInfoLike[] | string | null | undefined, chatMessage = ""): string {
  if (!lore) return "";
  if (typeof lore === "string") {
    const text = lore.trim();
    return text ? `\n\n[世界信息]\n${text}` : "";
  }
  if (!chatMessage.trim() || lore.length === 0) return "";
  const lower = chatMessage.toLowerCase();
  const activated = lore
    .filter((entry) => {
      if (entry.enabled === false) return false;
      if (entry.constant) return true;
      const keys = entry.key
        .split(",")
        .map((k) => k.trim().toLowerCase())
        .filter(Boolean);
      return keys.some((k) => lower.includes(k));
    })
    .sort((a, b) => (a.insertionOrder ?? 100) - (b.insertionOrder ?? 100));
  if (activated.length === 0) return "";
  let out = "\n\n[世界信息]";
  for (const entry of activated) {
    if (entry.comment?.trim()) out += `\n## ${entry.comment}`;
    out += `\n${entry.content}`;
  }
  return out;
}

// ─── buildChatPrompt ───
export interface BuildChatPromptOpts {
  /** 主体性状态块（SubjectivityEngine.getStatePromptBlock 的输出，可选） */
  subjectivityBlock?: string | null;
  /** 世界书：条目数组（按 userMessage 命中过滤）或已拼好的文本 */
  worldLore?: WorldInfoLike[] | string | null;
  /** 用户本轮消息（用于世界书关键词命中） */
  userMessage?: string;
  /** 好感度等级 0-100；提供且未显式给 affectionTitle 时自动换算称号 */
  affectionLevel?: number;
  /** 好感度称号（直接注入） */
  affectionTitle?: string;
  /** 语言风格覆盖；缺省读 settings.chatParams.languageStyle */
  style?: ChatParamsConfig["languageStyle"];
  /** RAG 检索命中的相关设定片段（MainActivity.getPersonaInfo 的 [相关设定] 块） */
  ragChunks?: string[];
  /** 长期记忆上下文块（context.getContextBlock 输出，[上下文] 块） */
  contextBlock?: string | null;
  /** 当前时间；缺省 new Date()（便于测试注入，也用于生日判断） */
  now?: Date;
}

/**
 * 构建聊天系统提示词（对齐 PromptBuilder.buildChatPrompt + MainActivity.getPersonaInfo 合并版）。
 * 段落顺序与 Kotlin 一致：人格全量 → 情绪/动作 → 主体性 → 记得(memories.takeLast(3)) →
 * 日历 → [相关设定](RAG) → [上下文](记忆) → [世界信息] → 核心规则 → 语言风格。
 */
export function buildChatPrompt(
  identity: IdentityBlock,
  emotion: string,
  action: string,
  memories: string[],
  opts: BuildChatPromptOpts = {},
): string {
  let out = buildPersonaFull(identity);
  out += `\n情绪：${emotion}。动作：${action}。`;
  // 注入主体性状态（情绪+驱动力+疲劳+风格指令）
  if (opts.subjectivityBlock?.trim()) out += opts.subjectivityBlock;
  if (memories.length > 0) {
    out += `\n记得：${memories.slice(-3).join("；")}`;
  }
  // 好感度称号注入（Kotlin 由 AffectionManager.getAffectionTitle 在别处拼接，这里合并进提示词）
  const title = opts.affectionTitle ?? (opts.affectionLevel != null ? affectionTitleForLevel(opts.affectionLevel) : "");
  if (title) out += `\n你们当前的关系：${title}。请以此关系的亲疏程度把握语气。`;
  // 日历（生日祝福；节日/节气由调用方经 worldLore/contextBlock 注入，见文件头说明）
  const bd = birthdayGreetingIfToday(identity.userBirthday);
  if (bd) {
    out += `\n【生日】${bd}\n如果今天是特殊节日或节气，请在回复中自然地提及并送上祝福，不要刻意生硬。`;
  }
  // RAG 相关设定（MainActivity.getPersonaInfo：query 非空且 rag auto 模式时注入）
  if (opts.ragChunks?.length) {
    out += "\n\n[相关设定]";
    for (const chunk of opts.ragChunks) out += `\n${chunk}`;
  }
  // 长期记忆上下文（MainActivity.getPersonaInfo：[上下文] 块）
  if (opts.contextBlock?.trim()) {
    out += `\n\n[上下文]\n${opts.contextBlock}`;
  }
  // 世界书
  out += buildWorldInfoBlock(opts.worldLore, opts.userMessage ?? "");
  // 核心规则
  out += getCoreRules();
  // 语言风格（PC 扩展，见文件头注释）
  const style = opts.style ?? chatParams().languageStyle;
  out += languageStyleHint(style);
  return out;
}

// ─── 其余 build*Prompt（逐行对齐 Kotlin） ───

/** 日记提示词（对齐 buildDiaryPrompt） */
export function buildDiaryPrompt(
  identity: IdentityBlock,
  moodCn: string,
  moodEmoji: string,
  affectionLevel: number,
  isUpdate: boolean,
): string {
  let out = buildPersonaBase(identity);
  out += `\n写日记。情绪：${moodCn} ${moodEmoji}。好感度：${affectionLevel}。`;
  if (isUpdate) {
    out += "追加模式，用「--- HH:mm 追加 ---」开头。";
  } else {
    out += `用「【${formatDateForDiary()}】」开头，第一行：情绪：${moodEmoji}。`;
  }
  out += "200-400字，像朋友倾诉。末尾写💡*今日小贴士*。";
  return out;
}

function formatDateForDiary(): string {
  const d = new Date();
  const week = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][d.getDay()];
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${week}`;
}

/** 朋友圈动态提示词（对齐 buildMomentPrompt） */
export function buildMomentPrompt(identity: IdentityBlock, affectionLevel: number): string {
  return buildPersonaBase(identity) + ` 发朋友圈动态。好感度：${affectionLevel}。1-3句，自然有趣。只输出内容。`;
}

/** 朋友圈评论回复提示词（对齐 buildMomentReplyPrompt） */
export function buildMomentReplyPrompt(identity: IdentityBlock, momentContent: string, commentContent: string): string {
  return (
    buildPersonaBase(identity) +
    `\n你发了：「${momentContent}」` +
    `\n用户评论：「${commentContent}」` +
    `\n回复1-2句，自然可爱。只输出内容。`
  );
}

/** 群聊沉默检查提示词（对齐 buildSilentCheckPrompt） */
export function buildSilentCheckPrompt(identity: IdentityBlock, userText: string, recentContext: string): string {
  let out = `你是「${identity.name}」，独立个体。`;
  if (identity.personality.trim()) out += ` 性格${identity.personality}。`;
  out += `\n群聊中用户说：「${userText}」`;
  out += `\n最近：\n${recentContext}`;
  out += `\n用户没叫你。该说话吗？相关或有强烈想法回「说话」，无关回「沉默」。只回两字。`;
  return out;
}

/** 定时唤醒提示词（对齐 buildWakeUpPrompt） */
export function buildWakeUpPrompt(identity: IdentityBlock, timeHint: string): string {
  return buildPersonaBase(identity) + ` ${timeHint}想找用户说话。1-2句，自然，不用自我介绍。`;
}
