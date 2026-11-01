/**
 * 皮肤商店引擎 —— 移植自 theme/BubbleSkinManager.kt + ui/SkinShopActivity.kt
 * Android 端目录情况：
 * - 气泡皮肤 builtinSkins（default/glass/neon/candy/mint）完整照抄，颜色值取自 Kotlin Color.parseColor；
 * - 头像框 builtinFrames（default/pink_glow/gold/cyber/none）完整照抄 → 映射为 SkinRecord(type=avatarFrame)；
 * - 图片型气泡/头像框在 Android 从 assets 的 bubble_metadata.json / frame_metadata.json 动态读取
 *   （SkinShopActivity 只是空壳列表，无硬编码目录、无价格），故按任务要求另行设计一套含 CSS style
 *   数据的 UI 皮肤默认目录（见 BUILTIN_UI_SKINS，注明为 PC 自研补充）。
 * 经济系统：购买扣 useSettingsStore.settings.coins（星尘币），不足返回 false；
 * 拥有/装备状态持久化于 db collections skins / bubbleSkins（id = "owned:"+目录id）。
 */

import { getAll, getOne, put, kvGet, kvSet } from "@/lib/db";
import type { BubbleSkinRecord, SkinRecord } from "./kinds";
import { emit } from "@/lib/events";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { usePersonaStore } from "@/stores/usePersonaStore";

// ─── 目录定义 ───

interface FrameDef { id: string; name: string; strokeColor: string; strokeWidth: number; glowColor?: string; glowRadius?: number; price: number }
interface BubbleDef {
  id: string; name: string; price: number;
  userBg: string; petBg: string; userText: string; petText: string; radius: number; border?: string; pattern?: string;
}

/** 头像框目录（抄自 BubbleSkinManager.builtinFrames，价格为本端商品化补充） */
const FRAME_DEFS: FrameDef[] = [
  { id: "default", name: "默认", strokeColor: "#667eea", strokeWidth: 1.5, price: 0 },
  { id: "pink_glow", name: "粉光", strokeColor: "#ff6b9d", strokeWidth: 2, glowColor: "#44ff6b9d", glowRadius: 6, price: 120 },
  { id: "gold", name: "金边", strokeColor: "#FFD700", strokeWidth: 2, glowColor: "#33FFD700", glowRadius: 4, price: 300 },
  { id: "cyber", name: "赛博", strokeColor: "#00e5ff", strokeWidth: 1.5, glowColor: "#2200e5ff", glowRadius: 8, price: 200 },
  { id: "none", name: "无框", strokeColor: "transparent", strokeWidth: 0, price: 0 },
];

/** 气泡皮肤目录（抄自 builtinSkins；Android ai=AI侧 → PC petBg；aiAlpha 已折算进色值 alpha 通道） */
const BUBBLE_DEFS: BubbleDef[] = [
  { id: "default", name: "默认", price: 0, userBg: "#ff6b9d", petBg: "#BB4a2a3a", userText: "#FFFFFF", petText: "#E0E0E0", radius: 18 },
  { id: "glass", name: "毛玻璃", price: 150, userBg: "#66ff6b9d", petBg: "#881a1a3e", userText: "#FFFFFF", petText: "#E0E0E0", radius: 20, border: "1px solid #33667eea" },
  { id: "neon", name: "霓虹", price: 250, userBg: "#1a1a2e", petBg: "#1a1a2e", userText: "#FFFFFF", petText: "#FFFFFF", radius: 16, border: "1.5px solid #00e5ff" },
  { id: "candy", name: "糖果", price: 180, userBg: "linear-gradient(135deg, #FF6B6B, #FFB347)", petBg: "#BBE8DAEF", userText: "#1A1A2E", petText: "#1A1A2E", radius: 22, border: "1px solid #D4A5FF" },
  { id: "mint", name: "薄荷", price: 120, userBg: "#2ECC71", petBg: "#BB1a2e2e", userText: "#FFFFFF", petText: "#E0E0E0", radius: 14, border: "0.5px solid #2ECC71" },
];

/**
 * UI 主题皮肤默认目录（PC 自研，共 7 款）——
 * Android 端 SkinShopActivity 仅展示 assets 图片九宫格且仓库未附带元数据文件，属空壳，
 * 故此处设计含 CSS style 数据的合理默认目录。
 */
export const BUILTIN_UI_SKINS: SkinRecord[] = [
  { id: "ui_sakura", name: "樱花庭院", type: "ui", price: 200, preview: "🌸", owned: false, equipped: false, config: { cssVars: { "--sd-primary": "#f472b6", "--sd-bg": "#fff1f2", "--sd-surface": "#ffffff" }, fontHint: "serif" } },
  { id: "ui_deepsea", name: "深海星空", type: "ui", price: 260, preview: "🌊", owned: false, equipped: false, config: { cssVars: { "--sd-primary": "#38bdf8", "--sd-bg": "#0f172a", "--sd-surface": "#1e293b" }, fontHint: "sans-serif" } },
  { id: "ui_forest", name: "苔原森林", type: "ui", price: 180, preview: "🌲", owned: false, equipped: false, config: { cssVars: { "--sd-primary": "#4ade80", "--sd-bg": "#f0fdf4", "--sd-surface": "#ffffff" }, fontHint: "sans-serif" } },
  { id: "ui_violet", name: "紫电幻境", type: "ui", price: 300, preview: "🔮", owned: false, equipped: false, config: { cssVars: { "--sd-primary": "#a78bfa", "--sd-bg": "#1e1b4b", "--sd-surface": "#312e81" }, fontHint: "monospace" } },
  { id: "ui_sunset", name: "落日熔金", type: "ui", price: 220, preview: "🌇", owned: false, equipped: false, config: { cssVars: { "--sd-primary": "#fb923c", "--sd-bg": "#fff7ed", "--sd-surface": "#ffffff" }, fontHint: "serif" } },
  { id: "ui_matcha", name: "抹茶拿铁", type: "ui", price: 160, preview: "🍵", owned: false, equipped: false, config: { cssVars: { "--sd-primary": "#a3b18a", "--sd-bg": "#fefae0", "--sd-surface": "#ffffff" }, fontHint: "serif" } },
  { id: "ui_midnight", name: "午夜蓝调", type: "ui", price: 0, preview: "🌙", owned: false, equipped: false, config: { cssVars: { "--sd-primary": "#818cf8", "--sd-bg": "#111827", "--sd-surface": "#1f2937" }, fontHint: "sans-serif" } },
];

// ─── 通用工具 ───

function currentCoins(): number {
  return useSettingsStore.getState().settings.coins ?? 0;
}

const OWNED_PREFIX = "owned:";
const EQUIPPED_ID = "equipped:active";

async function spendCoins(amount: number): Promise<boolean> {
  const store = useSettingsStore.getState();
  if (currentCoins() < amount) return false;
  store.updateSettings({ coins: currentCoins() - amount });
  return true;
}

// ─── 头像框 / UI 皮肤（SkinRecord） ───

/** 全部可购皮肤目录（头像框 + 内置 UI 皮肤） */
export function listSkins(): SkinRecord[] {
  const frames: SkinRecord[] = FRAME_DEFS.map((f) => ({
    id: f.id,
    name: f.name,
    type: "avatarFrame",
    price: f.price,
    preview: "🖼️",
    config: { strokeColor: f.strokeColor, strokeWidth: f.strokeWidth, glowColor: f.glowColor ?? "transparent", glowRadius: f.glowRadius ?? 0 },
    owned: false,
    equipped: false,
  }));
  return [...frames, ...BUILTIN_UI_SKINS.map((s) => ({ ...s }))];
}

/** 是否已拥有（免费项恒为已拥有） */
export async function isSkinOwned(id: string): Promise<boolean> {
  const def = listSkins().find((s) => s.id === id);
  if (!def) return false;
  if (def.price === 0) return true;
  return !!(await getOne("skins", OWNED_PREFIX + id));
}

/** 购买皮肤：扣星尘币，余额不足返回 false */
export async function purchaseSkin(id: string): Promise<boolean> {
  const def = listSkins().find((s) => s.id === id);
  if (!def) return false;
  if (await isSkinOwned(id)) return true;
  if (!(await spendCoins(def.price))) return false;
  await put("skins", { ...def, id: OWNED_PREFIX + id, owned: true });
  emit("data-changed", { skins: id });
  return true;
}

/**
 * 装备皮肤：
 * - avatarFrame 类写入当前活跃 persona 的 avatarFrame 字段；
 * - ui 类写入 settings.appearance.theme 承载的自定义主题名（cssVars 由页面层读取应用）。
 * 未拥有则先尝试免费/购买守卫：装备免费项无需拥有。
 */
export async function equipSkin(id: string): Promise<boolean> {
  const def = listSkins().find((s) => s.id === id);
  if (!def) return false;
  if (!(await isSkinOwned(id))) return false;
  // 记录装备态（config.__sid 保存目录 id，供 getEquippedSkinId 还原）
  await put("skins", { ...def, id: EQUIPPED_ID, owned: true, equipped: true, config: { ...def.config, __sid: id } });
  if (def.type === "avatarFrame") {
    const store = usePersonaStore.getState();
    const p = store.activePersona ?? store.personas[0];
    if (p) store.updatePersona(p.id, { avatarFrame: id } as never);
  } else {
    // UI 皮肤：以主题名形式落到外观配置，页面层依据 BUILTIN_UI_SKINS 的 cssVars 渲染
    const { settings } = useSettingsStore.getState();
    useSettingsStore.getState().updateSettings({ appearance: { ...settings.appearance, theme: `custom:${id}` as typeof settings.appearance.theme } });
  }
  emit("data-changed", { equippedSkin: id });
  return true;
}

/** 当前装备的皮肤 id（"" = 未装备自定义皮肤） */
export async function getEquippedSkinId(): Promise<string> {
  const rec = await getOne<SkinRecord>("skins", EQUIPPED_ID);
  if (!rec || !rec.equipped) return "";
  return typeof rec.config?.__sid === "string" ? rec.config.__sid : "";
}

// ─── 气泡皮肤（BubbleSkinRecord） ───

/** 气泡皮肤目录（含 CSS style 数据） */
export function listBubbleSkins(): BubbleSkinRecord[] {
  return BUBBLE_DEFS.map((b) => ({
    id: b.id,
    name: b.name,
    price: b.price,
    style: { userBg: b.userBg, petBg: b.petBg, userText: b.userText, petText: b.petText, radius: b.radius, border: b.border, pattern: b.pattern },
    owned: b.price === 0,
  }));
}

/** 是否已拥有某气泡皮肤 */
export async function isBubbleSkinOwned(id: string): Promise<boolean> {
  const def = BUBBLE_DEFS.find((b) => b.id === id);
  if (!def) return false;
  if (def.price === 0) return true;
  return !!(await getOne("bubbleSkins", OWNED_PREFIX + id));
}

/** 购买气泡皮肤（扣币，不足 false） */
export async function purchaseBubbleSkin(id: string): Promise<boolean> {
  const rec = listBubbleSkins().find((s) => s.id === id);
  if (!rec) return false;
  if (await isBubbleSkinOwned(id)) return true;
  if (!(await spendCoins(rec.price))) return false;
  await put("bubbleSkins", { ...rec, id: OWNED_PREFIX + id, owned: true });
  emit("data-changed", { bubbleSkins: id });
  return true;
}

/** 装备气泡皮肤：写全局激活键 + 当前 persona.bubbleSkin（Kotlin setActiveSkin 语义） */
export async function equipBubbleSkin(id: string): Promise<boolean> {
  if (!(await isBubbleSkinOwned(id))) return false;
  const rec = listBubbleSkins().find((s) => s.id === id);
  if (!rec) return false;
  await kvSet("bubble_skin_active", id); // 对齐 Kotlin prefs active_bubble_skin
  const store = usePersonaStore.getState();
  const p = store.activePersona ?? store.personas[0];
  if (p) store.updatePersona(p.id, { bubbleSkin: id } as never);
  emit("data-changed", { equippedBubbleSkin: id });
  return true;
}

/** 当前使用中的气泡皮肤 id（默认 "default"，对齐 Kotlin getActiveSkin 兜底） */
export async function getActiveBubbleSkinId(): Promise<string> {
  const id = await kvGet<string>("bubble_skin_active");
  return id && BUBBLE_DEFS.some((b) => b.id === id) ? id : "default";
}

/** 当前使用中的气泡皮肤完整样式（供聊天页渲染） */
export async function getActiveBubbleSkin(): Promise<BubbleSkinRecord | null> {
  const id = await getActiveBubbleSkinId();
  return listBubbleSkins().find((s) => s.id === id) ?? null;
}

/** 拥有列表（合并目录与存档，供商店 UI 显示 已拥有/使用中） */
export async function getOwnedSummary(): Promise<{ skinIds: string[]; bubbleSkinIds: string[] }> {
  const skins = await getAll<SkinRecord>("skins");
  const bubbles = await getAll<BubbleSkinRecord>("bubbleSkins");
  const free = listSkins().filter((s) => s.price === 0).map((s) => s.id);
  const freeB = listBubbleSkins().filter((s) => s.price === 0).map((s) => s.id);
  return {
    skinIds: [...new Set([...free, ...skins.filter((s) => s.id.startsWith(OWNED_PREFIX)).map((s) => s.id.slice(OWNED_PREFIX.length))])],
    bubbleSkinIds: [...new Set([...freeB, ...bubbles.filter((s) => s.id.startsWith(OWNED_PREFIX)).map((s) => s.id.slice(OWNED_PREFIX.length))])],
  };
}
