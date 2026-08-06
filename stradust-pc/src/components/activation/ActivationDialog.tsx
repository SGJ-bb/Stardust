import { useState } from "react";
import { useActivationStore } from "@/stores/useActivationStore";
import { Lock, Sparkles, Crown, ArrowRight, Copy, Check } from "lucide-react";

/**
 * 激活对话框
 *
 * 两步流程：
 * Step 1: 首次使用 → 输入口令（口令明文显示在界面上）
 * Step 2: 高级功能 → 输入密钥 "1314520" 解锁入场动画、雨滴效果等
 */

const FIRST_PASSPHRASE = "时光机大人宇宙无敌超级厉害";

type Step = "first" | "premium";

export function ActivationDialog() {
  const { isFirstActivated, isPremiumUnlocked, activateFirst, unlockPremium } = useActivationStore();
  const [step, setStep] = useState<Step>("first");
  const [input, setInput] = useState("");
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  // 是否已看过并处理过 premium 步骤（跳过或解锁后关闭）
  const [dismissed, setDismissed] = useState(false);

  // 已完全激活（基础+高级）→ 不显示
  if (isFirstActivated && isPremiumUnlocked) return null;
  // 用户已跳过 premium 步骤 → 不再显示
  if (dismissed && isFirstActivated) return null;

  const handleFirstSubmit = () => {
    if (input.trim() === FIRST_PASSPHRASE) {
      activateFirst();
      setError("");
      setInput("");
      setStep("premium");
    } else {
      setError("输入不正确哦，请复制上方口令");
    }
  };

  const handlePremiumSubmit = () => {
    if (unlockPremium(input.trim())) {
      setError("");
      setDismissed(true); // 解锁成功，关闭对话框
    } else {
      setError("密钥错误，请检查后重试");
    }
  };

  /** 跳过高级功能 → 进入基础模式 */
  const handleSkip = () => {
    setDismissed(true);
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(FIRST_PASSPHRASE).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/70 backdrop-blur-sm">
      <div className="w-full max-w-md mx-4 rounded-2xl border border-white/10 bg-gradient-to-b from-[var(--color-card)] to-[var(--color-card)]/80 p-8 shadow-2xl shadow-black/50">
        {/* 标题区域 */}
        <div className="text-center mb-6">
          {step === "first" ? (
            <>
              <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[var(--color-primary)]/15 mb-4">
                <Sparkles className="h-8 w-8 text-[var(--color-primary)]" />
              </div>
              <h2 className="text-xl font-bold mb-2">欢迎来到星尘</h2>
              <p className="text-sm text-[var(--color-muted-foreground)]">
                请复制下方口令完成激活
              </p>
            </>
          ) : (
            <>
              <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-yellow-500/15 mb-4">
                <Crown className="h-8 w-8 text-yellow-500" />
              </div>
              <h2 className="text-xl font-bold mb-2">解锁高级功能</h2>
              <p className="text-sm text-[var(--color-muted-foreground)]">
                输入密钥解锁矢量入场动画、雨滴效果等高级功能
              </p>
            </>
          )}
        </div>

        {/* 口令显示区域 — 仅第一步 */}
        {step === "first" && (
          <div className="mb-5">
            <p className="text-xs text-[var(--color-muted-foreground)] mb-2 text-center">激活口令（点击复制）：</p>
            <div
              onClick={handleCopy}
              className="relative group cursor-pointer rounded-xl border-2 border-dashed border-[var(--color-primary)]/30 bg-[var(--color-primary)]/5 p-3.5 hover:border-[var(--color-primary)]/60 hover:bg-[var(--color-primary)]/10 transition-all"
            >
              <p className="text-center text-base font-bold tracking-wide text-[var(--color-primary)] select-none break-all leading-relaxed">
                {FIRST_PASSPHRASE}
              </p>
              {/* 复制按钮覆盖层 */}
              <div className={`absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-1 px-2 py-1 rounded-md bg-[var(--color-card)] border border-white/10 text-[10px] transition-opacity ${copied ? "opacity-100 text-green-500" : "opacity-0 group-hover:opacity-100 text-[var(--color-muted-foreground)]"}`}>
                {copied ? (
                  <><Check className="h-3 w-3" /> 已复制</>
                ) : (
                  <><Copy className="h-3 w-3" /> 复制</>
                )}
              </div>
            </div>
            <p className="text-[10px] text-[var(--color-muted-foreground)]/50 text-center mt-2">
              复制后粘贴到下方输入框 → 点击激活
            </p>
          </div>
        )}

        {/* 输入区域 */}
        <div className="space-y-4">
          <div className="relative">
            <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--color-muted-foreground)]" />
            <input
              type="text"
              value={input}
              onChange={(e) => { setInput(e.target.value); setError(""); }}
              onKeyDown={(e) => e.key === "Enter" && (step === "first" ? handleFirstSubmit() : handlePremiumSubmit())}
              placeholder={step === "first" ? "粘贴口令到这里..." : "请输入高级功能密钥..."}
              className="w-full h-12 pl-10 pr-4 rounded-xl border border-[var(--color-border)] bg-[var(--color-background)] text-sm placeholder:text-[var(--color-muted-foreground)] focus:border-[var(--color-primary)] focus:ring-1 focus:ring-[var(--color-primary)]/30 outline-none transition-all"
              autoFocus
            />
          </div>

          {error && (
            <p className="text-xs text-red-400 text-center">{error}</p>
          )}

          {/* 按钮 */}
          <div className="flex gap-3 pt-2">
            {step === "premium" && (
              <button
                onClick={handleSkip}
                className="flex-1 h-11 rounded-xl border border-[var(--color-border)] text-sm text-[var(--color-muted-foreground)] hover:bg-[var(--color-border)]/30 transition-colors"
              >
                跳过
              </button>
            )}
            <button
              onClick={step === "first" ? handleFirstSubmit : handlePremiumSubmit}
              className="flex-1 h-11 rounded-xl bg-[var(--color-primary)] text-sm font-medium text-white hover:brightness-110 transition-all flex items-center justify-center gap-2"
            >
              {step === "first" ? "激活" : "解锁"} <ArrowRight className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* 底部装饰 */}
        <div className="mt-6 pt-4 border-t border-[var(--color-border)]/50 text-center">
          <p className="text-[10px] text-[var(--color-muted-foreground)]/40">
            星尘 AI 伴侣 · 让每个瞬间都有温度
          </p>
        </div>
      </div>
    </div>
  );
}
