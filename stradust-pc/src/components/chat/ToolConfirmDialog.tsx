/**
 * 工具执行确认弹窗 —— ask 权限下在聊天界面上方浮出，
 * 提供 允许 / 总是允许 / 拒绝 三键（借鉴 Operit 工具权限交互）。
 */

import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ShieldQuestion, Check, X, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { on } from "@/lib/events";
import { resolveToolConfirm, type ToolConfirmRequest, type ToolDecision } from "@/engine/toolConfirm";
import { TOOL_LABELS } from "@/engine/plugins";

export function ToolConfirmDialog() {
  const [queue, setQueue] = useState<ToolConfirmRequest[]>([]);

  useEffect(
    () =>
      on<ToolConfirmRequest>("tool-confirm", (req) => {
        if (!req?.id) return;
        setQueue((q) => (q.some((x) => x.id === req.id) ? q : [...q, req]));
      }),
    [],
  );

  const answer = (decision: ToolDecision) => {
    const [head, ...rest] = queue;
    if (head) resolveToolConfirm(head.id, decision);
    setQueue(rest);
  };

  const current = queue[0];

  return (
    <AnimatePresence>
      {current && (
        <motion.div
          initial={{ opacity: 0, y: 24, scale: 0.96 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 12, scale: 0.98 }}
          transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
          className="pointer-events-auto fixed bottom-24 left-1/2 z-[999] -translate-x-1/2 w-[min(440px,calc(100vw-32px))]"
        >
          <div className="surface-card p-4 shadow-xl">
            <div className="flex items-start gap-3">
              <div className="h-9 w-9 shrink-0 rounded-xl bg-[var(--color-primary)]/15 flex items-center justify-center">
                <ShieldQuestion className="h-5 w-5 text-[var(--color-primary)]" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-[var(--color-card-foreground)]">
                  AI 想要执行「{TOOL_LABELS[current.toolName] ?? current.toolName}」
                </p>
                {current.argsSummary && (
                  <p className="mt-1 text-xs text-[var(--color-muted-foreground)] break-words">
                    {current.argsSummary}
                  </p>
                )}
                {queue.length > 1 && (
                  <p className="mt-1 text-[10px] text-[var(--color-muted-foreground)]/70">还有 {queue.length - 1} 个待确认</p>
                )}
              </div>
            </div>
            <div className="mt-3 flex justify-end gap-2">
              <Button size="sm" variant="ghost" className="text-[var(--color-muted-foreground)]" onClick={() => answer("deny")}>
                <X className="h-3.5 w-3.5 mr-1" /> 拒绝
              </Button>
              <Button size="sm" variant="outline" onClick={() => answer("always")}>
                <ShieldCheck className="h-3.5 w-3.5 mr-1" /> 总是允许
              </Button>
              <Button size="sm" onClick={() => answer("allow")}>
                <Check className="h-3.5 w-3.5 mr-1" /> 允许
              </Button>
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
