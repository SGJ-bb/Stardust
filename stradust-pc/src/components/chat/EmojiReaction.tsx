import { cn } from "@/lib/utils";

interface EmojiReactionProps {
  emoji: string;
  count: number;
  reacted: boolean;
  onClick?: () => void;
}

/**
 * 表情回应组件
 */
export function EmojiReaction({ emoji, count, reacted, onClick }: EmojiReactionProps) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs transition-colors",
        reacted
          ? "bg-[var(--color-primary)]/10 text-[var(--color-primary)]"
          : "bg-[var(--color-muted)] text-[var(--color-muted-foreground)] hover:bg-[var(--color-accent)]"
      )}
    >
      <span>{emoji}</span>
      {count > 1 && <span>{count}</span>}
    </button>
  );
}
