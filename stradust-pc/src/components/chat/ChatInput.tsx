import { useState, useRef, useCallback } from "react";
import { Textarea } from "@/components/ui/textarea";
import { VoiceRecorder } from "./VoiceRecorder";
import { StickerPicker } from "./StickerPicker";
import { Send, Paperclip, Smile, X, Image as ImageIcon, Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";
import { isTauri } from "@/lib/tauri";
import { motion, AnimatePresence } from "framer-motion";

interface ChatInputProps {
  onSend: (content: string, attachments?: string[]) => void;
  disabled?: boolean;
  placeholder?: string;
  className?: string;
  compact?: boolean;
}

export function ChatInput({
  onSend,
  disabled = false,
  placeholder = "说点什么...",
  className,
  compact = false,
}: ChatInputProps) {
  const [content, setContent] = useState("");
  const [showStickerPicker, setShowStickerPicker] = useState(false);
  const [selectedImages, setSelectedImages] = useState<string[]>([]);
  const [imageUrls, setImageUrls] = useState<Record<number, string>>({});
  const [isFocused, setIsFocused] = useState(false);
  const [justSent, setJustSent] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const canSend = content.trim().length > 0 || selectedImages.length > 0;

  const handleSend = useCallback(() => {
    const trimmed = content.trim();
    if ((!trimmed && selectedImages.length === 0) || disabled) return;
    onSend(trimmed, selectedImages.length > 0 ? selectedImages : undefined);
    setContent("");
    setSelectedImages([]);
    setJustSent(true);
    setTimeout(() => setJustSent(false), 600);
    textareaRef.current?.focus();
  }, [content, disabled, onSend, selectedImages]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend]
  );

  const handleStickerSelect = useCallback(
    (stickerPath: string) => {
      onSend(`[sticker:${stickerPath}]`);
      setShowStickerPicker(false);
    },
    [onSend]
  );

  const handleVoiceResult = useCallback(
    (text: string) => {
      onSend(text);
    },
    [onSend]
  );

  const handleSelectImage = useCallback(async () => {
    if (!isTauri()) { console.warn("Image selection not supported in browser"); return; }
    try {
      const { open } = await import("@tauri-apps/plugin-dialog");
      const filePath = await open({
        multiple: true,
        filters: [{ name: "Images", extensions: ["png", "jpg", "jpeg", "gif", "webp", "bmp"] }],
      });
      if (filePath) {
        const paths = Array.isArray(filePath) ? filePath : [filePath];
        setSelectedImages((prev) => {
          const newPaths = [...prev, ...paths];
          // 异步转换每个文件路径为可访问 URL
          paths.forEach((path, i) => {
            const idx = prev.length + i;
            convertFileSrc(path).then((url) => {
              setImageUrls((prev) => ({ ...prev, [idx]: url }));
            });
          });
          return newPaths;
        });
      }
    } catch (error) {
      console.error("Failed to select image:", error);
    }
  }, []);

  const removeImage = useCallback((index: number) => {
    setSelectedImages((prev) => prev.filter((_, i) => i !== index));
  }, []);

  return (
    <div className={cn("chat-input-surface relative", className, compact && "!p-0")}>
      {/* Sticker picker - 使用 surface-elevated */}
      <AnimatePresence>
        {showStickerPicker && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 10 }}
            transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
            className="surface-elevated mb-2 mx-3"
          >
            <StickerPicker
              onSelect={handleStickerSelect}
              onClose={() => setShowStickerPicker(false)}
            />
          </motion.div>
        )}
      </AnimatePresence>

      {/* Image preview strip - 使用 surface-card */}
      <AnimatePresence>
        {selectedImages.length > 0 && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
            className="overflow-hidden"
          >
            <div className="flex gap-2 p-3 overflow-x-auto">
              {selectedImages.map((imgPath, index) => (
                <motion.div
                  key={index}
                  initial={{ scale: 0.8, opacity: 0 }}
                  animate={{ scale: 1, opacity: 1 }}
                  exit={{ scale: 0.8, opacity: 0 }}
                  transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
                  className="relative shrink-0 w-16 h-16 surface-card overflow-hidden"
                >
                  <img
                    src={imageUrls[index] ?? imgPath}
                    alt={`Attachment ${index + 1}`}
                    className="w-full h-full object-cover"
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = "none";
                    }}
                  />
                  <button
                    onClick={() => removeImage(index)}
                    className="absolute top-0.5 right-0.5 w-4 h-4 bg-black/60 rounded-full flex items-center justify-center hover:bg-red-500/80 transition-colors"
                  >
                    <X className="h-2.5 w-2.5 text-white" />
                  </button>
                </motion.div>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Input area */}
      <div className={cn("flex items-end gap-1.5 p-3", compact && "!p-2 !gap-1")}>
        {/* Left action buttons - 使用 btn-ghost */}
        <div className={cn("flex items-center gap-0.5 shrink-0 pb-0.5", compact && "hidden")}>
          <ActionIconButton
            icon={<ImageIcon className="h-4 w-4" />}
            title="发送图片"
            onClick={handleSelectImage}
          />
          <ActionIconButton
            icon={<Paperclip className="h-4 w-4" />}
            title="附件"
          />
          <ActionIconButton
            icon={<Smile className="h-4 w-4" />}
            title="表情"
            onClick={() => setShowStickerPicker(!showStickerPicker)}
            active={showStickerPicker}
          />
        </div>

        {/* Text input - 使用 input-field 类 */}
        <div className="flex-1 relative">
          <Textarea
            ref={textareaRef}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            placeholder={placeholder}
            disabled={disabled}
            className={cn(
              "input-field min-h-[42px] max-h-[120px] resize-none rounded-xl",
              "bg-white/[0.04] border-white/[0.06]",
              "text-white/80 placeholder:text-white/20 text-[14px] leading-relaxed",
              compact && "!min-h-[32px] !text-[13px] !rounded-lg"
            )}
            rows={1}
          />
        </div>

        {/* Voice recorder */}
        <div className={cn("shrink-0 pb-0.5", compact && "hidden")}>
          <VoiceRecorder onResult={handleVoiceResult} />
        </div>

        {/* Send button - 使用 btn-primary 样式 */}
        <div className={cn("shrink-0 pb-0.5", compact && "!pb-0")}>
          <motion.button
            onClick={handleSend}
            disabled={disabled || !canSend}
            className={cn(
              "btn-primary p-0",
              !compact && "h-9 w-9",
              compact && "h-7 w-7",
              !canSend && "opacity-50 cursor-not-allowed"
            )}
            whileTap={canSend ? { scale: 0.9 } : undefined}
          >
            {justSent ? (
              <Sparkles className={cn("h-4 w-4", compact && "!h-3 !w-3")} />
            ) : (
              <Send className={cn("h-4 w-4", compact && "!h-3 !w-3")} />
            )}
          </motion.button>
        </div>
      </div>
    </div>
  );
}

/* 操作图标按钮使用 btn-ghost */
function ActionIconButton({
  icon,
  title,
  onClick,
  active = false,
}: {
  icon: React.ReactNode;
  title: string;
  onClick?: () => void;
  active?: boolean;
}) {
  return (
    <motion.button
      onClick={onClick}
      title={title}
      className={cn(
        "btn-ghost h-8 w-8 p-0",
        active && "bg-[var(--color-primary)]/15 text-[var(--color-primary)]"
      )}
      whileTap={{ scale: 0.9 }}
    >
      {icon}
    </motion.button>
  );
}

/** 将本地文件路径转换为可访问的 URL */
async function convertFileSrc(filePath: string): Promise<string> {
  if (isTauri()) {
    try {
      const { convertFileSrc: tauriConvert } = await import("@tauri-apps/api/core");
      return tauriConvert(filePath);
    } catch {
      // fallback：直接使用文件路径（某些情况下 WebView 可访问）
      return filePath;
    }
  }
  // Web 模式：无法访问本地文件，返回占位
  return filePath;
}
