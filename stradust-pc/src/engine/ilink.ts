/**
 * iLink 微信 bot 通道 —— 移植自 Android ilink/IlinkApi.kt + IlinkAuthManager.kt +
 * IlinkPollingService.kt（API 端点/鉴权头/轮询协议/消息结构忠实照搬）。
 *
 * 原始值（读自 Kotlin）：
 * - BASE_URL = "https://ilinkai.weixin.qq.com"（IlinkApi.kt；非内网地址，但按要求做成
 *   kv 可配置项 ilink.baseUrl，默认沿用该值）
 * - CDN_URL  = "https://novac2c.cdn.weixin.qq.com/c2c"（表情包上传，Web 暂不移植）
 * - 公共头：iLink-App-Id: bot、iLink-App-ClientVersion: "1"；
 *   鉴权头：AuthorizationType: ilink_bot_token、Authorization: Bearer <botToken>、
 *   X-WECHAT-UIN: base64(abs(randomInt))
 * - 端点：GET /ilink/bot/get_bot_qrcode?bot_type=3、GET /ilink/bot/get_qrcode_status?qrcode=、
 *   POST /ilink/bot/getupdates、POST /ilink/bot/sendmessage（base_info.channel_version="2.1.1"）
 * - 轮询间隔 POLL_INTERVAL_MS = 2000ms，连续失败 maxFails=10 次停止，errcode=-14 会话过期。
 *
 * Web 等价替代说明：
 * - Android 是前台 Service 持续轮询；Web 用 setInterval（页面关闭即停，属预期差异）。
 * - **CORS**：ilinkai.weixin.qq.com 不提供跨域许可，浏览器直连大概率被拦。Tauri WebView
 *   同源策略宽松可直接工作；纯浏览器模式需自行配置代理/CORS 豁免（此处不做静默代理，
 *   失败按 failCount 逻辑上报事件）。
 * - 二维码渲染（zxing）不在 API 层：generateQRCode 返回 qrcode 字符串与内容，由 UI 渲染。
 * - 自动回复的提示词（微信 emoji 规则 [[emoji:...]]、人格 prompt 等）在 generator 注入方
 *   （Phase3 聊天引擎），本文件只做协议与调度。
 */

import * as db from "@/lib/db";
import { emit } from "@/lib/events";
import type { IlinkBinding, IlinkMessageRecord } from "./kinds";

// ═══════════════════════ 纯 API 层 ═══════════════════════

const DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";
const CHANNEL_VERSION = "2.1.1";

/** 服务端可覆盖的 base url（kv: ilink.baseUrl）；确认绑定后 binding 里也存一份 */
async function getBaseUrl(): Promise<string> {
  const custom = await db.kvGet<string>("ilink.baseUrl");
  return (typeof custom === "string" && custom.trim()) ? custom.replace(/\/+$/, "") : DEFAULT_BASE_URL;
}

function generateUin(): string {
  // 照搬 Kotlin：Base64(abs(randomInt).toString())
  const uid = Math.abs(Math.floor(Math.random() * 0x7fffffff));
  return btoa(String(uid));
}

function commonHeaders(): Record<string, string> {
  return {
    "iLink-App-Id": "bot",
    "iLink-App-ClientVersion": "1",
  };
}

function authHeaders(botToken: string): Record<string, string> {
  return {
    ...commonHeaders(),
    "Content-Type": "application/json",
    AuthorizationType: "ilink_bot_token",
    "X-WECHAT-UIN": generateUin(),
    Authorization: `Bearer ${botToken}`,
  };
}

const baseInfo = () => ({ channel_version: CHANNEL_VERSION });

export interface IlinkQrcodeResult {
  qrcode: string;
  qrcodeImgContent: string;
}

export type IlinkQrcodeStatus =
  | { kind: "waiting" }
  | { kind: "scanned" }
  | { kind: "expired" }
  | { kind: "confirmed"; botToken: string; ilinkBotId: string; baseUrl: string; ilinkUserId: string };

/** 获取登录二维码（GET /ilink/bot/get_bot_qrcode?bot_type=3）；UI 拿 qrcode 字符串自行渲染 */
export async function getBotQrcode(): Promise<IlinkQrcodeResult | null> {
  try {
    const base = await getBaseUrl();
    const res = await fetch(`${base}/ilink/bot/get_bot_qrcode?bot_type=3`, {
      method: "GET",
      headers: commonHeaders(),
      signal: AbortSignal.timeout(15000),
    });
    if (!res.ok) return null;
    const json = (await res.json()) as Record<string, any>;
    if (Number(json?.ret ?? -1) !== 0) return null;
    return {
      qrcode: String(json?.qrcode ?? ""),
      qrcodeImgContent: String(json?.qrcode_img_content ?? ""),
    };
  } catch {
    return null;
  }
}

/** 查询扫码状态（GET /ilink/bot/get_qrcode_status?qrcode=） */
export async function getQrcodeStatus(qrcode: string): Promise<IlinkQrcodeStatus> {
  try {
    const base = await getBaseUrl();
    const res = await fetch(`${base}/ilink/bot/get_qrcode_status?qrcode=${encodeURIComponent(qrcode)}`, {
      method: "GET",
      headers: commonHeaders(),
      signal: AbortSignal.timeout(30000),
    });
    if (!res.ok) return { kind: "waiting" };
    const json = (await res.json()) as Record<string, any>;
    const status = String(json?.status ?? "");
    switch (status) {
      case "confirmed":
        return {
          kind: "confirmed",
          botToken: String(json?.bot_token ?? ""),
          ilinkBotId: String(json?.ilink_bot_id ?? ""),
          baseUrl: String(json?.baseurl ?? "") || DEFAULT_BASE_URL,
          ilinkUserId: String(json?.ilink_user_id ?? ""),
        };
      case "scaned":
      case "scanned":
        return { kind: "scanned" };
      case "expired":
        return { kind: "expired" };
      default:
        return { kind: "waiting" };
    }
  } catch {
    return { kind: "waiting" };
  }
}

/** 生成二维码并给出轮询器（Android WechatBindActivity 的扫码流程等价物） */
export async function generateQRCode(): Promise<{
  qrcode: string;
  imgContent: string;
  /** 轮询直到 confirmed/expired；每次状态变化回调 onStatus */
  poll: (onStatus: (s: IlinkQrcodeStatus) => void, intervalMs?: number) => () => void;
} | null> {
  const r = await getBotQrcode();
  if (!r || !r.qrcode) return null;
  return {
    qrcode: r.qrcode,
    imgContent: r.qrcodeImgContent,
    poll: (onStatus, intervalMs = 2000) => {
      let stopped = false;
      let timer = 0;
      const tick = async () => {
        if (stopped) return;
        const s = await getQrcodeStatus(r.qrcode);
        onStatus(s);
        if (s.kind === "confirmed" || s.kind === "expired") return;
        timer = window.setTimeout(tick, intervalMs);
      };
      void tick();
      return () => {
        stopped = true;
        window.clearTimeout(timer);
      };
    },
  };
}

/** 消息条目（照搬 MessageItem） */
export interface IlinkMessageItem {
  type: number; // 1=text 2=image 3=voice 4=file
  text: string;
  imageUrl: string;
  voiceText: string;
  fileName: string;
}

/** 原始消息（照搬 IlinkMessage） */
export interface IlinkRawMessage {
  messageId: number;
  fromUserId: string;
  toUserId: string;
  clientId: string;
  createTimeMs: number;
  messageType: number;
  messageState: number;
  contextToken: string;
  itemList: IlinkMessageItem[];
  textContent: string;
}

export interface IlinkUpdateResult {
  messages: IlinkRawMessage[];
  getUpdatesBuf: string;
  sessionExpired: boolean;
}

/** 照搬 IlinkApi.parseMessage */
function parseMessage(obj: Record<string, any>): IlinkRawMessage {
  const itemList: IlinkMessageItem[] = [];
  const arr = Array.isArray(obj?.item_list) ? obj.item_list : [];
  for (const itemObj of arr) {
    itemList.push({
      type: Number(itemObj?.type ?? 1),
      text: String(itemObj?.text_item?.text ?? ""),
      imageUrl: String(itemObj?.image_item?.url ?? ""),
      voiceText: String(itemObj?.voice_item?.text ?? ""),
      fileName: String(itemObj?.file_item?.file_name ?? ""),
    });
  }
  const textContent = itemList.length
    ? itemList.filter((it) => it.type === 1 && it.text.trim()).map((it) => it.text).join("\n")
    : String(obj?.content ?? "");
  const contextToken = String(obj?.context_token ?? "") || String(obj?.client_id ?? "");
  return {
    messageId: Number(obj?.message_id ?? 0),
    fromUserId: String(obj?.from_user_id ?? ""),
    toUserId: String(obj?.to_user_id ?? ""),
    clientId: String(obj?.client_id ?? ""),
    createTimeMs: Number(obj?.create_time_ms ?? 0),
    messageType: Number(obj?.message_type ?? 1),
    messageState: Number(obj?.message_state ?? 0),
    contextToken,
    itemList,
    textContent,
  };
}

/**
 * 拉取更新（长轮询，照搬 getUpdates）：POST {baseUrl}/ilink/bot/getupdates
 * body: {get_updates_buf?, base_info}; errcode=-14 → sessionExpired。
 * since 即 Android 的 getUpdatesBuf 游标。
 */
export async function getUpdates(botToken: string, baseUrl: string, since = ""): Promise<IlinkUpdateResult | null> {
  try {
    const body: Record<string, unknown> = { base_info: baseInfo() };
    if (since.trim()) body.get_updates_buf = since;
    const res = await fetch(`${baseUrl}/ilink/bot/getupdates`, {
      method: "POST",
      headers: authHeaders(botToken),
      body: JSON.stringify(body),
      // 照搬 readTimeout 65s（长轮询）
      signal: AbortSignal.timeout(65000),
    });
    if (!res.ok) return null;
    const json = (await res.json()) as Record<string, any>;
    const errcode = Number(json?.errcode ?? 0);
    if (errcode === -14) {
      return { messages: [], getUpdatesBuf: "", sessionExpired: true };
    }
    const msgArray = Array.isArray(json?.msgs) ? json.msgs : Array.isArray(json?.messages) ? json.messages : null;
    const messages: IlinkRawMessage[] = [];
    if (msgArray) {
      for (const m of msgArray) {
        try {
          messages.push(parseMessage(m));
        } catch { /* 跳过坏消息 */ }
      }
    }
    const ret = Number(json?.ret ?? -1);
    if (ret !== 0 && messages.length === 0) return null;
    return {
      messages,
      getUpdatesBuf: String(json?.get_updates_buf ?? ""),
      sessionExpired: false,
    };
  } catch {
    return null;
  }
}

/**
 * 发送文本消息（照搬 sendMessage）：POST {baseUrl}/ilink/bot/sendmessage
 * msg: {from_user_id:"", to_user_id, client_id, message_type:2, message_state:2,
 *       context_token, item_list:[{type:1,text_item:{text}}]}
 */
export async function sendRawMessage(botToken: string, baseUrl: string, toUserId: string, contextToken: string, text: string): Promise<boolean> {
  try {
    const clientId = String(Math.abs(Math.floor(Math.random() * Number.MAX_SAFE_INTEGER)));
    const body = {
      msg: {
        from_user_id: "",
        to_user_id: toUserId,
        client_id: clientId,
        message_type: 2,
        message_state: 2,
        context_token: contextToken,
        item_list: [{ type: 1, text_item: { text } }],
      },
      base_info: baseInfo(),
    };
    const res = await fetch(`${baseUrl}/ilink/bot/sendmessage`, {
      method: "POST",
      headers: authHeaders(botToken),
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    });
    if (!res.ok) return false;
    const json = (await res.json()) as Record<string, any>;
    const ret = Number(json?.ret ?? -1);
    const errcode = Number(json?.errcode ?? 0);
    if (ret !== 0 && errcode !== 0) return false;
    return true; // ret!=0 但 errcode==0 视为成功（对齐 Kotlin）
  } catch {
    return false;
  }
}

// ═══════════════════════ 绑定存取（IlinkAuthManager 等价：kv 集合 id="ilink-self"） ═══════════════════════

const BINDING_ID = "ilink-self";

/** IlinkBinding 扩展字段：Android Confirmed 响应里的 ilinkBotId/ilinkUserId/baseUrl */
export interface IlinkBindingExt extends IlinkBinding {
  ilinkBotId?: string;
  ilinkUserId?: string;
  baseUrl?: string;
}

export async function getBinding(): Promise<IlinkBindingExt | null> {
  return db.getOne<IlinkBindingExt>("kv", BINDING_ID);
}

export async function setBinding(b: IlinkBindingExt): Promise<void> {
  await db.put("kv", { ...b, id: BINDING_ID });
}

/** 用 get_qrcode_status 返回的 bot_token 完成绑定（对应 saveBinding(Confirmed)） */
export async function bindBot(token: string, extra?: Partial<Pick<IlinkBindingExt, "ilinkBotId" | "ilinkUserId" | "baseUrl" | "botName" | "personaId">>): Promise<IlinkBindingExt> {
  const prev = await getBinding();
  const b: IlinkBindingExt = {
    id: BINDING_ID,
    bound: true,
    botToken: token,
    botName: extra?.botName ?? prev?.botName ?? "星尘",
    boundAt: Date.now(),
    autoReply: prev?.autoReply ?? true,
    personaId: extra?.personaId ?? prev?.personaId ?? "default",
    ilinkBotId: extra?.ilinkBotId ?? prev?.ilinkBotId ?? "",
    ilinkUserId: extra?.ilinkUserId ?? prev?.ilinkUserId ?? "",
    baseUrl: extra?.baseUrl ?? prev?.baseUrl ?? (await getBaseUrl()),
  };
  await setBinding(b);
  return b;
}

/** 解绑（对应 clearBinding；保留 autoReply/personaId 便于再绑） */
export async function unbindBot(): Promise<void> {
  const prev = await getBinding();
  const b: IlinkBindingExt = {
    id: BINDING_ID,
    bound: false,
    botToken: "",
    botName: prev?.botName ?? "",
    autoReply: prev?.autoReply ?? true,
    personaId: prev?.personaId ?? "default",
    baseUrl: prev?.baseUrl,
  };
  await setBinding(b);
}

// ═══════════════════════ 轮询服务（IlinkPollingService 等价） ═══════════════════════

const POLL_INTERVAL_MS = 2000; // 照搬 Kotlin
const MAX_FAILS = 10;          // 照搬 Kotlin

let pollTimer: ReturnType<typeof setTimeout> | null = null;
let polling = false;
let updatesBuf = "";
let failCount = 0;
/** 已处理消息 id 去重（页面刷新后 buf 归零时防重复入库） */
const seenIds = new Set<string>();

let replyGenerator: ((userText: string, conversationId: string) => Promise<string>) | null = null;

/**
 * 注入自动回复生成器（Phase3 由聊天引擎接入）。
 * 注：微信专属回复规则/[[emoji:...]] 标记剥离等提示词属于 generator 注入方的职责，不在本文件。
 */
export function setReplyGenerator(fn: ((userText: string, conversationId: string) => Promise<string>) | null): void {
  replyGenerator = fn;
}

/** 便捷轮询（纯 API 层，无状态）：使用当前 binding 拉一条增量 */
export async function pollMessages(since = ""): Promise<IlinkUpdateResult | null> {
  const b = await getBinding();
  if (!b?.bound || !b.botToken) return null;
  return getUpdates(b.botToken, b.baseUrl ?? (await getBaseUrl()), since || updatesBuf);
}

/** 便捷发送（纯 API 层）：conversationId = from_user_id，内部补 context_token */
export async function sendMessage(convId: string, text: string, contextToken = ""): Promise<boolean> {
  const b = await getBinding();
  if (!b?.bound || !b.botToken) return false;
  const base = b.baseUrl ?? (await getBaseUrl());
  // 无缓存 token 时先拉一次增量拿最近 context_token（对齐 Android contextToken 传递链）
  let ctx = contextToken;
  if (!ctx) {
    const latest = await getUpdates(b.botToken, base, updatesBuf);
    const lastFromConv = latest?.messages.filter((m) => m.fromUserId === convId).pop();
    ctx = lastFromConv?.contextToken ?? "";
    if (latest?.getUpdatesBuf) updatesBuf = latest.getUpdatesBuf;
  }
  const ok = await sendRawMessage(b.botToken, base, convId, ctx, text);
  if (ok) {
    const rec: IlinkMessageRecord = {
      id: db.newId("ilink-out"),
      conversationId: convId,
      direction: "out",
      senderName: b.botName || "星尘",
      content: text,
      timestamp: Date.now(),
    };
    await db.put("ilinkMessages", rec);
    emit("data-changed");
  }
  return ok;
}

/** 处理一条入站消息：入库 + 事件 + 自动回复（照搬 handleTextMessage 主流程） */
async function handleMessage(msg: IlinkRawMessage): Promise<void> {
  if (msg.messageType !== 1) return;
  let userText = msg.textContent.trim();
  const imageUrls = msg.itemList.filter((it) => it.type === 2 && it.imageUrl.trim()).map((it) => it.imageUrl);
  if (!userText) {
    for (const it of msg.itemList) {
      if (it.type === 1 && it.text.trim()) { userText = it.text.trim(); break; }
      if (it.type === 3 && it.voiceText.trim()) { userText = it.voiceText.trim(); break; }
    }
  }
  if (!userText && imageUrls.length) userText = "[图片]"; // 照搬占位规则
  if (!userText && !imageUrls.length) return;

  const dedupeKey = `${msg.messageId}-${msg.clientId}`;
  if (seenIds.has(dedupeKey)) return;
  seenIds.add(dedupeKey);

  const b = await getBinding();
  const rec: IlinkMessageRecord = {
    id: db.newId("ilink-in"),
    conversationId: msg.fromUserId,
    direction: "in",
    senderName: "微信用户",
    content: userText,
    timestamp: msg.createTimeMs || Date.now(),
    raw: { imageUrls, contextToken: msg.contextToken, messageId: msg.messageId },
  };
  await db.put("ilinkMessages", rec);
  emit("ilink-message", rec);
  emit("data-changed");

  // 自动回复
  if (b?.autoReply && replyGenerator && userText) {
    try {
      const reply = await replyGenerator(userText, msg.fromUserId);
      if (reply.trim()) {
        await sendRawMessage(b.botToken, b.baseUrl ?? (await getBaseUrl()), msg.fromUserId, msg.contextToken, reply);
        const outRec: IlinkMessageRecord = {
          id: db.newId("ilink-out"),
          conversationId: msg.fromUserId,
          direction: "out",
          senderName: b.botName || "星尘",
          content: reply,
          timestamp: Date.now(),
        };
        await db.put("ilinkMessages", outRec);
        emit("data-changed");
      }
    } catch (e) {
      console.warn("[ilink] reply generator failed:", (e as Error).message);
    }
  }
}

async function pollTick(): Promise<void> {
  if (!polling) return;
  const b = await getBinding();
  if (!b?.bound || !b.botToken) {
    stop();
    return;
  }
  const base = b.baseUrl ?? (await getBaseUrl());
  const result = await getUpdates(b.botToken, base, updatesBuf);
  if (result) {
    failCount = 0;
    if (result.sessionExpired) {
      emit("ilink-message", { error: "session-expired" });
      stop();
      return;
    }
    if (result.getUpdatesBuf.trim()) updatesBuf = result.getUpdatesBuf;
    for (const msg of result.messages) {
      await handleMessage(msg).catch((e) => console.warn("[ilink] handleMessage:", (e as Error).message));
    }
    // 更新 lastPollAt
    await setBinding({ ...b, lastPollAt: Date.now() });
  } else {
    failCount++;
    if (failCount >= MAX_FAILS) {
      emit("ilink-message", { error: "too-many-failures" });
      stop();
      return;
    }
  }
  pollTimer = setTimeout(pollTick, POLL_INTERVAL_MS);
}

/** 启动轮询（setInterval 等价：递归 setTimeout 防重入） */
export function start(): void {
  if (polling) return;
  polling = true;
  failCount = 0;
  void pollTick();
}

/** 停止轮询 */
export function stop(): void {
  polling = false;
  if (pollTimer !== null) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
}

export function isPolling(): boolean {
  return polling;
}

/** 重置游标（重新绑定后旧 buf 失效） */
export function resetCursor(): void {
  updatesBuf = "";
  failCount = 0;
  seenIds.clear();
}
