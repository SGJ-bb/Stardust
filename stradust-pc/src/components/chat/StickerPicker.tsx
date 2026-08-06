import { useState } from "react";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Input } from "@/components/ui/input";
import { EMOJI_CATEGORIES } from "@/lib/emojis";
import { Search } from "lucide-react";
import { cn } from "@/lib/utils";

interface StickerPickerProps {
  /** 选择表情包回调 */
  onSelect: (path: string) => void;
  /** 关闭回调 */
  onClose: () => void;
  className?: string;
}

/**
 * 表情包选择器组件
 */
export function StickerPicker({ onSelect, onClose, className }: StickerPickerProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [activeCategory, setActiveCategory] = useState(0);

  return (
    <div
      className={cn(
        "absolute bottom-full left-0 right-0 mb-2 rounded-[var(--app-radius)] border border-[var(--color-border)] bg-[var(--color-popover)] shadow-lg",
        className
      )}
    >
      {/* 搜索栏 */}
      <div className="flex items-center gap-2 border-b border-[var(--color-border)] p-2">
        <Search className="h-4 w-4 text-[var(--color-muted-foreground)]" />
        <Input
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="搜索表情..."
          className="h-8 border-0 bg-transparent focus-visible:ring-0"
        />
      </div>

      {/* 分类标签 */}
      <div className="flex gap-1 border-b border-[var(--color-border)] px-2 py-1">
        {EMOJI_CATEGORIES.map((category, index) => (
          <button
            key={category.name}
            onClick={() => setActiveCategory(index)}
            className={cn(
              "rounded-sm px-2 py-1 text-xs transition-colors",
              activeCategory === index
                ? "bg-[var(--color-accent)] text-[var(--color-accent-foreground)]"
                : "text-[var(--color-muted-foreground)] hover:bg-[var(--color-muted)]"
            )}
          >
            {category.name}
          </button>
        ))}
      </div>

      {/* 表情列表 */}
      <ScrollArea className="h-[200px] p-2">
        <div className="grid grid-cols-8 gap-1">
          {EMOJI_CATEGORIES[activeCategory]?.emojis
            .filter((emoji) => !searchQuery || emoji.includes(searchQuery))
            .map((emoji, index) => (
            <button
              key={index}
              onClick={() => onSelect(emoji)}
              className="flex h-9 w-9 items-center justify-center rounded-sm text-lg hover:bg-[var(--color-muted)] transition-colors"
            >
              {emoji}
            </button>
          ))}
        </div>
      </ScrollArea>
    </div>
  );
}
