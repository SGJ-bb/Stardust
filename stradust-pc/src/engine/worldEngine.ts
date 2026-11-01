/**
 * 虚拟世界引擎 —— 移植自 Android virtualworld/VirtualWorldManager.kt
 * 保留：虚拟时钟推进、单人/多人推演提示词、[[location:]][[weather:]][[mood:]] 状态标签协议、
 *       图片生成规则与"连续两条不重复配图"约束、推演事件同步聊天记忆池。
 */

import { chatCompletion, simpleCompletion, getActiveProvider, type ChatMsg } from "./llmClient";
import { buildIdentityById } from "./promptBuilder";
import { getAll, getByPersona, put, remove as dbRemove, newId } from "@/lib/db";
import { emit } from "@/lib/events";
import { usePersonaStore } from "@/stores/usePersonaStore";
import type { WorldRecord, WorldEventRecord } from "./kinds";
import { isImageGenConfigured, generateImage } from "./imagegen";
import { addMemoryFact } from "./context";

const TICK_VIRTUAL_MINUTES = 60; // 每 tick 推进 1 小时（对齐 Kotlin virtualMinutesAdvanced）

// ─── CRUD ───

export async function listWorlds(): Promise<WorldRecord[]> {
  const all = await getAll<WorldRecord>("worlds");
  return all.sort((a, b) => b.updatedAt - a.updatedAt);
}

export async function getWorld(worldId: string): Promise<WorldRecord | null> {
  return (await getAll<WorldRecord>("worlds")).find((w) => w.id === worldId) ?? null;
}

export async function createWorld(input: Omit<WorldRecord, "id" | "createdAt" | "updatedAt" | "state">): Promise<WorldRecord> {
  const record: WorldRecord = {
    ...input,
    id: newId("world"),
    createdAt: Date.now(),
    updatedAt: Date.now(),
    state: { day: 1, timeOfDay: "08:00", location: "家中", mood: "平静", flags: {}, lastTickAt: 0 },
  };
  await put("worlds", record);
  emit("data-changed", { collection: "worlds" });
  return record;
}

export async function updateWorld(world: WorldRecord): Promise<void> {
  await put("worlds", { ...world, updatedAt: Date.now() });
}

export async function deleteWorld(worldId: string): Promise<void> {
  await dbRemove("worlds", worldId);
  const events = await getByPersona<WorldEventRecord & { worldId: string }>("worldEvents", worldId).catch(() => [] as never[]);
  for (const e of events) await dbRemove("worldEvents", e.id);
  emit("data-changed", { collection: "worlds" });
}

export async function getStoryEvents(worldId: string): Promise<WorldEventRecord[]> {
  const all = await getAll<WorldEventRecord & { worldId: string }>("worldEvents");
  return all.filter((e) => e.worldId === worldId).sort((a, b) => a.timestamp - b.timestamp);
}

// ─── 时钟 ───

type WState = NonNullable<WorldRecord["state"]>;

function advanceTime(world: WorldRecord): WState {
  const s = world.state ?? { day: 1, timeOfDay: "08:00", location: "家中", mood: "平静", flags: {}, lastTickAt: 0 };
  const [h, m] = s.timeOfDay.split(":").map(Number);
  let total = h * 60 + m + TICK_VIRTUAL_MINUTES;
  let day = s.day;
  while (total >= 24 * 60) { total -= 24 * 60; day++; }
  return { ...s, day, timeOfDay: `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`, lastTickAt: Date.now() };
}

/**
 * 单次推演 tick：LLM 生成旁白 → 解析状态标签 → 存事件 →（可选）生成配图 → 同步聊天记忆。
 */
export async function runWorldTick(worldId: string, opts: { generateImage?: boolean } = {}): Promise<WorldEventRecord | null> {
  if (!getActiveProvider()) return null;
  const world = await getWorld(worldId);
  if (!world) return null;
  const state = advanceTime(world);
  const timeDesc = `第${state.day}天 ${state.timeOfDay}`;
  const recentEvents = (await getStoryEvents(worldId)).slice(-5)
    .map((e) => `[第${e.day}天${e.time.slice(0, 2)}时] ${e.characters.join("、") || world.name}：${e.content}`)
    .join("\n");
  const lastImageEvent = (await getStoryEvents(worldId)).slice(-1)[0];
  const canUseImage = (opts.generateImage ?? world.autoTick) && isImageGenConfigured() && !lastImageEvent?.imageUrl;

  const multiMember = world.characters.length > 1;
  let systemPrompt: string;
  if (multiMember) {
    const personaDescs = world.characters.map((c) => {
      const p = usePersonaStore.getState().personas.find((x) => x.id === c.id);
      return `「${p?.name ?? c.name}」${p?.personality ?? c.role}。`;
    }).join(" ");
    systemPrompt = [
      `你是虚拟世界的旁白。以下角色在这个世界中活动：${personaDescs}`,
      `【世界观】${world.lore || world.description}`,
      `【当前状态】时间：${timeDesc}。地点：${state.location}。氛围：${state.mood}。`,
      recentEvents ? `【近期事件】\n${recentEvents}` : "",
      `【规则】推演这个时刻发生的事。描述2-3个角色的行动和互动，像小说片段。末尾用[[location:地点]][[weather:天气]][[mood:氛围]]标记状态变化。`,
      canUseImage ? IMAGE_RULES : "",
    ].filter(Boolean).join("\n");
  } else {
    const pid = world.characters[0]?.id;
    const identity = pid ? buildIdentityById(pid) : null;
    systemPrompt = [
      `你是「${identity?.name ?? world.name}」，生活在虚拟世界中。${identity?.personality ? ` 性格${identity.personality}。` : ""}`,
      `【世界观】${world.lore || world.description}`,
      `【当前状态】时间：${timeDesc}。地点：${state.location}。氛围：${state.mood}。`,
      recentEvents ? `【近期事件】\n${recentEvents}` : "",
      `【规则】根据当前时间和状态，描述你在这个时刻做了什么、遇到了什么、想了什么。1-3句话，像小说旁白。末尾用[[location:地点]][[weather:天气]][[mood:氛围]]标记状态变化。`,
      canUseImage ? IMAGE_RULES : "",
    ].filter(Boolean).join("\n");
  }

  try {
    let text: string;
    let toolImagePrompt: string | null = null;
    if (canUseImage) {
      // 带 generate_image 工具的推演（LLM 自主决定配图时机）
      const r = await chatCompletion(
        [{ role: "system", content: systemPrompt }, { role: "user", content: "继续推演" }] as ChatMsg[],
        {
          tools: [{
            name: "generate_image",
            description: "根据文字描述生成一张图片",
            parameters: {
              type: "object",
              properties: {
                prompt: { type: "string", description: "图片描述，英文效果更好" },
                style: { type: "string", description: "风格提示，可选" },
              },
              required: ["prompt"],
            },
          }],
        },
      );
      text = r.text;
      const imgCall = r.toolCalls.find((t) => t.name === "generate_image");
      if (imgCall) {
        try { toolImagePrompt = String((JSON.parse(imgCall.arguments) as { prompt?: string }).prompt ?? ""); } catch { /* ignore */ }
      }
    } else {
      text = await simpleCompletion(systemPrompt, { system: "继续推演" });
    }
    if (!text?.trim()) return null;

    let content = text.replace(/\[\[emotion:\w+\]\]/g, "").trim();
    const pick = (tag: string, fallback: string) => content.match(new RegExp(`\\[\\[${tag}:([^\\]]+)\\]\\]`))?.[1]?.trim() ?? fallback;
    const newLocation = pick("location", state.location);
    const newWeather = pick("weather", (state.flags?.weather as string) ?? "晴");
    const newMood = pick("mood", state.mood);
    content = content
      .replace(/\[\[location:[^\]]+\]\]/g, "")
      .replace(/\[\[weather:[^\]]+\]\]/g, "")
      .replace(/\[\[mood:[^\]]+\]\]/g, "")
      .trim();

    // 配图：工具请求或（未用工具时按规则）直接生成
    let imageUrl: string | undefined;
    if (toolImagePrompt && content.length > 50) {
      imageUrl = await generateImage(toolImagePrompt).catch(() => undefined);
    }

    const speakers = multiMember ? world.characters.map((c) => c.name) : [world.characters[0]?.name ?? world.name];
    const event: WorldEventRecord & { worldId: string } = {
      id: newId("wevent"), worldId, day: state.day, time: state.timeOfDay,
      content, imageUrl, characters: speakers, timestamp: Date.now(),
    };
    await put("worldEvents", event);

    // 更新世界状态
    await updateWorld({ ...world, state: { ...state, location: newLocation, mood: newMood, flags: { ...state.flags, weather: newWeather } } });

    // 推演事件同步到聊天记忆池（对齐"事件已同步到VW记忆池+聊天记忆池"）
    const pid = world.characters[0]?.id;
    if (pid) {
      addMemoryFact(pid, `虚拟世界[第${state.day}天] ${content.slice(0, 80)}`, "world").catch(() => undefined);
    }

    emit("world-tick", event);
    emit("data-changed", { collection: "worldEvents" });
    return event;
  } catch {
    return null;
  }
}

const IMAGE_RULES = [
  "",
  "[图片生成规则]",
  "仅在以下情况调用generate_image工具生成场景配图：",
  "① 重大剧情节点（首次登场/关键转折/情感高潮/重要决定/告别/重逢）",
  "② 场景具有强烈视觉冲击力（星空/花海/雨雪/火焰/夕阳等自然景观+人物互动）",
  "③ 与前一条记录相比有明显的场景切换或时间跨度变化",
  "④ 本轮推演未生成过图片且内容超过50字",
  "禁止：连续两条都生成图片；普通日常场景；纯对话无场景描写",
].join("\n");

/** 给聊天引擎的世界状态注入块（对齐 getStatePromptBlock） */
export async function getWorldStateBlock(personaId: string): Promise<string> {
  const worlds = (await listWorlds()).filter((w) => w.characters.some((c) => c.id === personaId));
  const w = worlds.find((x) => x.autoTick) ?? worlds[0];
  if (!w?.state) return "";
  const events = (await getStoryEvents(w.id)).slice(-3);
  return [
    `[虚拟世界状态] 第${w.state.day}天 ${w.state.timeOfDay}，地点：${w.state.location}，氛围：${w.state.mood}。`,
    "[近期剧情]",
    ...events.map((e) => `[第${e.day}天${e.time.slice(0, 2)}时] ${e.characters.join("、")}：${e.content}`),
  ].join("\n");
}

// ─── 自动推演调度 ───

let worldTimer: number | null = null;

export function startWorldAutoTick(): void {
  if (worldTimer !== null) return;
  worldTimer = window.setInterval(async () => {
    const worlds = await listWorlds();
    for (const w of worlds) {
      if (!w.autoTick) continue;
      const intervalMs = Math.max(5, w.tickIntervalMin) * 60_000;
      if (!w.state?.lastTickAt || Date.now() - w.state.lastTickAt >= intervalMs) {
        await runWorldTick(w.id, { generateImage: true });
      }
    }
  }, 60_000);
}

export function stopWorldAutoTick(): void {
  if (worldTimer !== null) {
    clearInterval(worldTimer);
    worldTimer = null;
  }
}
