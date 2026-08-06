import { useNavigate } from "react-router";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { PersonaCard } from "./PersonaCard";
import { PersonaFeaturedCard } from "./PersonaFeaturedCard";
import { CreatePersonaDialog } from "./CreatePersonaDialog";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Plus, Search, Sparkles, Flame, Clock, Star, Heart } from "lucide-react";
import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";

const CATEGORIES = [
  { id: "all", label: "全部", icon: Sparkles },
  { id: "pinned", label: "置顶", icon: Star },
  { id: "favorite", label: "收藏", icon: Heart },
  { id: "recent", label: "最近", icon: Clock },
  { id: "hot", label: "热门", icon: Flame },
];

const staggerContainer = {
  hidden: {},
  visible: {
    transition: {
      staggerChildren: 0.06,
      delayChildren: 0.05,
    },
  },
};

const revealUp = {
  hidden: { opacity: 0, y: 16 },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      duration: 0.45,
      ease: [0.25, 0.46, 0.45, 0.94] as const,
    },
  },
};

export function HomePage() {
  const navigate = useNavigate();
  const { personas } = usePersonaStore();
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeCategory, setActiveCategory] = useState("all");

  const filteredPersonas = (() => {
    let list = personas;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          p.description.toLowerCase().includes(q) ||
          (p.tags ?? []).some((t) => t.toLowerCase().includes(q))
      );
    }
    switch (activeCategory) {
      case "pinned":
        return list.filter((p) => p.pinned);
      case "favorite":
        return list.filter((p) => p.favorited);
      case "recent":
        return [...list].sort((a, b) => b.lastChatTime - a.lastChatTime).slice(0, 20);
      case "hot":
        return [...list].sort((a, b) => b.favorabilityLevel - a.favorabilityLevel);
      default:
        return list;
    }
  })();

  const pinnedPersonas = personas.filter((p) => p.pinned);
  const nonPinnedPersonas = filteredPersonas.filter(
    (p) => !(activeCategory === "all" && !searchQuery && p.pinned)
  );

  const hasPinned = activeCategory === "all" && !searchQuery && pinnedPersonas.length > 0;

  return (
    <PageContainer>
      <div className="page-content flex flex-col h-full">
        {/* 标题区 - space-8 节奏 */}
        <div className="mb-8">
          {/* 标题行 */}
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-h1" style={{ color: "var(--color-card-foreground)" }}>
                我的角色
              </h1>
              <p className="text-caption mt-1.5">
                共 {personas.length} 个角色
              </p>
            </div>
            <motion.button
              onClick={() => setShowCreateDialog(true)}
              className="btn-primary"
              whileTap={{ scale: 0.97 }}
              whileHover={{ scale: 1.02 }}
            >
              <Plus className="h-4 w-4" />
              创建角色
            </motion.button>
          </div>

          {/* 搜索栏 */}
          <div className="relative mb-4">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--color-muted)]" />
            <Input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="搜索角色名称、描述或标签..."
              className="input-field pl-11"
            />
          </div>

          {/* 分类标签 */}
          <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
            {CATEGORIES.map((cat) => {
              const Icon = cat.icon;
              const isActive = activeCategory === cat.id;
              return (
                <button
                  key={cat.id}
                  onClick={() => setActiveCategory(cat.id)}
                  className={`chip ${isActive ? "chip-active" : ""}`}
                >
                  <Icon className="h-3.5 w-3.5" />
                  {cat.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* 内容区 - space-4/6 节奏 */}
        <ScrollArea className="flex-1">
          <AnimatePresence mode="wait">
            <motion.div
              key={`${activeCategory}-${searchQuery}`}
              variants={staggerContainer}
              initial="hidden"
              animate="visible"
              exit="hidden"
            >
              {/* 置顶角色区 - 混合布局：featured 大卡 + 2列网格 */}
              {hasPinned && (
                <div className="mb-6">
                  <div className="flex items-center gap-2 mb-4">
                    <Star className="h-3.5 w-3.5 text-[var(--color-primary)] fill-[var(--color-primary)]" />
                    <span className="text-label">置顶角色</span>
                  </div>

                  {/* 第一个置顶角色：featured 大卡 */}
                  <motion.div variants={revealUp} className="mb-4">
                    <PersonaFeaturedCard
                      persona={pinnedPersonas[0]}
                      onClick={() => navigate(`/chat/${pinnedPersonas[0].id}`)}
                    />
                  </motion.div>

                  {/* 其余置顶角色：2列网格 */}
                  {pinnedPersonas.length > 1 && (
                    <div className="grid grid-cols-2 gap-4">
                      {pinnedPersonas.slice(1).map((persona) => (
                        <motion.div key={persona.id} variants={revealUp}>
                          <PersonaCard
                            persona={persona}
                            onClick={() => navigate(`/chat/${persona.id}`)}
                          />
                        </motion.div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* 全部角色 / 非置顶角色 */}
              {(hasPinned ? nonPinnedPersonas : filteredPersonas).length > 0 && (
                <div>
                  {hasPinned && (
                    <div className="flex items-center gap-3 my-5">
                      <div className="h-px flex-1 bg-[var(--color-border)]" />
                      <span className="text-caption">全部角色</span>
                      <div className="h-px flex-1 bg-[var(--color-border)]" />
                    </div>
                  )}

                  {/* 混合网格：前3个 2列大卡片，其余 3列紧凑小卡片 */}
                  {!hasPinned && filteredPersonas.length > 0 ? (
                    <>
                      {/* 前3个：2列大卡片 */}
                      <div className="grid grid-cols-2 gap-4 mb-4">
                        {filteredPersonas.slice(0, 3).map((persona) => (
                          <motion.div key={persona.id} variants={revealUp}>
                            <PersonaCard
                              persona={persona}
                              onClick={() => navigate(`/chat/${persona.id}`)}
                              variant="large"
                            />
                          </motion.div>
                        ))}
                      </div>
                      {/* 其余：3列紧凑小卡片 */}
                      {filteredPersonas.length > 3 && (
                        <div className="grid grid-cols-3 gap-3">
                          {filteredPersonas.slice(3).map((persona) => (
                            <motion.div key={persona.id} variants={revealUp}>
                              <PersonaCard
                                persona={persona}
                                onClick={() => navigate(`/chat/${persona.id}`)}
                              />
                            </motion.div>
                          ))}
                        </div>
                      )}
                    </>
                  ) : hasPinned ? (
                    /* 有置顶时，非置顶角色用 3列紧凑网格 */
                    <div className="grid grid-cols-3 gap-3">
                      {nonPinnedPersonas.map((persona) => (
                        <motion.div key={persona.id} variants={revealUp}>
                          <PersonaCard
                            persona={persona}
                            onClick={() => navigate(`/chat/${persona.id}`)}
                          />
                        </motion.div>
                      ))}
                    </div>
                  ) : null}
                </div>
              )}

              {/* 空状态 */}
              {filteredPersonas.length === 0 && (
                <motion.div
                  variants={revealUp}
                  className="flex flex-col items-center justify-center py-20"
                >
                  <div className="relative mb-6">
                    <div className="h-20 w-20 rounded-2xl bg-[var(--color-primary)]/10 flex items-center justify-center">
                      <Sparkles className="empty-state-icon h-9 w-9 text-[var(--color-primary)]" />
                    </div>
                  </div>
                  <h3 className="text-h3 mb-2" style={{ color: "var(--color-card-foreground)" }}>
                    {searchQuery ? "没有找到匹配的角色" : "还没有角色"}
                  </h3>
                  <p className="text-body text-center max-w-xs mb-5" style={{ color: "var(--color-muted)" }}>
                    {searchQuery
                      ? "试试换个关键词搜索"
                      : "创建你的第一个 AI 角色，开始一段独特的对话旅程"}
                  </p>
                  {!searchQuery && (
                    <button onClick={() => setShowCreateDialog(true)} className="btn-ghost">
                      <Plus className="h-3.5 w-3.5" />
                      立即创建
                    </button>
                  )}
                </motion.div>
              )}
            </motion.div>
          </AnimatePresence>
        </ScrollArea>

        <CreatePersonaDialog
          open={showCreateDialog}
          onOpenChange={setShowCreateDialog}
        />
      </div>
    </PageContainer>
  );
}
