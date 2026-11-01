/**
 * 微信 iLink 页面 —— 对应 Android WechatBindActivity + WechatChatActivity
 * 扫码绑定 Bot、轮询开关、自动回复设置、微信聊天记录浏览。
 */

import { useEffect, useState, useCallback } from "react";
import { motion } from "framer-motion";
import { QrCode, RefreshCw, MessageSquare, Bot, Unlink, Play, Square, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Input } from "@/components/ui/input";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { ilinkApi } from "@/lib/tauri";
import { on } from "@/lib/events";
import type { IlinkMessageRecord } from "@/engine/kinds";
import { PageContainer } from "@/components/layout/PageContainer";

type BindStatus = "unbound" | "qr-ready" | "waiting" | "scanned" | "expired" | "confirmed";

interface Conversation {
  id: string;
  name: string;
  lastContent: string;
  lastTime: number;
}

export function IlinkPage() {
  const personas = usePersonaStore((s) => s.personas);
  const [bound, setBound] = useState(false);
  const [botName, setBotName] = useState("");
  const [autoReply, setAutoReply] = useState(true);
  const [personaId, setPersonaId] = useState("");
  const [status, setStatus] = useState<BindStatus>("unbound");
  const [qrDataUrl, setQrDataUrl] = useState("");
  const [polling, setPolling] = useState(false);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConv, setActiveConv] = useState<string | null>(null);
  const [messages, setMessages] = useState<IlinkMessageRecord[]>([]);
  const [replyText, setReplyText] = useState("");
  const [error, setError] = useState<string | null>(null);

  const loadBinding = useCallback(async () => {
    const b = await ilinkApi.getBinding();
    if (b) {
      setBound(b.bound);
      setBotName(b.botName || "微信Bot");
      setAutoReply(b.autoReply);
      setPersonaId(b.personaId || personas[0]?.id || "");
      if (b.bound) setPolling(ilinkApi.isPolling());
    }
  }, [personas]);

  const loadConversations = useCallback(async () => {
    const all = (await ilinkApi.listMessages()) as unknown as IlinkMessageRecord[];
    const map = new Map<string, Conversation>();
    for (const m of all) {
      const c = map.get(m.conversationId) ?? { id: m.conversationId, name: m.senderName || m.conversationId.slice(0, 8), lastContent: "", lastTime: 0 };
      if (m.timestamp >= c.lastTime) {
        c.lastContent = m.content;
        c.lastTime = m.timestamp;
      }
      map.set(m.conversationId, c);
    }
    setConversations([...map.values()].sort((a, b) => b.lastTime - a.lastTime));
  }, []);

  useEffect(() => {
    loadBinding().catch(() => undefined);
    loadConversations().catch(() => undefined);
    const off = on("ilink-message", () => {
      loadConversations().catch(() => undefined);
    });
    return off;
  }, [loadBinding, loadConversations]);

  const openConversation = async (convId: string) => {
    setActiveConv(convId);
    const list = (await ilinkApi.listMessages(convId)) as unknown as IlinkMessageRecord[];
    setMessages(list.sort((a, b) => a.timestamp - b.timestamp));
  };

  const handleGetQr = async () => {
    setError(null);
    try {
      const qr = await ilinkApi.generateQRCode();
      if (!qr) {
        setError("无法获取登录二维码（浏览器模式可能被微信接口跨域拦截，请在桌面版使用或检查网络）");
        setStatus("expired");
        return;
      }
      setQrDataUrl(qr.imgContent?.startsWith("data:") ? qr.imgContent : "");
      setStatus("qr-ready");
      // generateQRCode 内部已轮询状态；这里再查询一次并跟踪
      const st = await ilinkApi.getQrcodeStatus(qr.qrcode);
      applyQrStatus(st);
    } catch (e) {
      setError((e as Error).message);
      setStatus("expired");
    }
  };

  const applyQrStatus = (st: Awaited<ReturnType<typeof ilinkApi.getQrcodeStatus>>) => {
    if (st.kind === "confirmed") {
      setStatus("confirmed");
      ilinkApi
        .bindBot(st.botToken, { ilinkBotId: st.ilinkBotId, ilinkUserId: st.ilinkUserId, baseUrl: st.baseUrl, personaId })
        .then(() => {
          setBound(true);
          ilinkApi.start();
          setPolling(true);
          loadBinding().catch(() => undefined);
        })
        .catch((e: Error) => setError(e.message));
    } else if (st.kind === "scanned") setStatus("scanned");
    else if (st.kind === "expired") setStatus("expired");
    else setStatus("waiting");
  };

  const handleTogglePolling = async () => {
    if (polling) {
      ilinkApi.stop();
      setPolling(false);
    } else {
      ilinkApi.start();
      setPolling(true);
    }
  };

  const handleAutoReplyChange = async (v: boolean) => {
    setAutoReply(v);
    const b = await ilinkApi.getBinding();
    if (b) await ilinkApi.setBinding({ ...b, autoReply: v, personaId });
  };

  const handlePersonaChange = async (pid: string) => {
    setPersonaId(pid);
    const b = await ilinkApi.getBinding();
    if (b) await ilinkApi.setBinding({ ...b, personaId: pid });
  };

  const handleUnbind = async () => {
    await ilinkApi.unbind();
    setBound(false);
    setPolling(false);
    setStatus("unbound");
  };

  const handleSendReply = async () => {
    if (!replyText.trim() || !activeConv) return;
    await ilinkApi.sendMessage(activeConv, replyText.trim());
    setReplyText("");
    openConversation(activeConv).catch(() => undefined);
  };

  return (
    <PageContainer title="微信互联" description="通过 iLink 让 AI 角色接入微信，自动回复好友消息">
      <div className="grid gap-6 lg:grid-cols-2">
        {/* 绑定与开关 */}
        <div className="surface-card p-5 space-y-4">
          <div className="flex items-center gap-2">
            <Bot className="h-5 w-5 text-[var(--color-primary)]" />
            <h3 className="text-h3">Bot 绑定</h3>
            <span className={`ml-auto text-xs px-2 py-0.5 rounded-full ${bound ? "bg-emerald-500/15 text-emerald-500" : "bg-zinc-500/15 text-zinc-400"}`}>
              {bound ? `已绑定 · ${botName}` : "未绑定"}
            </span>
          </div>

          {bound ? (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm">消息轮询</span>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-[var(--color-muted-foreground)]">{polling ? "运行中" : "已停止"}</span>
                  <Button size="sm" variant="outline" onClick={handleTogglePolling}>
                    {polling ? <Square className="h-4 w-4 mr-1" /> : <Play className="h-4 w-4 mr-1" />}
                    {polling ? "停止" : "启动"}
                  </Button>
                </div>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm">自动回复</span>
                <Switch checked={autoReply} onCheckedChange={handleAutoReplyChange} />
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm shrink-0">回复人格</span>
                <select
                  className="flex-1 rounded-md border border-[var(--color-border)] bg-transparent px-2 py-1.5 text-sm"
                  value={personaId}
                  onChange={(e) => handlePersonaChange(e.target.value)}
                >
                  {personas.map((p) => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </select>
              </div>
              <Button variant="ghost" size="sm" className="text-red-400" onClick={handleUnbind}>
                <Unlink className="h-4 w-4 mr-1" /> 解除绑定
              </Button>
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-[var(--color-muted-foreground)]">
                点击下方按钮获取微信登录二维码，用手机微信扫码并确认授权后自动完成绑定。
              </p>
              <div className="flex items-center justify-center min-h-40 rounded-xl border border-dashed border-[var(--color-border)] p-4">
                {qrDataUrl && status !== "expired" ? (
                  <div className="text-center space-y-2">
                    <img src={qrDataUrl} alt="微信登录二维码" className="w-44 h-44 object-contain rounded-lg bg-white p-2" />
                    <p className="text-xs text-[var(--color-muted-foreground)]">
                      {status === "scanned" ? "已扫码，请在手机上确认…" : status === "confirmed" ? "绑定成功" : "请使用微信扫码"}
                    </p>
                  </div>
                ) : (
                  <div className="text-center text-[var(--color-muted-foreground)]">
                    <QrCode className="h-10 w-10 mx-auto mb-2 opacity-50" />
                    <p className="text-sm">{status === "expired" ? "二维码已过期" : "尚未获取二维码"}</p>
                  </div>
                )}
              </div>
              <div className="flex gap-2">
                <Button onClick={handleGetQr}>
                  <RefreshCw className="h-4 w-4 mr-1" /> {status === "expired" ? "刷新二维码" : "获取登录二维码"}
                </Button>
                {personas.length > 0 && (
                  <Button variant="outline" onClick={async () => {
                    // 手动粘贴 bot token 绑定（无法扫码时的替代路径）
                    const token = window.prompt("粘贴 iLink Bot Token");
                    if (token) {
                      await ilinkApi.bindBot(token.trim(), { personaId: personaId || personas[0].id });
                      setBound(true);
                      ilinkApi.start();
                      setPolling(true);
                      loadBinding().catch(() => undefined);
                    }
                  }}>
                    <Sparkles className="h-4 w-4 mr-1" /> 手动输入 Token
                  </Button>
                )}
              </div>
            </div>
          )}
          {error && <p className="text-xs text-red-400">{error}</p>}
        </div>

        {/* 会话列表 + 消息记录 */}
        <div className="surface-card p-5 space-y-3 flex flex-col min-h-96">
          <div className="flex items-center gap-2">
            <MessageSquare className="h-5 w-5 text-[var(--color-primary)]" />
            <h3 className="text-h3">微信消息记录</h3>
            <Button size="sm" variant="ghost" className="ml-auto" onClick={() => loadConversations()}>
              <RefreshCw className="h-4 w-4" />
            </Button>
          </div>
          <div className="grid grid-cols-[minmax(0,140px)_1fr] gap-3 flex-1 min-h-0">
            <div className="space-y-1 overflow-y-auto max-h-105">
              {conversations.length === 0 && (
                <p className="text-xs text-[var(--color-muted-foreground)] p-2">暂无会话<br />绑定后收到的消息会出现在这里</p>
              )}
              {conversations.map((c) => (
                <button
                  key={c.id}
                  onClick={() => openConversation(c.id)}
                  className={`w-full text-left px-2 py-1.5 rounded-lg text-xs truncate transition-colors ${
                    activeConv === c.id ? "bg-[var(--color-primary)]/15 text-[var(--color-primary)]" : "hover:bg-[var(--color-muted)]"
                  }`}
                >
                  {c.name}
                </button>
              ))}
            </div>
            <div className="flex flex-col min-h-0">
              <div className="flex-1 overflow-y-auto space-y-2 p-2 rounded-lg bg-[var(--color-muted)]/30 max-h-96">
                {messages.length === 0 && (
                  <p className="text-xs text-center text-[var(--color-muted-foreground)] py-8">选择左侧会话查看消息</p>
                )}
                {messages.map((m) => (
                  <motion.div
                    key={m.id}
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    className={`max-w-4/5 text-xs px-3 py-2 rounded-xl whitespace-pre-wrap break-words ${
                      m.direction === "in"
                        ? "bg-[var(--color-card)] self-start"
                        : "bg-[var(--color-primary)]/20 self-end ml-auto"
                    }`}
                  >
                    {m.content}
                    <div className="text-[10px] opacity-60 mt-1">
                      {new Date(m.timestamp).toLocaleString("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" })}
                    </div>
                  </motion.div>
                ))}
              </div>
              {activeConv && (
                <div className="flex gap-2 mt-2">
                  <Input
                    value={replyText}
                    onChange={(e) => setReplyText(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && handleSendReply()}
                    placeholder="以角色身份回复微信消息…"
                  />
                  <Button size="sm" onClick={handleSendReply}>发送</Button>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </PageContainer>
  );
}
