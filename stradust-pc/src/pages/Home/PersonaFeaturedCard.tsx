import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Heart, Clock } from "lucide-react";
import { motion } from "framer-motion";
import type { Persona } from "@/types/persona";
import { formatTime, truncate } from "@/lib/utils";

interface PersonaFeaturedCardProps {
  persona: Persona;
  onClick: () => void;
}

export function PersonaFeaturedCard({ persona, onClick }: PersonaFeaturedCardProps) {
  return (
    <motion.div
      whileHover={{ y: -2, transition: { duration: 0.2 } }}
      whileTap={{ scale: 0.99 }}
      className="surface-card featured-card cursor-pointer"
      style={{ borderRadius: "var(--radius-lg)" }}
      onClick={onClick}
    >
      <div className="flex gap-5 p-5">
        {/* 左侧大头像 */}
        <Avatar
          className="h-20 w-20 shrink-0"
          style={{ borderRadius: "var(--radius-lg)" }}
        >
          <AvatarImage src={persona.avatar} />
          <AvatarFallback
            className="text-xl font-semibold"
            style={{
              backgroundColor: "var(--color-primary)",
              color: "white",
              opacity: 0.15,
            }}
          >
            <span style={{ color: "var(--color-primary)" }}>{persona.name[0]}</span>
          </AvatarFallback>
        </Avatar>

        {/* 右侧信息区 */}
        <div className="flex-1 min-w-0 flex flex-col justify-between">
          <div>
            <h3
              className="text-h3 truncate mb-1.5"
              style={{ color: "var(--color-card-foreground)" }}
            >
              {persona.name}
            </h3>
            <p
              className="text-body line-clamp-2 leading-relaxed mb-3"
              style={{ color: "var(--color-muted)" }}
            >
              {truncate(persona.description, 120)}
            </p>

            {/* 标签 */}
            {(persona.tags ?? []).length > 0 && (
              <div className="flex flex-wrap gap-1.5 mb-3">
                {persona.tags.slice(0, 5).map((tag) => (
                  <span key={tag} className="chip chip-sm">
                    {tag}
                  </span>
                ))}
                {persona.tags.length > 5 && (
                  <span
                    className="text-caption"
                    style={{ color: "var(--color-muted)" }}
                  >
                    +{persona.tags.length - 5}
                  </span>
                )}
              </div>
            )}
          </div>

          {/* 底部统计行 */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <Heart
                className={`h-3.5 w-3.5 ${
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
                className="h-1.5 w-24 rounded-full overflow-hidden"
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
                  className="h-3.5 w-3.5"
                  style={{ color: "var(--color-muted)" }}
                />
                <span
                  className="text-caption"
                  style={{ color: "var(--color-muted)" }}
                >
                  {formatTime(persona.lastChatTime)}
                </span>
              </div>
            )}
          </div>
        </div>
      </div>
    </motion.div>
  );
}
