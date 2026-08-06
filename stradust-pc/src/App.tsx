import { BrowserRouter, Routes, Route, useLocation } from "react-router";
import { AppLayout } from "@/components/layout/AppLayout";
import { ThemeProvider } from "@/components/common/ThemeProvider";
import { ErrorBoundary } from "@/components/common/ErrorBoundary";
import { lazy, Suspense, useState, useEffect } from "react";
import { LoadingSpinner } from "@/components/common/LoadingSpinner";
import { EntranceAnimation } from "@/components/effects/EntranceAnimation";
import { ActivationDialog } from "@/components/activation/ActivationDialog";
import { useThemeStore } from "@/stores/useThemeStore";
import { useActivationStore } from "@/stores/useActivationStore";

// 页面组件懒加载
const HomePage = lazy(() => import("@/pages/Home/HomePage").then((m) => ({ default: m.HomePage })));
const ChatPage = lazy(() => import("@/pages/Chat/ChatPage").then((m) => ({ default: m.ChatPage })));
const GroupChatListPage = lazy(() => import("@/pages/GroupChat/GroupChatListPage").then((m) => ({ default: m.GroupChatListPage })));
const GroupChatPage = lazy(() => import("@/pages/GroupChat/GroupChatPage").then((m) => ({ default: m.GroupChatPage })));
const PersonaDetailPage = lazy(() => import("@/pages/Persona/PersonaDetailPage").then((m) => ({ default: m.PersonaDetailPage })));
const PersonaEditorPage = lazy(() => import("@/pages/Persona/PersonaEditorPage").then((m) => ({ default: m.PersonaEditorPage })));
const MemoryPage = lazy(() => import("@/pages/Memory/MemoryPage").then((m) => ({ default: m.MemoryPage })));
const MemoryPoolPage = lazy(() => import("@/pages/Memory/MemoryPoolPage").then((m) => ({ default: m.MemoryPoolPage })));
const DiaryPage = lazy(() => import("@/pages/Diary/DiaryPage").then((m) => ({ default: m.DiaryPage })));
const MomentsPage = lazy(() => import("@/pages/Moments/MomentsPage").then((m) => ({ default: m.MomentsPage })));
const VirtualWorldPage = lazy(() => import("@/pages/VirtualWorld/VirtualWorldPage").then((m) => ({ default: m.VirtualWorldPage })));
const AchievementPage = lazy(() => import("@/pages/Achievement/AchievementPage").then((m) => ({ default: m.AchievementPage })));
const CalendarPage = lazy(() => import("@/pages/Calendar/CalendarPage").then((m) => ({ default: m.CalendarPage })));
const StickerPage = lazy(() => import("@/pages/Sticker/StickerPage").then((m) => ({ default: m.StickerPage })));
const SkinShopPage = lazy(() => import("@/pages/SkinShop/SkinShopPage").then((m) => ({ default: m.SkinShopPage })));
const ModelManagerPage = lazy(() => import("@/pages/ModelManager/ModelManagerPage").then((m) => ({ default: m.ModelManagerPage })));
const SettingsPage = lazy(() => import("@/pages/Settings/SettingsPage").then((m) => ({ default: m.SettingsPage })));
const AlarmPage = lazy(() => import("@/pages/Alarm/AlarmPage").then((m) => ({ default: m.AlarmPage })));
const PhoneCallPage = lazy(() => import("@/pages/PhoneCall/PhoneCallPage").then((m) => ({ default: m.PhoneCallPage })));
const BedtimeRadioPage = lazy(() => import("@/pages/BedtimeRadio/BedtimeRadioPage").then((m) => ({ default: m.BedtimeRadioPage })));
const MemorialAlbumPage = lazy(() => import("@/pages/MemorialAlbum/MemorialAlbumPage").then((m) => ({ default: m.MemorialAlbumPage })));
const TimeCapsulePage = lazy(() => import("@/pages/TimeCapsule/TimeCapsulePage").then((m) => ({ default: m.TimeCapsulePage })));
const ChatHistoryPage = lazy(() => import("@/pages/ChatHistory/ChatHistoryPage").then((m) => ({ default: m.ChatHistoryPage })));
const ActivationPage = lazy(() => import("@/pages/Activation/ActivationPage").then((m) => ({ default: m.ActivationPage })));
const LocalModelPage = lazy(() => import("@/pages/LocalModel/LocalModelPage").then((m) => ({ default: m.LocalModelPage })));
const OverlayPet = lazy(() => import("@/pages/Overlay/OverlayPet").then((m) => ({ default: m.OverlayPet })));
const AgentWorkspace = lazy(() => import("@/components/agent/AgentWorkspace").then((m) => ({ default: m.AgentWorkspace })));

/**
 * 根组件
 * 配置路由和全局Provider
 */
function RouteDetector() {
  const location = useLocation();

  useEffect(() => {
    const root = document.getElementById("root");
    if (root) {
      // overlay-pet 路由时设置标记，CSS 会将背景透明化
      if (location.pathname === "/overlay-pet") {
        root.setAttribute("data-route", "overlay-pet");
      } else {
        root.removeAttribute("data-route");
      }
    }
  }, [location.pathname]);

  return null;
}

function App() {
  const [showingEntrance, setShowingEntrance] = useState(true);
  const { currentTheme } = useThemeStore();
  const { isPremiumUnlocked, isFirstActivated } = useActivationStore();

  /** 用户点击入场动画后进入主界面 */
  const handleEntranceDismiss = () => {
    setShowingEntrance(false);
  };

  // 未解锁高级功能时，跳过入场动画直接进入主界面
  const shouldShowEntrance = isPremiumUnlocked && showingEntrance;

  return (
    <ErrorBoundary>
      <ThemeProvider>
        {/* 激活对话框 — 未完成首次激活时显示 */}
        {!isFirstActivated && <ActivationDialog />}

        {/* 入场动画覆盖层 — 仅高级功能解锁且未关闭时显示 */}
        {shouldShowEntrance && (
          <EntranceAnimation
            theme={currentTheme}
            onDismiss={handleEntranceDismiss}
          />
        )}

        <BrowserRouter>
          <RouteDetector />
          <Suspense fallback={<LoadingSpinner />}>
            <Routes>
            {/* 主布局路由 */}
            <Route element={<AppLayout />}>
              {/* 主页 - 角色列表 */}
              <Route path="/" element={<HomePage />} />

              {/* 单聊 */}
              <Route path="/chat/:personaId" element={<ChatPage />} />
              <Route path="/chat" element={<ChatPage />} />

              {/* 群聊 */}
              <Route path="/group-chat" element={<GroupChatListPage />} />
              <Route path="/group-chat/:groupId" element={<GroupChatPage />} />

              {/* 角色详情/编辑 */}
              <Route path="/persona/:id" element={<PersonaDetailPage />} />
              <Route path="/persona/:id/edit" element={<PersonaEditorPage />} />
              <Route path="/persona/new" element={<PersonaEditorPage />} />

              {/* 记忆 */}
              <Route path="/memory/:personaId" element={<MemoryPage />} />
              <Route path="/memory/:personaId/pool" element={<MemoryPoolPage />} />
              <Route path="/memory" element={<MemoryPage />} />

              {/* 日记 */}
              <Route path="/diary/:personaId" element={<DiaryPage />} />
              <Route path="/diary" element={<DiaryPage />} />

              {/* 朋友圈 */}
              <Route path="/moments/:personaId" element={<MomentsPage />} />
              <Route path="/moments" element={<MomentsPage />} />

              {/* 虚拟世界 */}
              <Route path="/world/:worldId" element={<VirtualWorldPage />} />
              <Route path="/world" element={<VirtualWorldPage />} />

              {/* 成就 */}
              <Route path="/achievement/:personaId" element={<AchievementPage />} />
              <Route path="/achievement" element={<AchievementPage />} />

              {/* 日历 */}
              <Route path="/calendar/:personaId" element={<CalendarPage />} />
              <Route path="/calendar" element={<CalendarPage />} />

              {/* 表情包 */}
              <Route path="/sticker/:personaId" element={<StickerPage />} />
              <Route path="/sticker" element={<StickerPage />} />

              {/* 皮肤商店 */}
              <Route path="/skin-shop" element={<SkinShopPage />} />

              {/* 模型管理 */}
              <Route path="/model-manager" element={<ModelManagerPage />} />

              {/* 设置 */}
              <Route path="/settings" element={<SettingsPage />} />

              {/* 闹钟 */}
              <Route path="/alarm" element={<AlarmPage />} />

              {/* 语音通话 */}
              <Route path="/phone-call/:personaId" element={<PhoneCallPage />} />
              <Route path="/phone-call" element={<PhoneCallPage />} />

              {/* 星尘电台 */}
              <Route path="/bedtime-radio/:personaId" element={<BedtimeRadioPage />} />
              <Route path="/bedtime-radio" element={<BedtimeRadioPage />} />

              {/* 纪念相册 */}
              <Route path="/album/:personaId" element={<MemorialAlbumPage />} />
              <Route path="/album" element={<MemorialAlbumPage />} />

              {/* 时光胶囊 */}
              <Route path="/capsule/:personaId" element={<TimeCapsulePage />} />
              <Route path="/capsule" element={<TimeCapsulePage />} />

              {/* 聊天记录 */}
              <Route path="/chat-history/:personaId" element={<ChatHistoryPage />} />
              <Route path="/chat-history" element={<ChatHistoryPage />} />

              {/* 激活码 */}
              <Route path="/activation" element={<ActivationPage />} />

              {/* 本地模型 */}
              <Route path="/local-model" element={<LocalModelPage />} />

              {/* 悬浮窗桌宠 */}
              <Route path="/overlay-pet" element={<OverlayPet />} />

              {/* Agent 智能体工作台 */}
              <Route path="/agent" element={<AgentWorkspace />} />
            </Route>
            </Routes>
          </Suspense>
        </BrowserRouter>
      </ThemeProvider>
    </ErrorBoundary>
  );
}

export default App;
