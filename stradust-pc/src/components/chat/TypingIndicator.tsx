import { cn } from "@/lib/utils";

interface TypingIndicatorProps {
  className?: string;
}

/**
 * 打字指示器组件
 * 显示AI正在输入的状态
 */
export function TypingIndicator({ className }: TypingIndicatorProps) {
  return (
    <div className={cn("flex items-center gap-1 px-4 py-2", className)}>
      <div className="flex gap-1">
        <span className="typing-dot h-2 w-2 rounded-full bg-[var(--color-muted-foreground)]" />
        <span className="typing-dot h-2 w-2 rounded-full bg-[var(--color-muted-foreground)]" />
        <span className="typing-dot h-2 w-2 rounded-full bg-[var(--color-muted-foreground)]" />
      </div>
      <span className="text-xs text-[var(--color-muted-foreground)]">正在输入...</span>
    </div>
  );
}
