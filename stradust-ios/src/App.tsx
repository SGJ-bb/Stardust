import { useState } from "react";
import ChatPage from "./pages/ChatPage";
import SettingsPage from "./pages/SettingsPage";
import ProfilePage from "./pages/ProfilePage";
import DiaryPage from "./pages/DiaryPage";
import AchievementPage from "./pages/AchievementPage";
import MemoryPage from "./pages/MemoryPage";
import MomentsPage from "./pages/MomentsPage";
import MorePage from "./pages/MorePage";
import TimeCapsulePage from "./pages/TimeCapsulePage";
import GroupChatPage from "./pages/GroupChatPage";
import SkinShopPage from "./pages/SkinShopPage";
import StickerPage from "./pages/StickerPage";
import WakeUpPage from "./pages/WakeUpPage";
import ChatHistoryPage from "./pages/ChatHistoryPage";
import MemorialAlbumPage from "./pages/MemorialAlbumPage";
import CalendarPage from "./pages/CalendarPage";

type Page = "chat" | "settings" | "profile" | "diary" | "achievement" | "memory" | "moments" | "more" | "capsule" | "groupchat" | "skinshop" | "sticker" | "wakeup" | "chathistory" | "memorial" | "calendar";

function App() {
  const [currentPage, setCurrentPage] = useState<Page>("chat");

  // 子页面直接返回
  if (currentPage === "settings") {
    return <div className="page-enter"><SettingsPage onBack={() => setCurrentPage("chat")} /></div>;
  }
  if (currentPage === "profile") {
    return <div className="page-enter"><ProfilePage onBack={() => setCurrentPage("chat")} /></div>;
  }
  if (currentPage === "diary") {
    return <div className="page-enter"><DiaryPage onBack={() => setCurrentPage("chat")} /></div>;
  }
  if (currentPage === "achievement") {
    return <div className="page-enter"><AchievementPage onBack={() => setCurrentPage("chat")} /></div>;
  }
  if (currentPage === "memory") {
    return <div className="page-enter"><MemoryPage onBack={() => setCurrentPage("chat")} /></div>;
  }
  if (currentPage === "moments") {
    return <div className="page-enter"><MomentsPage onBack={() => setCurrentPage("chat")} /></div>;
  }
  if (currentPage === "capsule") {
    return <div className="page-enter"><TimeCapsulePage onBack={() => setCurrentPage("more")} /></div>;
  }
  if (currentPage === "groupchat") {
    return <div className="page-enter"><GroupChatPage onBack={() => setCurrentPage("more")} /></div>;
  }
  if (currentPage === "skinshop") {
    return <div className="page-enter"><SkinShopPage onBack={() => setCurrentPage("more")} /></div>;
  }
  if (currentPage === "sticker") {
    return <div className="page-enter"><StickerPage onBack={() => setCurrentPage("more")} /></div>;
  }
  if (currentPage === "wakeup") {
    return <div className="page-enter"><WakeUpPage onBack={() => setCurrentPage("more")} /></div>;
  }
  if (currentPage === "chathistory") {
    return <div className="page-enter"><ChatHistoryPage onBack={() => setCurrentPage("chat")} /></div>;
  }
  if (currentPage === "memorial") {
    return <div className="page-enter"><MemorialAlbumPage onBack={() => setCurrentPage("more")} /></div>;
  }
  if (currentPage === "calendar") {
    return <div className="page-enter"><CalendarPage onBack={() => setCurrentPage("chat")} /></div>;
  }
  if (currentPage === "more") {
    return <div className="page-enter"><MorePage onBack={() => setCurrentPage("chat")} onNavigate={(page) => {
      const pageMap: Record<string, Page> = {
        moments: "moments",
        settings: "settings",
        profile: "profile",
        capsule: "capsule",
        groupchat: "groupchat",
        skinshop: "skinshop",
        sticker: "sticker",
        alarm: "wakeup",
        chathistory: "chathistory",
        memorial: "memorial",
        calendar: "calendar",
      };
      const target = pageMap[page];
      if (target) setCurrentPage(target);
    }} /></div>;
  }

  // 主聊天页面 + 底部导航
  return (
    <div style={appContainerStyle}>
      <div style={{ flex: 1, overflow: "hidden" }}>
        <ChatPage
          onOpenSettings={() => setCurrentPage("settings")}
          onOpenProfile={() => setCurrentPage("profile")}
          onOpenChatHistory={() => setCurrentPage("chathistory")}
        />
      </div>
      <div style={tabBarStyle}>
        <TabBtn icon="💬" label="聊天" active={currentPage === "chat"} onClick={() => setCurrentPage("chat")} />
        <TabBtn icon="📖" label="日记" active={false} onClick={() => setCurrentPage("diary")} />
        <TabBtn icon="🌟" label="成就" active={false} onClick={() => setCurrentPage("achievement")} />
        <TabBtn icon="🧠" label="记忆" active={false} onClick={() => setCurrentPage("memory")} />
        <TabBtn icon="📱" label="更多" active={false} onClick={() => setCurrentPage("more")} />
      </div>
    </div>
  );
}

function TabBtn({ icon, label, active, onClick }: { icon: string; label: string; active: boolean; onClick: () => void }) {
  const [pressed, setPressed] = useState(false);
  const [hovered, setHovered] = useState(false);

  const handleClick = () => {
    if (active) return;
    onClick();
  };

  return (
    <button
      onClick={handleClick}
      onMouseDown={() => setPressed(true)}
      onMouseUp={() => setPressed(false)}
      onMouseLeave={() => { setPressed(false); setHovered(false); }}
      onMouseEnter={() => setHovered(true)}
      style={{
        flex: 1,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: "var(--space-1)",
        padding: "var(--space-2) 0",
        background: "transparent",
        color: active ? "var(--accent-primary)" : (hovered ? "var(--text-secondary)" : "var(--text-muted)"),
        fontSize: "var(--text-xs)",
        fontWeight: active ? "var(--weight-semibold)" : "var(--weight-regular)",
        border: "none",
        cursor: "pointer",
        position: "relative",
        transform: pressed ? "scale(0.96)" : "scale(1)",
        transition: "color var(--duration-fast) var(--ease-out-quart), transform var(--duration-fast) var(--ease-out-quart)",
      }}
    >
      <span style={{
        fontSize: "22px",
        transition: "transform var(--duration-fast) var(--ease-out-quart)",
        ...(active ? {
          filter: "drop-shadow(0 0 8px rgba(139, 108, 255, 0.5))",
        } : {}),
      }}>{icon}</span>
      <span>{label}</span>
      {active && (
        <div style={{
          position: "absolute",
          top: 0,
          left: "50%",
          transform: "translateX(-50%)",
          width: 20,
          height: 2,
          borderRadius: "var(--radius-full)",
          background: "var(--gradient-primary)",
          boxShadow: "0 0 8px rgba(139, 108, 255, 0.4)",
          transition: "all var(--duration-normal) var(--ease-out-expo)",
        }} />
      )}
    </button>
  );
}

const appContainerStyle: React.CSSProperties = {
  height: "100%",
  display: "flex",
  flexDirection: "column",
  background: "var(--bg-primary)",
};

const tabBarStyle: React.CSSProperties = {
  display: "flex",
  background: "rgba(20, 18, 31, 0.85)",
  backdropFilter: "blur(20px)",
  WebkitBackdropFilter: "blur(20px)",
  borderTop: "1px solid var(--border-color)",
  paddingBottom: "env(safe-area-inset-bottom, 0px)",
  boxShadow: "0 -4px 16px rgba(0, 0, 0, 0.2)",
};

export default App;
