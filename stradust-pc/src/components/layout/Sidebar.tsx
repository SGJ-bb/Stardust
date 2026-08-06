import { useLocation, useNavigate } from "react-router";
import {
  Home,
  MessageCircle,
  Users,
  Brain,
  BookOpen,
  Image,
  Globe,
  Trophy,
  Calendar,
  Sticker,
  ShoppingBag,
  Box,
  Settings,
  AlarmClock,
  Phone,
  Radio,
  Camera,
  Timer,
  History,
  Key,
  Cpu,
  Zap,
  ChevronDown,
  Sparkles,
  Maximize2,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { isTauri } from "@/lib/tauri";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { useChatStore } from "@/stores/useChatStore";
import { Tooltip, TooltipContent, TooltipTrigger, TooltipProvider } from "@/components/ui/tooltip";
import { AnimatePresence, motion } from "framer-motion";
import { useState } from "react";

interface NavItem {
  icon: React.ElementType;
  label: string;
  path: string;
}

const PRIMARY_NAV: NavItem[] = [
  { icon: Home, label: "首页", path: "/" },
  { icon: MessageCircle, label: "聊天", path: "/chat" },
  { icon: Users, label: "群聊", path: "/group-chat" },
  { icon: Brain, label: "记忆", path: "/memory" },
  { icon: BookOpen, label: "日记", path: "/diary" },
  { icon: Image, label: "朋友圈", path: "/moments" },
];

const SOCIAL_NAV: NavItem[] = [
  { icon: Globe, label: "世界", path: "/world" },
  { icon: Trophy, label: "成就", path: "/achievement" },
  { icon: Calendar, label: "日历", path: "/calendar" },
  { icon: Radio, label: "电台", path: "/bedtime-radio" },
  { icon: Camera, label: "相册", path: "/album" },
  { icon: Timer, label: "胶囊", path: "/capsule" },
  { icon: Phone, label: "通话", path: "/phone-call" },
];

const TOOLS_NAV: NavItem[] = [
  { icon: Sticker, label: "表情", path: "/sticker" },
  { icon: ShoppingBag, label: "皮肤", path: "/skin-shop" },
  { icon: Box, label: "模型", path: "/model-manager" },
  { icon: AlarmClock, label: "闹钟", path: "/alarm" },
  { icon: History, label: "记录", path: "/chat-history" },
  { icon: Key, label: "激活", path: "/activation" },
  { icon: Cpu, label: "本地AI", path: "/local-model" },
  { icon: Zap, label: "Agent", path: "/agent" },
];

function CollapsibleSection({
  label,
  items,
  defaultOpen = false,
  collapsed,
  isActive,
  getNavPath,
  onNavigate,
}: {
  label: string;
  items: NavItem[];
  defaultOpen?: boolean;
  collapsed: boolean;
  isActive: (path: string) => boolean;
  getNavPath: (path: string) => string;
  onNavigate: (path: string) => void;
}) {
  const [open, setOpen] = useState(defaultOpen);

  if (collapsed) {
    return (
      <>
        <div className="sidebar-section-divider" />
        {items.map((item) => {
          const active = isActive(item.path);
          const Icon = item.icon;
          const button = (
            <button
              key={item.path}
              onClick={() => onNavigate(getNavPath(item.path))}
              className={cn(
                "sidebar-item flex w-full items-center justify-center px-0 py-2 text-sm",
                active && "active"
              )}
            >
              <Icon className={cn("h-[18px] w-[18px] shrink-0")} />
            </button>
          );
          return (
            <Tooltip key={item.path}>
              <TooltipTrigger asChild>{button}</TooltipTrigger>
              <TooltipContent side="right" className="surface-panel text-xs">{item.label}</TooltipContent>
            </Tooltip>
          );
        })}
      </>
    );
  }

  return (
    <div>
      {/* 分组标题 - 正常大小写，无 uppercase/tracking */}
      <button
        onClick={() => setOpen(!open)}
        className="sidebar-section-label flex items-center gap-2 w-full px-5 cursor-pointer hover:opacity-80 transition-opacity"
      >
        <span className="flex-1 text-left">{label}</span>
        <ChevronDown
          className={cn(
            "h-3 w-3 transition-transform duration-200",
            !open && "-rotate-90"
          )}
        />
      </button>

      {/* 折叠内容 */}
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] as [number, number, number, number] }}
            className="overflow-hidden"
          >
            {items.map((item, index) => {
              const active = isActive(item.path);
              const Icon = item.icon;
              return (
                <motion.button
                  key={item.path}
                  onClick={() => onNavigate(getNavPath(item.path))}
                  className={cn("sidebar-item flex w-full items-center gap-3 px-4 py-2.5 text-[13px]", active && "active")}
                  initial={{ opacity: 0, x: -8 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{
                    delay: index * 0.03,
                    duration: 0.2,
                    ease: [0.22, 1, 0.36, 1] as [number, number, number, number],
                  }}
                >
                  <Icon className="h-[18px] w-[18px] shrink-0" />
                  <span className="truncate font-medium">{item.label}</span>
                </motion.button>
              );
            })}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();
  const { settings } = useSettingsStore();
  const { currentPersonaId } = useChatStore();
  const collapsed = settings.appearance.sidebarCollapsed;

  const getNavPath = (basePath: string) => {
    const needsPersonaId = [
      "/chat", "/memory", "/diary", "/moments",
      "/achievement", "/calendar", "/sticker",
      "/phone-call", "/bedtime-radio", "/album",
      "/capsule", "/chat-history",
    ];
    if (needsPersonaId.some((p) => basePath.startsWith(p)) && currentPersonaId) {
      return `${basePath}/${currentPersonaId}`;
    }
    if (basePath === "/world") return "/world/default";
    return basePath;
  };

  const isActive = (path: string) =>
    location.pathname === path || (path !== "/" && location.pathname.startsWith(path));

  return (
    <TooltipProvider delayDuration={200}>
      <aside
        className={cn(
          "flex h-full flex-col sidebar-surface relative z-10 transition-all duration-300 ease-out",
          collapsed ? "w-[var(--sidebar-collapsed-width)]" : "w-[var(--sidebar-width)]"
        )}
      >
        {/* ====== Logo 区域 ====== */}
        <div
          className={cn(
            "flex items-center gap-3 px-5 py-4 shrink-0",
            "border-b border-[color-mix(in_srgb,var(--color-border),transparent_15%)]",
            collapsed && "justify-center px-3"
          )}
        >
          {/* Logo 图标 - 纯色背景，不用渐变 */}
          <div
            className="h-9 w-9 rounded-xl flex items-center justify-center shrink-0 overflow-hidden"
            style={{
              background: "var(--color-primary)",
            }}
          >
            <Sparkles className="h-4.5 w-4.5 text-white drop-shadow-sm" />
          </div>

          {/* 应用名称 - 无 tracking-wide */}
          <AnimatePresence mode="wait">
            {!collapsed && (
              <motion.span
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -10 }}
                transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] as [number, number, number, number] }}
                className="text-[15px] font-bold whitespace-nowrap overflow-hidden reveal-up"
                style={{ color: "var(--color-card-foreground)" }}
              >
                星尘
              </motion.span>
            )}
          </AnimatePresence>
        </div>

        {/* ====== 导航区域 ====== */}
        <nav className="flex-1 overflow-y-auto scroll-smooth py-3 px-1.5 stagger-list">
          {/* 常驻导航 - stagger 入场 + reveal-up */}
          {PRIMARY_NAV.map((item, index) => {
            const active = isActive(item.path);
            const Icon = item.icon;

            const button = (
              <motion.button
                key={item.path}
                onClick={() => navigate(getNavPath(item.path))}
                className={cn(
                  "sidebar-item flex w-full items-center gap-3 px-3.5 py-2.5 text-[13px]",
                  collapsed && "justify-center px-0",
                  active && "active"
                )}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{
                  delay: index * 0.04,
                  duration: 0.3,
                  ease: [0.22, 1, 0.36, 1] as [number, number, number, number],
                }}
                whileTap={{ scale: 0.97 }}
              >
                <Icon className="h-[18px] w-[18px] shrink-0" />
                {!collapsed && <span className="truncate font-medium">{item.label}</span>}
              </motion.button>
            );

            if (collapsed) {
              return (
                <Tooltip key={item.path}>
                  <TooltipTrigger asChild>{button}</TooltipTrigger>
                  <TooltipContent
                    side="right"
                    className="surface-panel text-xs font-medium px-3 py-1.5 rounded-lg"
                    sideOffset={8}
                  >
                    {item.label}
                  </TooltipContent>
                </Tooltip>
              );
            }

            return button;
          })}

          {/* 分隔线 + 社交娱乐折叠组 */}
          <div className="sidebar-section-divider my-1" />
          <CollapsibleSection
            label="社交娱乐"
            items={SOCIAL_NAV}
            defaultOpen={SOCIAL_NAV.some((item) => isActive(item.path))}
            collapsed={collapsed}
            isActive={isActive}
            getNavPath={getNavPath}
            onNavigate={navigate}
          />

          {/* 分隔线 + 工具管理折叠组 */}
          <div className="sidebar-section-divider my-1" />
          <CollapsibleSection
            label="工具管理"
            items={TOOLS_NAV}
            defaultOpen={TOOLS_NAV.some((item) => isActive(item.path))}
            collapsed={collapsed}
            isActive={isActive}
            getNavPath={getNavPath}
            onNavigate={navigate}
          />
        </nav>

        {/* ====== 底部设置 ====== */}
        <div
          className="shrink-0 border-t border-[color-mix(in_srgb,var(--color-border),transparent_15%)] py-2.5 px-1.5"
        >
          {/* 桌宠模式入口 — 独立透明悬浮窗 */}
          {isTauri() && (
            <button
              onClick={async () => {
                try {
                  const { invoke } = await import("@tauri-apps/api/core");
                  await invoke("show_overlay", { visible: true });
                } catch (e) {
                  console.error("Failed to open pet mode:", e);
                }
              }}
              className={cn(
                "sidebar-item flex w-full items-center gap-3 px-3.5 py-2.5 text-[13px]",
                collapsed && "justify-center px-0"
              )}
              title="桌宠模式（独立悬浮窗）"
            >
              <Maximize2 className="h-[18px] w-[18px] shrink-0" />
              {!collapsed && <span className="truncate font-medium">桌宠模式</span>}
            </button>
          )}

          <button
            onClick={() => navigate("/settings")}
            className={cn(
              "sidebar-item flex w-full items-center gap-3 px-3.5 py-2.5 text-[13px]",
              collapsed && "justify-center px-0",
              isActive("/settings") && "active"
            )}
          >
            <Settings className="h-[18px] w-[18px] shrink-0" />
            {!collapsed && <span className="truncate font-medium">设置</span>}
          </button>
        </div>

        {/* ====== 中式/茶香主题装饰：右下角角花 ====== */}
        <div className="absolute bottom-6 right-0 pointer-events-none chinese-corner-decor" aria-hidden="true" />
      </aside>
    </TooltipProvider>
  );
}
