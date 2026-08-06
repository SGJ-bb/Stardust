import { useNavigate } from "react-router";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { MoreVertical, Pin, Star, Trash2, Edit, Heart, Clock } from "lucide-react";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { formatTime, truncate } from "@/lib/utils";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import type { Persona } from "@/types/persona";
import { useState } from "react";
import { motion } from "framer-motion";

interface PersonaCardProps {
  persona: Persona;
  onClick: () => void;
  variant?: "default" | "large";
}

export function PersonaCard({ persona, onClick, variant = "default" }: PersonaCardProps) {
  const navigate = useNavigate();
  const { togglePin, toggleFavorite, deletePersona } = usePersonaStore();
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const isLarge = variant === "large";

  return (
    <motion.div
      whileHover={{ y: -2, transition: { duration: 0.2 } }}
      whileTap={{ scale: 0.98 }}
      className="surface-card group cursor-pointer"
      style={{ borderRadius: "var(--radius-lg)" }}
      onClick={onClick}
    >
      <div className={isLarge ? "p-5" : "p-4"}>
        <div className={`flex items-start gap-${isLarge ? "4" : "3"}`}>
          {/* 头像 */}
          <Avatar
            className={`${isLarge ? "h-14 w-14" : "h-11 w-11"} shrink-0`}
            style={{ borderRadius: "var(--radius-md)" }}
          >
            <AvatarImage src={persona.avatar} />
            <AvatarFallback
              className={`${isLarge ? "text-base" : "text-sm"} font-semibold`}
              style={{
                backgroundColor: "var(--color-primary)",
                color: "var(--color-primary)",
                opacity: 0.15,
              }}
            >
              <span style={{ color: "var(--color-primary)", opacity: 1 }}>
                {persona.name[0]}
              </span>
            </AvatarFallback>
          </Avatar>

          {/* 信息区 */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center justify-between">
              <h3
                className={`font-semibold truncate ${isLarge ? "text-base" : "text-sm"}`}
                style={{ color: "var(--color-card-foreground)" }}
              >
                {persona.name}
              </h3>
              <DropdownMenu>
                <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                  <button
                    className="opacity-0 group-hover:opacity-100 transition-opacity h-6 w-6 inline-flex items-center justify-center hover:bg-[var(--color-muted)]/10"
                    style={{ borderRadius: "var(--radius-sm)" }}
                  >
                    <MoreVertical
                      className="h-3.5 w-3.5"
                      style={{ color: "var(--color-muted)" }}
                    />
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
                  <DropdownMenuItem onClick={() => togglePin(persona.id)}>
                    <Pin className="mr-2 h-3.5 w-3.5" />
                    {persona.pinned ? "取消置顶" : "置顶"}
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => toggleFavorite(persona.id)}>
                    <Star
                      className={`mr-2 h-3.5 w-3.5 ${
                        persona.favorited
                          ? "fill-yellow-400 text-yellow-400"
                          : ""
                      }`}
                    />
                    {persona.favorited ? "取消收藏" : "收藏"}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() =>
                      navigate(`/persona/${persona.id}/edit`)
                    }
                  >
                    <Edit className="mr-2 h-3.5 w-3.5" />
                    编辑
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() => setShowDeleteConfirm(true)}
                    className="text-red-400 focus:text-red-400"
                  >
                    <Trash2 className="mr-2 h-3.5 w-3.5" />
                    删除
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>

            <p
              className={`mt-0.5 line-clamp-${isLarge ? "3" : "2"} leading-relaxed ${
                isLarge ? "text-body" : "text-caption"
              }`}
              style={{ color: "var(--color-muted)" }}
            >
              {truncate(persona.description, isLarge ? 100 : 80)}
            </p>
          </div>
        </div>

        {/* 标签 */}
        {(persona.tags ?? []).length > 0 && (
          <div className="flex flex-wrap gap-1 mt-2.5">
            {(persona.tags ?? []).slice(0, isLarge ? 4 : 3).map((tag) => (
              <span key={tag} className="chip chip-sm">
                {tag}
              </span>
            ))}
            {(persona.tags ?? []).length > (isLarge ? 4 : 3) && (
              <span className="text-caption" style={{ color: "var(--color-muted)" }}>
                +{(persona.tags ?? []).length - (isLarge ? 4 : 3)}
              </span>
            )}
          </div>
        )}

        {/* 底部统计 - 简洁一行，无分割线 */}
        <div className="flex items-center justify-between mt-3">
          <div className="flex items-center gap-2">
            <Heart
              className={`h-3 w-3 ${
                persona.favorabilityLevel >= 4
                  ? "fill-pink-400 text-pink-400"
                  : ""
              }`}
              style={
                persona.favorabilityLevel < 4
                  ? { color: "var(--color-muted)" }
                  : {}
              }
            />
            <span
              className="text-caption font-medium"
              style={{ color: "var(--color-card-foreground)" }}
            >
              好感 Lv.{persona.favorabilityLevel}
            </span>
            <div
              className={`h-1.5 ${isLarge ? "w-16" : "w-14"} rounded-full overflow-hidden`}
              style={{ backgroundColor: "var(--color-border)" }}
            >
              <motion.div
                className="h-full rounded-full"
                style={{ backgroundColor: "var(--color-primary)" }}
                initial={{ width: 0 }}
                animate={{
                  width: `${Math.min(persona.favorabilityLevel * 20, 100)}%`,
                }}
                transition={{
                  duration: 0.6,
                  ease: [0.16, 1, 0.3, 1] as [number, number, number, number],
                }}
              />
            </div>
          </div>

          {persona.lastChatTime > 0 && (
            <div className="flex items-center gap-1">
              <Clock
                className="h-3 w-3"
                style={{ color: "var(--color-muted)" }}
              />
              <span className="text-caption" style={{ color: "var(--color-muted)" }}>
                {formatTime(persona.lastChatTime)}
              </span>
            </div>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={showDeleteConfirm}
        onOpenChange={setShowDeleteConfirm}
        title="删除角色"
        description={`确定要删除角色「${persona.name}」吗？此操作不可撤销。`}
        confirmText="删除"
        variant="destructive"
        onConfirm={() => deletePersona(persona.id)}
      />
    </motion.div>
  );
}
