import { useState } from "react";
import { cn } from "@/lib/utils";
import { Sparkles, Smile, Play, X } from "lucide-react";

export interface MotionGroupInfo {
  group: string;
  count: number;
}

interface PetDockProps {
  /** 可用表情名（来自 model3 Expressions） */
  expressions: string[];
  /** 可用动作组（来自 model3 Motions） */
  motions: MotionGroupInfo[];
  /** 可用序列帧特效（来自 petfx 索引） */
  frames?: string[];
  onExpression: (name: string) => void;
  onMotion: (group: string) => void;
  onFrames?: (name: string) => void;
  className?: string;
}

/**
 * 桌宠交互坞：表情 / 动作 / 特效 三组按钮。
 * 数据全部来自模型自身（换模型自动换内容，不写死）。
 */
export function PetDock({
  expressions,
  motions,
  frames = [],
  onExpression,
  onMotion,
  onFrames,
  className,
}: PetDockProps) {
  const [open, setOpen] = useState(true);
  const [tab, setTab] = useState<"expr" | "motion" | "fx">("expr");

  const empty =
    (tab === "expr" && !expressions.length) ||
    (tab === "motion" && !motions.length) ||
    (tab === "fx" && !frames.length);

  return (
    <div
      className={cn(
        "pointer-events-auto select-none flex flex-col items-end gap-1.5",
        className,
      )}
    >
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="h-7 w-7 rounded-full border border-[var(--color-border)] bg-[var(--color-card)]/85 backdrop-blur-md shadow flex items-center justify-center text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]"
        title={open ? "收起交互坞" : "展开交互坞"}
      >
        {open ? <X className="h-3.5 w-3.5" /> : <Sparkles className="h-3.5 w-3.5" />}
      </button>

      {open && (
        <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-card)]/90 backdrop-blur-md shadow-lg p-1.5 w-[172px]">
          <div className="flex gap-1 mb-1.5">
            {(
              [
                ["expr", "表情", Smile],
                ["motion", "动作", Play],
                ["fx", "特效", Sparkles],
              ] as const
            ).map(([k, label, Icon]) => (
              <button
                key={k}
                type="button"
                onClick={() => setTab(k)}
                className={cn(
                  "flex-1 h-6 rounded-md text-[11px] flex items-center justify-center gap-1",
                  tab === k
                    ? "bg-[var(--color-primary)] text-[var(--color-primary-foreground)]"
                    : "text-[var(--color-muted-foreground)] hover:bg-[var(--color-accent)]",
                )}
              >
                <Icon className="h-3 w-3" />
                {label}
              </button>
            ))}
          </div>

          <div className="max-h-[168px] overflow-y-auto pr-0.5 space-y-1">
            {empty && (
              <div className="text-[11px] text-[var(--color-muted-foreground)] px-1 py-2 leading-relaxed">
                该模型无可用{tab === "expr" ? "表情" : tab === "motion" ? "动作" : "特效"}
              </div>
            )}

            {tab === "expr" &&
              expressions.map((n) => (
                <button
                  key={n}
                  type="button"
                  onClick={() => onExpression(n)}
                  className="w-full text-left px-1.5 py-1 rounded-md text-[11px] text-[var(--color-foreground)] hover:bg-[var(--color-accent)] truncate"
                  title={n}
                >
                  {n}
                </button>
              ))}

            {tab === "motion" &&
              motions.map((m) => (
                <button
                  key={m.group}
                  type="button"
                  onClick={() => onMotion(m.group)}
                  className="w-full text-left px-1.5 py-1 rounded-md text-[11px] text-[var(--color-foreground)] hover:bg-[var(--color-accent)] flex items-center justify-between"
                  title={`播放 ${m.group}`}
                >
                  <span className="truncate">{m.group}</span>
                  {m.count > 1 && (
                    <span className="text-[10px] text-[var(--color-muted-foreground)]">
                      ×{m.count}
                    </span>
                  )}
                </button>
              ))}

            {tab === "fx" &&
              frames.map((n) => (
                <button
                  key={n}
                  type="button"
                  onClick={() => onFrames?.(n)}
                  className="w-full text-left px-1.5 py-1 rounded-md text-[11px] text-[var(--color-foreground)] hover:bg-[var(--color-accent)] truncate"
                  title={`播放特效 ${n}`}
                >
                  {n}
                </button>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
