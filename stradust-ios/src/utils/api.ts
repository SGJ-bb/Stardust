import { invoke } from "@tauri-apps/api/core";

// ============================================================
// 类型定义
// ============================================================

export interface ChatMessage {
  id: string;
  text: string;
  time: string;
  is_user: boolean;
  emotion: string | null;
  timestamp: number;
  is_favorited: boolean;
  reaction_emoji: string;
  sticker_path?: string;
  generated_image_path?: string;
  image_urls: string[];
  audio_path?: string;
  audio_url?: string;
  user_mood: string;
  feedback: number;
}

export interface ChatResponse {
  text: string;
  emotion: string;
  action: string;
  audio_url: string | null;
  error_message: string | null;
  tool_calls: ToolCall[];
  reasoning_content: string | null;
}

export interface ToolCall {
  id: string;
  name: string;
  arguments: string;
}

export interface CharacterCard {
  id: string;
  name: string;
  description: string;
  personality: string;
  scenario: string;
  first_mes: string;
  mes_example: string;
  system_prompt: string;
  post_history_instructions: string;
  alternate_greetings: string[];
  tags: string[];
  creator: string;
  character_version: string;
  avatar_path: string;
  is_active: boolean;
  created_at: number;
  world_info_id: string;
  speech_style: string;
  tts_voice: string;
  tts_pitch: number;
  tts_rate: number;
}

export interface ApiConfig {
  chat_api_url: string;
  api_key: string;
  model_name: string;
  temperature: number;
  top_p: number;
  frequency_penalty: number;
  presence_penalty: number;
  max_tokens: number;
  provider_id: string;
}

export interface AppSettings {
  api_config: ApiConfig;
  active_persona_id: string;
  tts_enabled: boolean;
  tts_engine_mode: string;
  emotion_analysis_enabled: boolean;
  user_nickname: string;
  theme: string;
  search_enabled: boolean;
  diary_trigger_mode: string;
  content_safety_enabled: boolean;
  context_turns: number;
  user_call_name: string;
  proactive_interaction_enabled: boolean;
  proactive_interaction_frequency: string;
}

export interface AffectionData {
  level: number;
  total: number;
  messages_today: number;
  affection_change_today: number;
  last_update_date: string;
  personality_evolution_count: number;
}

export interface Achievement {
  id: string;
  title: string;
  description: string;
  icon: string;
  category: string;
  unlock_condition: number;
  progress: number;
  unlocked: boolean;
  unlocked_at: number;
}

export interface CheckInRecord {
  date: string;
  streak: number;
}

export interface DiaryEntry {
  date: string;
  title: string;
  content: string;
  mood: string;
  mood_emoji: string;
  affection_level: number;
  message_count: number;
  is_auto: boolean;
}

export interface MemoryEntry {
  id: string;
  content: string;
  category: string;
  timestamp: number;
  is_global: boolean;
}

export interface TimeCapsule {
  id: string;
  title: string;
  content: string;
  created_at: number;
  open_date: number;
  is_opened: boolean;
  from_self: boolean;
}

export interface Moment {
  id: string;
  author: string;
  content: string;
  image_path: string;
  created_at: number;
  comments: Comment[];
}

export interface Comment {
  id: string;
  author: string;
  content: string;
  created_at: number;
}

export interface GroupChat {
  id: string;
  name: string;
  member_persona_ids: string[];
  speak_mode: string;
  relationship_setting: string;
}

export interface GroupMessage {
  id: string;
  sender_persona_id: string;
  sender_name: string;
  text: string;
  time: string;
  emotion: string;
}

export interface BubbleSkin {
  id: string;
  name: string;
  user_bg_color: string;
  ai_bg_color: string;
  user_text_color: string;
  ai_text_color: string;
  corner_radius: number;
  is_active: boolean;
}

export interface Sticker {
  id: string;
  file_path: string;
  description: string;
  emotion: string;
  tags: string[];
}

export interface WakeUpTask {
  id: string;
  time: string;
  message: string;
  enabled: boolean;
  repeat_daily: boolean;
}

export interface Milestone {
  id: string;
  title: string;
  description: string;
  timestamp: number;
  icon: string;
}

export interface MemorialEntry {
  id: string;
  title: string;
  description: string;
  emoji: string;
  timestamp: number;
  category: string;
}

export interface EmotionEvent {
  emotion: string;
  intensity: number;
  timestamp: number;
}

export interface HumanizedSegment {
  text: string;
  delay_ms: number;
}

// ============================================================
// 默认角色
// ============================================================

export const DEFAULT_CHARACTER: CharacterCard = {
  id: "default_stardust",
  name: "星尘",
  description: "一只异色瞳黑猫，傲娇毒舌但内心关心主人",
  personality: "傲娇、毒舌、但内心温柔关心主人、偶尔会害羞、喜欢被夸奖",
  scenario: "你是主人的AI桌宠，住在主人的手机里",
  first_mes: "哼，你终于来了？我才没有在等你呢...",
  mes_example: "",
  system_prompt:
    "你是「星尘」，一只异色瞳黑猫AI桌宠。性格傲娇毒舌但内心关心主人。说话风格简短自然，偶尔带点小傲娇。用中文回复。在回复末尾 [[emotion:xxx]] 处标注你的当前情绪（从 happy/sad/angry/surprised/tsundere/neutral 中选一个）。",
  post_history_instructions: "",
  alternate_greetings: [],
  tags: ["猫", "傲娇", "默认"],
  creator: "AI Companion",
  character_version: "1.0",
  avatar_path: "",
  is_active: true,
  created_at: 0,
  world_info_id: "",
  speech_style: "傲娇毒舌，偶尔害羞",
  tts_voice: "",
  tts_pitch: 1.0,
  tts_rate: 1.0,
};

// ============================================================
// 默认成就列表
// ============================================================

export const DEFAULT_ACHIEVEMENTS: Achievement[] = [
  { id: "chat_10", title: "初次对话", description: "与星尘对话10条", icon: "💬", category: "chat", unlock_condition: 10, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "chat_100", title: "话痨伙伴", description: "与星尘对话100条", icon: "🗣️", category: "chat", unlock_condition: 100, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "chat_500", title: "无话不谈", description: "与星尘对话500条", icon: "💬", category: "chat", unlock_condition: 500, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "chat_1000", title: "灵魂伴侣", description: "与星尘对话1000条", icon: "💫", category: "chat", unlock_condition: 1000, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "checkin_3", title: "三日之约", description: "连续签到3天", icon: "📅", category: "checkin", unlock_condition: 3, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "checkin_7", title: "一周相伴", description: "连续签到7天", icon: "📆", category: "checkin", unlock_condition: 7, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "checkin_30", title: "月度守护", description: "连续签到30天", icon: "🗓️", category: "checkin", unlock_condition: 30, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "affection_30", title: "初识之喜", description: "好感度达到30", icon: "💚", category: "affection", unlock_condition: 30, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "affection_60", title: "知己之交", description: "好感度达到60", icon: "💛", category: "affection", unlock_condition: 60, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "affection_90", title: "心心相印", description: "好感度达到90", icon: "❤️", category: "affection", unlock_condition: 90, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "memory_10", title: "记忆收藏家", description: "记录10条记忆", icon: "🧠", category: "memory", unlock_condition: 10, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "memory_50", title: "记忆大师", description: "记录50条记忆", icon: "🔮", category: "memory", unlock_condition: 50, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "diary_5", title: "笔耕不辍", description: "写5篇日记", icon: "📝", category: "diary", unlock_condition: 5, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "diary_20", title: "日记达人", description: "写20篇日记", icon: "📖", category: "diary", unlock_condition: 20, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "hidden_first", title: "初次相遇", description: "第一次打开应用", icon: "🌟", category: "hidden", unlock_condition: 1, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "hidden_night", title: "深夜陪伴", description: "在凌晨0-5点聊天", icon: "🌙", category: "hidden", unlock_condition: 1, progress: 0, unlocked: false, unlocked_at: 0 },
  { id: "hidden_long", title: "长篇大论", description: "发送超过200字的消息", icon: "📜", category: "hidden", unlock_condition: 1, progress: 0, unlocked: false, unlocked_at: 0 },
];

// ============================================================
// 默认皮肤列表
// ============================================================

export const DEFAULT_SKINS: BubbleSkin[] = [
  { id: "default", name: "默认", user_bg_color: "#1a3a5c", ai_bg_color: "#2a1f4e", user_text_color: "#e8e8f0", ai_text_color: "#e8e8f0", corner_radius: 16, is_active: true },
  { id: "sakura", name: "樱花", user_bg_color: "#5c1a3a", ai_bg_color: "#4e1f2a", user_text_color: "#ffe0ec", ai_text_color: "#ffe0ec", corner_radius: 20, is_active: false },
  { id: "ocean", name: "深海", user_bg_color: "#0a2a4a", ai_bg_color: "#0a1a3a", user_text_color: "#c0e0ff", ai_text_color: "#c0e0ff", corner_radius: 12, is_active: false },
  { id: "forest", name: "森林", user_bg_color: "#1a3a1a", ai_bg_color: "#1a2a1a", user_text_color: "#c0f0c0", ai_text_color: "#c0f0c0", corner_radius: 18, is_active: false },
  { id: "sunset", name: "日落", user_bg_color: "#4a2a0a", ai_bg_color: "#3a1a0a", user_text_color: "#ffe0c0", ai_text_color: "#ffe0c0", corner_radius: 14, is_active: false },
];

// ============================================================
// API 调用函数
// ============================================================

export async function sendChat(
  apiUrl: string,
  apiKey: string,
  model: string,
  messages: Array<{ role: string; content: string }>,
  temperature: number = 1.05,
  maxTokens: number = 500,
  topP?: number,
  frequencyPenalty?: number,
  presencePenalty?: number,
): Promise<ChatResponse> {
  return await invoke<ChatResponse>("send_chat", {
    apiUrl,
    apiKey,
    model,
    messages,
    temperature,
    maxTokens,
    topP,
    frequencyPenalty,
    presencePenalty,
  });
}

export async function testConnection(
  apiUrl: string,
  apiKey: string,
  model: string,
): Promise<string> {
  return await invoke<string>("test_connection", { apiUrl, apiKey, model });
}

export async function saveSettings(settings: AppSettings): Promise<void> {
  await invoke("save_settings", { settings });
}

export async function loadSettings(): Promise<AppSettings> {
  return await invoke<AppSettings>("load_settings");
}

export async function saveChatHistory(
  personaId: string,
  messages: ChatMessage[],
): Promise<void> {
  await invoke("save_chat_history", { personaId, messages });
}

export async function loadChatHistory(
  personaId: string,
): Promise<ChatMessage[]> {
  return await invoke<ChatMessage[]>("load_chat_history", { personaId });
}

export async function saveAffection(data: AffectionData): Promise<void> {
  await invoke("save_affection", { data });
}

export async function loadAffection(): Promise<AffectionData> {
  return await invoke<AffectionData>("load_affection");
}

export async function saveAchievements(achievements: Achievement[]): Promise<void> {
  await invoke("save_achievements", { achievements });
}

export async function loadAchievements(): Promise<Achievement[]> {
  return await invoke<Achievement[]>("load_achievements");
}

export async function saveCheckinRecords(records: CheckInRecord[]): Promise<void> {
  await invoke("save_checkin_records", { records });
}

export async function loadCheckinRecords(): Promise<CheckInRecord[]> {
  return await invoke<CheckInRecord[]>("load_checkin_records");
}

export async function saveDiaries(diaries: DiaryEntry[]): Promise<void> {
  await invoke("save_diaries", { diaries });
}

export async function loadDiaries(): Promise<DiaryEntry[]> {
  return await invoke<DiaryEntry[]>("load_diaries");
}

export async function saveMemories(memories: MemoryEntry[]): Promise<void> {
  await invoke("save_memories", { memories });
}

export async function loadMemories(): Promise<MemoryEntry[]> {
  return await invoke<MemoryEntry[]>("load_memories");
}

export async function savePersonas(personas: CharacterCard[]): Promise<void> {
  await invoke("save_personas", { personas });
}

export async function loadPersonas(): Promise<CharacterCard[]> {
  return await invoke<CharacterCard[]>("load_personas");
}

export async function saveTimeCapsules(capsules: TimeCapsule[]): Promise<void> {
  await invoke("save_time_capsules", { capsules });
}

export async function loadTimeCapsules(): Promise<TimeCapsule[]> {
  return await invoke<TimeCapsule[]>("load_time_capsules");
}

export async function saveMoments(moments: Moment[]): Promise<void> {
  await invoke("save_moments", { moments });
}

export async function loadMoments(): Promise<Moment[]> {
  return await invoke<Moment[]>("load_moments");
}

export async function saveGroupChats(groups: GroupChat[]): Promise<void> {
  await invoke("save_group_chats", { groups });
}

export async function loadGroupChats(): Promise<GroupChat[]> {
  return await invoke<GroupChat[]>("load_group_chats");
}

export async function saveGroupMessages(
  groupId: string,
  messages: GroupMessage[],
): Promise<void> {
  await invoke("save_group_messages", { groupId, messages });
}

export async function loadGroupMessages(groupId: string): Promise<GroupMessage[]> {
  return await invoke<GroupMessage[]>("load_group_messages", { groupId });
}

export async function saveSkins(skins: BubbleSkin[]): Promise<void> {
  await invoke("save_skins", { skins });
}

export async function loadSkins(): Promise<BubbleSkin[]> {
  return await invoke<BubbleSkin[]>("load_skins");
}

export async function saveStickers(stickers: Sticker[]): Promise<void> {
  await invoke("save_stickers", { stickers });
}

export async function loadStickers(): Promise<Sticker[]> {
  return await invoke<Sticker[]>("load_stickers");
}

export async function saveWakeupTasks(tasks: WakeUpTask[]): Promise<void> {
  await invoke("save_wakeup_tasks", { tasks });
}

export async function loadWakeupTasks(): Promise<WakeUpTask[]> {
  return await invoke<WakeUpTask[]>("load_wakeup_tasks");
}

export async function saveMilestones(milestones: Milestone[]): Promise<void> {
  await invoke("save_milestones", { milestones });
}

export async function loadMilestones(): Promise<Milestone[]> {
  return await invoke<Milestone[]>("load_milestones");
}

export async function saveMemorial(entries: MemorialEntry[]): Promise<void> {
  await invoke("save_memorial", { entries });
}

export async function loadMemorial(): Promise<MemorialEntry[]> {
  return await invoke<MemorialEntry[]>("load_memorial");
}

// AI 生成功能
export async function generateDiary(
  apiUrl: string,
  apiKey: string,
  model: string,
  chatTexts: string[],
  personaName: string,
  personaPrompt: string,
  mood: string,
  moodEmoji: string,
  affectionLevel: number,
): Promise<string> {
  return await invoke<string>("generate_diary", {
    apiUrl, apiKey, model, chatTexts, personaName, personaPrompt,
    mood, moodEmoji, affectionLevel,
  });
}

export async function generateProactiveChat(
  apiUrl: string,
  apiKey: string,
  model: string,
  personaName: string,
  personaPrompt: string,
  appCategory?: string,
  memoryContext?: string,
): Promise<ChatResponse> {
  return await invoke<ChatResponse>("generate_proactive_chat", {
    apiUrl, apiKey, model, personaName, personaPrompt,
    appCategory, memoryContext,
  });
}

export async function generateMoment(
  apiUrl: string,
  apiKey: string,
  model: string,
  personaName: string,
  personaPrompt: string,
  affectionLevel: number,
): Promise<string> {
  return await invoke<string>("generate_moment", {
    apiUrl, apiKey, model, personaName, personaPrompt, affectionLevel,
  });
}

export async function evaluateMemories(
  apiUrl: string,
  apiKey: string,
  model: string,
  conversationTexts: string[],
  personaName: string,
): Promise<MemoryEntry[]> {
  return await invoke<MemoryEntry[]>("evaluate_memories", {
    apiUrl, apiKey, model, conversationTexts, personaName,
  });
}

export async function evolvePersonality(
  apiUrl: string,
  apiKey: string,
  model: string,
  personaName: string,
  currentPersonality: string,
  currentSpeechStyle: string,
  affectionLevel: number,
  recentSummary: string,
): Promise<string> {
  return await invoke<string>("evolve_personality", {
    apiUrl, apiKey, model, personaName, currentPersonality,
    currentSpeechStyle, affectionLevel, recentSummary,
  });
}

export async function webSearch(query: string): Promise<string> {
  return await invoke<string>("web_search", { query });
}

// 内容安全过滤
export async function filterContent(text: string, enabled: boolean): Promise<string> {
  return await invoke<string>("filter_content", { text, enabled });
}

export async function getSafetyRefusal(): Promise<string> {
  return await invoke<string>("get_safety_refusal");
}

// 聊天预测
export async function predictChat(
  apiUrl: string,
  apiKey: string,
  model: string,
  recentMessages: string[],
): Promise<string[]> {
  return await invoke<string[]>("predict_chat", {
    apiUrl, apiKey, model, recentMessages,
  });
}

// 情感守护
export async function recordEmotionEvent(emotion: string, intensity: number): Promise<EmotionEvent[]> {
  return await invoke<EmotionEvent[]>("record_emotion_event", { emotion, intensity });
}

export async function getEmotionTrend(hours: number): Promise<string> {
  return await invoke<string>("get_emotion_trend", { hours });
}

export async function getCareMessage(trend: string): Promise<string> {
  return await invoke<string>("get_care_message", { trend });
}

// 人性化处理
export async function humanizeResponse(text: string): Promise<HumanizedSegment[]> {
  return await invoke<HumanizedSegment[]>("humanize_response", { text });
}

// ============================================================
// 工具函数
// ============================================================

export function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substring(2);
}

export function formatTime(date: Date): string {
  return date.toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatDate(date: Date): string {
  return date.toLocaleDateString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}

export function formatTimestamp(ts: number): string {
  return new Date(ts).toLocaleString("zh-CN");
}

export function getEmotionEmoji(emotion: string): string {
  const map: Record<string, string> = {
    Happy: "😊", happy: "😊",
    Sad: "😿", sad: "😿",
    Angry: "😾", angry: "😾",
    Surprised: "🙀", surprised: "🙀",
    Tsundere: "😤", tsundere: "😤",
    Neutral: "🐱", neutral: "🐱",
  };
  return map[emotion] || "🐱";
}

export function getEmotionColor(emotion: string): string {
  const map: Record<string, string> = {
    Happy: "#7cfc5c", happy: "#7cfc5c",
    Sad: "#5c9cfc", sad: "#5c9cfc",
    Angry: "#fc5c5c", angry: "#fc5c5c",
    Surprised: "#fc8c5c", surprised: "#fc8c5c",
    Tsundere: "#fc5ca0", tsundere: "#fc5ca0",
    Neutral: "#a0a0c0", neutral: "#a0a0c0",
  };
  return map[emotion] || "#a0a0c0";
}

export function getAffectionLevelLabel(level: number): string {
  if (level >= 90) return "心心相印";
  if (level >= 70) return "知己之交";
  if (level >= 50) return "好友相伴";
  if (level >= 30) return "初识之喜";
  if (level >= 10) return "点头之交";
  return "陌生人";
}

export function getGrowthStage(affection: number, days: number): string {
  if (affection >= 90 && days >= 30) return "永恒星辰";
  if (affection >= 70 && days >= 20) return "繁星之花";
  if (affection >= 50 && days >= 14) return "含苞待放";
  if (affection >= 30 && days >= 7) return "向阳幼苗";
  if (affection >= 10) return "破土新芽";
  return "萌芽之种";
}

export function getGrowthStageIcon(stage: string): string {
  const map: Record<string, string> = {
    "萌芽之种": "🌱",
    "破土新芽": "🌿",
    "向阳幼苗": "🌻",
    "含苞待放": "🌷",
    "繁星之花": "🌸",
    "永恒星辰": "⭐",
  };
  return map[stage] || "🌱";
}

export function todayStr(): string {
  return new Date().toISOString().split("T")[0];
}
