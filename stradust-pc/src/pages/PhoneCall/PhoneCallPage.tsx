import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import {
  ArrowLeft,
  Phone,
  PhoneOff,
  Mic,
  MicOff,
  Volume2,
  VolumeX,
} from "lucide-react";
import { useState, useEffect, useRef } from "react";

/**
 * 语音通话页面
 * 对应Android PhoneCallActivity
 */
export function PhoneCallPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const { currentPersonaId } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const navigate = useNavigate();

  const [isCalling, setIsCalling] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [isSpeakerOn, setIsSpeakerOn] = useState(false);
  const [callDuration, setCallDuration] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  /** 通话计时器：isCalling为true时每秒递增callDuration */
  useEffect(() => {
    if (isCalling) {
      timerRef.current = setInterval(() => {
        setCallDuration((prev) => prev + 1);
      }, 1000);
    } else {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    }
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [isCalling]);

  const toggleCall = () => {
    setIsCalling(!isCalling);
    if (!isCalling) {
      setCallDuration(0);
    }
  };

  /** 格式化通话时长 */
  const formatDuration = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  return (
    <PageContainer className="p-0 relative overflow-hidden">
      {/* ========== 背景模糊暗化 ========== */}
      <div className="absolute inset-0 bg-black/40 backdrop-blur-md -z-10" />

      {/* ========== 全屏居中布局 ========== */}
      <div className="flex flex-col items-center justify-center h-full relative z-10">
        {/* 返回按钮（左上角） */}
        <button
          onClick={() => navigate(-1)}
          className="absolute top-6 left-6 flex items-center gap-2 text-[var(--color-muted-foreground)] hover:text-[var(--color-card-foreground)] transition-colors"
        >
          <ArrowLeft className="h-5 w-5" />
          <span className="text-sm">返回</span>
        </button>

        {/* ========== 大头像圆形（渐变边框环，脉动动画） ========== */}
        <div className="relative mb-8">
          {/* 外层渐变边框环 */}
          <div
            className={`w-36 h-36 rounded-full p-[3px] ${
              isCalling ? "animate-pulse" : ""
            }`}
            style={{
              background: "var(--theme-gradient)",
            }}
          >
            {/* 内层头像 */}
            <div className="w-full h-full rounded-full bg-[var(--color-card)] flex items-center justify-center overflow-hidden">
              <span
                className="text-5xl font-bold"
                style={{ color: "var(--color-primary)" }}
              >
                AI
              </span>
            </div>
          </div>

          {/* 通话中的脉动光环 */}
          {isCalling && (
            <>
              <div
                className="absolute inset-[-12px] rounded-full border-2 opacity-30 animate-ping"
                style={{
                  borderColor: "var(--color-primary)",
                  animationDuration: "2s",
                }}
              />
              <div
                className="absolute inset-[-24px] rounded-full border opacity-15 animate-ping"
                style={{
                  borderColor: "var(--color-primary)",
                  animationDuration: "2.5s",
                  animationDelay: "0.5s",
                }}
              />
            </>
          )}
        </div>

        {/* ========== 角色名称 + 状态文字 ========== */}
        <div className="text-center mb-10">
          <h2 className="text-2xl font-bold text-white drop-shadow-lg">
            星尘助手
          </h2>
          <p
            className={`mt-2 text-base ${
              isCalling ? "cyber-glow text-[var(--color-primary)]" : "text-white/70"
            }`}
            style={
              isCalling
                ? {
                    textShadow:
                      "0 0 10px var(--theme-glow), 0 0 30px var(--theme-glow)",
                  }
                : undefined
            }
          >
            {isCalling ? `通话中... ${formatDuration(callDuration)}` : "准备通话"}
          </p>
        </div>

        {/* ========== 通话时长大字体显示 ========== */}
        {isCalling && (
          <div className="mb-12">
            <span
              className="text-5xl font-light tabular-nums tracking-wider text-white/90"
              style={{ fontFamily: "'SF Mono', 'Consolas', monospace" }}
            >
              {formatDuration(callDuration)}
            </span>
          </div>
        )}

        {/* ========== 底部操作按钮行 ========== */}
        <div className="flex items-center gap-8 mt-auto mb-16">
          {/* 静音按钮 */}
          <Button
            variant={isMuted ? "destructive" : "outline"}
            size="icon"
            className={`h-16 w-16 rounded-full transition-all ${
              isMuted
                ? "bg-red-500/20 border-red-500/50 text-red-400 hover:bg-red-500/30 hover:border-red-500"
                : "border-white/20 text-white hover:bg-white/10 hover:border-white/40"
            }`}
            onClick={() => setIsMuted(!isMuted)}
          >
            {isMuted ? (
              <MicOff className="h-7 w-7" />
            ) : (
              <Mic className="h-7 w-7" />
            )}
          </Button>

          {/* 挂断/拨号按钮（红色） */}
          <Button
            size="icon"
            className={`h-20 w-20 rounded-full transition-all shadow-xl ${
              isCalling
                ? "bg-[var(--color-destructive)] hover:bg-red-600 text-white"
                : "hover:scale-105"
            }`}
            style={
              !isCalling
                ? {
                    background: "linear-gradient(135deg, #22c55e, #16a34a)",
                  }
                : undefined
            }
            onClick={toggleCall}
          >
            {isCalling ? (
              <PhoneOff className="h-9 w-9" />
            ) : (
              <Phone className="h-9 w-9" fill="currentColor" />
            )}
          </Button>

          {/* 扬声器按钮 */}
          <Button
            variant={isSpeakerOn ? "default" : "outline"}
            size="icon"
            className={`h-16 w-16 rounded-full transition-all ${
              isSpeakerOn
                ? "bg-[var(--color-primary)] text-[var(--color-primary-foreground)]"
                : "border-white/20 text-white hover:bg-white/10 hover:border-white/40"
            }`}
            onClick={() => setIsSpeakerOn(!isSpeakerOn)}
          >
            {isSpeakerOn ? (
              <Volume2 className="h-7 w-7" />
            ) : (
              <VolumeX className="h-7 w-7" />
            )}
          </Button>
        </div>

        {/* 底部提示文字 */}
        <p className="absolute bottom-8 text-xs text-white/30">
          {isCalling ? "点击红色按钮结束通话" : "点击绿色按钮开始通话"}
        </p>
      </div>
    </PageContainer>
  );
}
