import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { ArrowLeft, Heart, PanelRight, Sparkles } from "lucide-react";
import { motion } from "framer-motion";

interface ChatToolbarProps {
  personaName: string;
  personaAvatar?: string;
  favorabilityLevel: number;
  onBack: () => void;
  onToggleSidebar: () => void;
}

export function ChatToolbar({
  personaName,
  personaAvatar,
  favorabilityLevel,
  onBack,
  onToggleSidebar,
}: ChatToolbarProps) {
  return (
    <div className="glass-panel flex items-center justify-between px-4 py-2 border-b-0">
      <div className="flex items-center gap-3">
        <motion.button
          onClick={onBack}
          className="h-8 w-8 rounded-lg flex items-center justify-center text-white/30 hover:bg-white/[0.06] hover:text-white/60 transition-all"
          whileTap={{ scale: 0.9 }}
        >
          <ArrowLeft className="h-4 w-4" />
        </motion.button>

        <div className="flex items-center gap-2.5">
          <Avatar className="h-8 w-8 ring-2 ring-white/10">
            <AvatarImage src={personaAvatar} />
            <AvatarFallback className="bg-[var(--color-primary)]/20 text-[var(--color-primary)] text-xs font-medium">
              {personaName[0]}
            </AvatarFallback>
          </Avatar>

          <div>
            <h2 className="text-sm font-semibold text-white/90 leading-tight">
              {personaName}
            </h2>
            <div className="flex items-center gap-1.5 mt-0.5">
              <Heart className={`h-3 w-3 ${favorabilityLevel >= 4 ? "text-pink-400 fill-pink-400" : "text-white/20"}`} />
              <span className="text-[12px] font-medium text-white/30">
                好感 Lv.{favorabilityLevel}
              </span>
              <div className="h-1 w-8 rounded-full bg-white/[0.06] overflow-hidden">
                <motion.div
                  className="h-full rounded-full bg-[var(--color-primary)]"
                  initial={{ width: 0 }}
                  animate={{ width: `${Math.min(favorabilityLevel * 20, 100)}%` }}
                  transition={{ duration: 0.5 }}
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-1">
        <button
          className="h-8 w-8 rounded-lg flex items-center justify-center text-white/25 hover:bg-white/[0.06] hover:text-white/50 transition-all"
        >
          <Sparkles className="h-4 w-4" />
        </button>
        <button
          onClick={onToggleSidebar}
          className="h-8 w-8 rounded-lg flex items-center justify-center text-white/25 hover:bg-white/[0.06] hover:text-white/50 transition-all"
        >
          <PanelRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
