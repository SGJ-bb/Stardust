// 星尘 12 套主题完整配色数据 — 与 PC端 themes.css 1:1 同步
const THEMES = [
  // ===== 1. 樱粉 Sakura (默认) =====
  {
    id: 'sakura', name: '樱粉', emoji: '🌸',
    light: {
      bg: '#fef7f7', bg2: '#fdf2f2', card: '#ffffff', cardFg: '#1a1a2e',
      primary: '#ec4899', primaryFg: '#ffffff',
      secondary: '#fce7f3', secondaryFg: '#9d174d',
      muted: '#f5f5f5', mutedFg: '#737373',
      accent: '#fce7f3', accentFg: '#9d174d',
      border: '#f3e8ff', input: '#fce7f3',
      text: '#1a1a2e', text2: '#6b7280',
      glow: 'rgba(236,72,153,0.15)',
      gradient: 'linear-gradient(135deg,#ec4899,#f472b6)',
    },
    dark: {
      bg: '#14080f', bg2: '#1e1020', card: '#261424', cardFg: '#fad0ea',
      primary: '#ec4899', primaryFg: '#fff',
      secondary: '#361830', secondaryFg: '#fbcfe8',
      muted: '#361830', mutedFg: '#c4a0b0',
      accent: '#361830', accentFg: '#fbcfe8',
      border: '#462840', input: '#361830',
      text: '#fad0ea', text2: '#c4a0b0',
      glow: 'rgba(236,72,153,0.20)',
      gradient: 'linear-gradient(135deg,#ec4899,#db2777)',
    }
  },
  // ===== 2. 桃粉 Peach =====
  {
    id: 'peach', name: '桃粉', emoji: '🍑',
    light: {
      bg: '#fff7ed', bg2: '#ffedd5', card: '#ffffff', cardFg: '#1c1917',
      primary: '#f97316', primaryFg: '#ffffff',
      secondary: '#ffedd5', secondaryFg: '#9a3412',
      muted: '#f5f5f4', mutedFg: '#78716c',
      accent: '#ffedd5', accentFg: '#9a3412',
      border: '#fed7aa', input: '#ffedd5',
      text: '#1c1917', text2: '#78716c',
      glow: 'rgba(249,115,22,0.15)',
      gradient: 'linear-gradient(135deg,#f97316,#fb923c)',
    },
    dark: {
      bg: '#141008', bg2: '#20180e', card: '#281e14', cardFg: '#fed8b0',
      primary: '#f97316', primaryFg: '#fff',
      secondary: '#382818', secondaryFg: '#fed7aa',
      muted: '#382818', mutedFg: '#c4a890',
      accent: '#382818', accentFg: '#fed7aa',
      border: '#483828', input: '#382818',
      text: '#fed8b0', text2: '#c4a890',
      glow: 'rgba(249,115,22,0.20)',
      gradient: 'linear-gradient(135deg,#f97316,#ea580c)',
    }
  },
  // ===== 3. 紫罗兰 Violet =====
  {
    id: 'violet', name: '紫罗兰', emoji: '💜',
    light: {
      bg: '#faf5ff', bg2: '#f3e8ff', card: '#ffffff', cardFg: '#1e1b4b',
      primary: '#8b5cf6', primaryFg: '#ffffff',
      secondary: '#f3e8ff', secondaryFg: '#5b21b6',
      muted: '#f5f5f5', mutedFg: '#737373',
      accent: '#f3e8ff', accentFg: '#5b21b6',
      border: '#e9d5ff', input: '#f3e8ff',
      text: '#1e1b4b', text2: '#737373',
      glow: 'rgba(139,92,246,0.15)',
      gradient: 'linear-gradient(135deg,#8b5cf6,#a78bfa)',
    },
    dark: {
      bg: '#0d0818', bg2: '#18102e', card: '#20143a', cardFg: '#ddd0f5',
      primary: '#8b5cf6', primaryFg: '#fff',
      secondary: '#2a184a', secondaryFg: '#d8b4fe',
      muted: '#2a184a', mutedFg: '#a090c4',
      accent: '#2a184a', accentFg: '#d8b4fe',
      border: '#3a285c', input: '#2a184a',
      text: '#ddd0f5', text2: '#a090c4',
      glow: 'rgba(139,92,246,0.20)',
      gradient: 'linear-gradient(135deg,#8b5cf6,#7c3aed)',
    }
  },
  // ===== 4. 海蓝 Ocean =====
  {
    id: 'ocean', name: '海蓝', emoji: '🌊',
    light: {
      bg: '#eff6ff', bg2: '#dbeafe', card: '#ffffff', cardFg: '#1e3a5f',
      primary: '#3b82f6', primaryFg: '#ffffff',
      secondary: '#dbeafe', secondaryFg: '#1e40af',
      muted: '#f5f5f5', mutedFg: '#6b7280',
      accent: '#dbeafe', accentFg: '#1e40af',
      border: '#bfdbfe', input: '#dbeafe',
      text: '#1e3a5f', text2: '#6b7280',
      glow: 'rgba(59,130,246,0.15)',
      gradient: 'linear-gradient(135deg,#3b82f6,#60a5fa)',
    },
    dark: {
      bg: '#08101a', bg2: '#101a2e', card: '#14203a', cardFg: '#bcd8f5',
      primary: '#3b82f6', primaryFg: '#fff',
      secondary: '#182a4a', secondaryFg: '#93c5fd',
      muted: '#182a4a', mutedFg: '#7090b0',
      accent: '#182a4a', accentFg: '#93c5fd',
      border: '#283a5c', input: '#182a4a',
      text: '#bcd8f5', text2: '#7090b0',
      glow: 'rgba(59,130,246,0.20)',
      gradient: 'linear-gradient(135deg,#3b82f6,#2563eb)',
    }
  },
  // ===== 5. 翡翠 Emerald =====
  {
    id: 'emerald', name: '翡翠', emoji: '💚',
    light: {
      bg: '#ecfdf5', bg2: '#d1fae5', card: '#ffffff', cardFg: '#064e3b',
      primary: '#10b981', primaryFg: '#ffffff',
      secondary: '#d1fae5', secondaryFg: '#065f46',
      muted: '#f5f5f5', mutedFg: '#6b7280',
      accent: '#d1fae5', accentFg: '#065f46',
      border: '#a7f3d0', input: '#d1fae5',
      text: '#064e3b', text2: '#6b7280',
      glow: 'rgba(16,185,129,0.15)',
      gradient: 'linear-gradient(135deg,#10b981,#34d399)',
    },
    dark: {
      bg: '#081410', bg2: '#102a1e', card: '#143024', cardFg: '#b0f5e0',
      primary: '#10b981', primaryFg: '#fff',
      secondary: '#183a2d', secondaryFg: '#6ee7b7',
      muted: '#183a2d', mutedFg: '#70b4a0',
      accent: '#183a2d', accentFg: '#6ee7b7',
      border: '#284d3d', input: '#183a2d',
      text: '#b0f5e0', text2: '#70b4a0',
      glow: 'rgba(16,185,129,0.20)',
      gradient: 'linear-gradient(135deg,#10b981,#059669)',
    }
  },
  // ===== 6. 日落 Sunset =====
  {
    id: 'sunset', name: '日落', emoji: '🌅',
    light: {
      bg: '#fffbeb', bg2: '#fef3c7', card: '#ffffff', cardFg: '#78350f',
      primary: '#f59e0b', primaryFg: '#78350f',
      secondary: '#fef3c7', secondaryFg: '#92400e',
      muted: '#f5f5f4', mutedFg: '#78716c',
      accent: '#fef3c7', accentFg: '#92400e',
      border: '#fde68a', input: '#fef3c7',
      text: '#78350f', text2: '#78716c',
      glow: 'rgba(245,158,11,0.15)',
      gradient: 'linear-gradient(135deg,#f59e0b,#fbbf24)',
    },
    dark: {
      bg: '#141008', bg2: '#201c10', card: '#282214', cardFg: '#f5e8c0',
      primary: '#f59e0b', primaryFg: '#141008',
      secondary: '#383018', secondaryFg: '#fde68a',
      muted: '#383018', mutedFg: '#b4a480',
      accent: '#383018', accentFg: '#fde68a',
      border: '#484028', input: '#383018',
      text: '#f5e8c0', text2: '#b4a480',
      glow: 'rgba(245,158,11,0.20)',
      gradient: 'linear-gradient(135deg,#f59e0b,#d97706)',
    }
  },
  // ===== 7. 玫瑰金 RoseGold =====
  {
    id: 'rosegold', name: '玫瑰金', emoji: '🌹',
    light: {
      bg: '#fdf2f8', bg2: '#fce7f3', card: '#ffffff', cardFg: '#831843',
      primary: '#e11d48', primaryFg: '#ffffff',
      secondary: '#fce7f3', secondaryFg: '#9d174d',
      muted: '#f5f5f5', mutedFg: '#737373',
      accent: '#fce7f3', accentFg: '#9d174d',
      border: '#fbcfe8', input: '#fce7f3',
      text: '#831843', text2: '#737373',
      glow: 'rgba(225,29,72,0.15)',
      gradient: 'linear-gradient(135deg,#e11d48,#fb7185)',
    },
    dark: {
      bg: '#140810', bg2: '#201018', card: '#281420', cardFg: '#f5d0e8',
      primary: '#e11d48', primaryFg: '#fff',
      secondary: '#381830', secondaryFg: '#fbcfe8',
      muted: '#381830', mutedFg: '#b4a0b0',
      accent: '#381830', accentFg: '#fbcfe8',
      border: '#482840', input: '#381818',
      text: '#f5d0e8', text2: '#b4a0b0',
      glow: 'rgba(225,29,72,0.20)',
      gradient: 'linear-gradient(135deg,#e11d48,#be123c)',
    }
  },
  // ===== 8. 薄荷 Mint =====
  {
    id: 'mint', name: '薄荷', emoji: '🍃',
    light: {
      bg: '#f0fdfa', bg2: '#ccfbf1', card: '#ffffff', cardFg: '#134e4a',
      primary: '#14b8a6', primaryFg: '#ffffff',
      secondary: '#ccfbf1', secondaryFg: '#115e59',
      muted: '#f5f5f5', mutedFg: '#6b7280',
      accent: '#ccfbf1', accentFg: '#115e59',
      border: '#99f6e4', input: '#ccfbf1',
      text: '#134e4a', text2: '#6b7280',
      glow: 'rgba(20,184,166,0.15)',
      gradient: 'linear-gradient(135deg,#14b8a6,#2dd4bf)',
    },
    dark: {
      bg: '#081412', bg2: '#102618', card: '#142824', cardFg: '#b0f5ee',
      primary: '#14b8a6', primaryFg: '#fff',
      secondary: '#183830', secondaryFg: '#5eead4',
      muted: '#183830', mutedFg: '#70aeaa',
      accent: '#183830', accentFg: '#5eead4',
      border: '#284840', input: '#183830',
      text: '#b0f5ee', text2: '#70aeaa',
      glow: 'rgba(20,184,166,0.20)',
      gradient: 'linear-gradient(135deg,#14b8a6,#0d9488)',
    }
  },
  // ===== 9. 暗夜 Midnight (仅暗色) =====
  {
    id: 'midnight', name: '暗夜', emoji: '🌙',
    light: null, // 暗夜主题无浅色模式
    dark: {
      bg: '#0c0c18', bg2: '#16162e', card: '#1c1c32', cardFg: '#dce0f0',
      primary: '#6366f1', primaryFg: '#ffffff',
      secondary: '#25254a', secondaryFg: '#a5b4fc',
      muted: '#25254a', mutedFg: '#8896b8',
      accent: '#25254a', accentFg: '#a5b4fc',
      border: '#33355c', input: '#25254a',
      text: '#dce0f0', text2: '#8896b8',
      glow: 'rgba(99,102,241,0.18)',
      gradient: 'linear-gradient(135deg,#6366f1,#818cf8)',
    }
  },
  // ===== 10. 茶香 Tea =====
  {
    id: 'tea', name: '茶香', emoji: '🍵',
    light: {
      bg: '#faf8f3', bg2: '#f2ede0', card: '#ffffff', cardFg: '#2d2a1e',
      primary: '#6b8e5a', primaryFg: '#ffffff',
      secondary: '#e8e0cc', secondaryFg: '#4a5a3a',
      muted: '#f0ece0', mutedFg: '#6b6655',
      accent: '#e8e0cc', accentFg: '#4a5a3a',
      border: '#d4cbb0', input: '#e8e0cc',
      text: '#2d2a1e', text2: '#6b6655',
      glow: 'rgba(107,142,90,0.12)',
      gradient: 'linear-gradient(135deg,#6b8e5a,#8ba86a)',
    },
    dark: {
      bg: '#12100c', bg2: '#1c1a14', card: '#222016', cardFg: '#e8e0cc',
      primary: '#7da36a', primaryFg: '#fff',
      secondary: '#2e2c1e', secondaryFg: '#b8c89a',
      muted: '#2e2c1e', mutedFg: '#999078',
      accent: '#2e2c1e', accentFg: '#b8c89a',
      border: '#3e3c28', input: '#2e2c1e',
      text: '#e8e0cc', text2: '#999078',
      glow: 'rgba(125,163,106,0.18)',
      gradient: 'linear-gradient(135deg,#7da36a,#6b8e5a)',
    }
  },
  // ===== 11. 赛博朋克 Cyberpunk (仅暗色) =====
  {
    id: 'cyberpunk', name: '赛博朋克', emoji: '🤖',
    light: null,
    dark: {
      bg: '#050510', bg2: '#0a0a1e', card: '#0e0e24', cardFg: '#d0e0ff',
      primary: '#00f0ff', primaryFg: '#000000',
      secondary: '#151530', secondaryFg: '#80d0ff',
      muted: '#151530', mutedFg: '#6080a0',
      accent: '#151530', accentFg: '#80d0ff',
      border: '#202048', input: '#151530',
      text: '#d0e0ff', text2: '#6080a0',
      glow: 'rgba(0,240,255,0.25)',
      gradient: 'linear-gradient(135deg,#00f0ff,#bf00ff)',
    }
  },
  // ===== 12. 华夏风韵 Chinese =====
  {
    id: 'chinese', name: '华夏风韵', emoji: '🏮',
    light: {
      bg: '#faf6f0', bg2: '#f2ebe0', card: '#ffffff', cardFg: '#2a1a10',
      primary: '#c53d43', primaryFg: '#ffffff',
      secondary: '#f0e4d8', secondaryFg: '#8b4513',
      muted: '#f0ece4', mutedFg: '#7a6050',
      accent: '#f0e4d8', accentFg: '#8b4513',
      border: '#d4c4ac', input: '#f0e4d8',
      text: '#2a1a10', text2: '#7a6050',
      glow: 'rgba(197,61,67,0.12)',
      gradient: 'linear-gradient(135deg,#c53d43,#d4765a)',
    },
    dark: {
      bg: '#100c08', bg2: '#1a1410', card: '#241c14', cardFg: '#f0e4d0',
      primary: '#e0454a', primaryFg: '#fff',
      secondary: '#2e2418', secondaryFg: '#d4a05a',
      muted: '#2e2418', mutedFg: '#a08070',
      accent: '#2e2418', accentFg: '#d4a05a',
      border: '#3e3428', input: '#2e2418',
      text: '#f0e4d0', text2: '#a08070',
      glow: 'rgba(224,69,74,0.18)',
      gradient: 'linear-gradient(135deg,#e0454a,#c53d43)',
    }
  }
];

// 渲染全部主题
function renderThemes() {
  const grid = document.getElementById('themeGrid');
  grid.innerHTML = THEMES.map(t => renderThemeCard(t)).join('');
}

function renderThemeCard(theme) {
  const hasLight = theme.light !== null;
  const hasDark = true;

  return `
  <div class="theme-card" data-theme="${theme.id}">
    <div class="theme-header" style="background:${theme.dark.bg};color:${theme.dark.text}">
      <div class="theme-name">
        <span class="theme-dot" style="background:${theme.dark.gradient}"></span>
        <span>${theme.emoji} ${theme.name}</span>
      </div>
      <span class="theme-badge" style="background:${theme.dark.primary}20;color:${theme.dark.primary}">
        ${hasLight && hasDark ? 'L+D' : hasDark ? 'D Only' : 'L Only'}
      </span>
    </div>
    <div class="modes-row">
      ${hasLight ? renderModePanel(theme, 'light') : ''}
      ${hasDark ? renderModePanel(theme, 'dark') : ''}
    </div>
  </div>`;
}

function renderModePanel(theme, mode) {
  const c = theme[mode];
  const isDark = mode === 'dark';

  return `
  <div class="mode-panel" style="background:${c.bg};color:${c.text}">
    <div class="mode-label">
      ${isDark ? '🌙 Dark' : '☀️ Light'}
    </div>

    <!-- 色板 -->
    <div class="color-swatches">
      <div class="swatch" style="background:${c.primary};color:${c.primaryFg}"><span>Primary</span></div>
      <div class="swatch" style="background:${c.card};color:${c.cardFg}"><span>Card</span></div>
      <div class="swatch" style="background:${c.secondary};color:${c.secondaryFg}"><span>Secondary</span></div>
      <div class="swatch" style="background:${c.muted};color:${c.mutedFg}"><span>Muted</span></div>
      <div class="swatch" style="background:${c.accent};color:${c.accentFg}"><span>Accent</span></div>
      <div class="swatch" style="background:${c.border}"><span>Border</span></div>
      <div class="swatch" style="background:${c.input}"><span>Input</span></div>
      <div class="swatch" style="background:${c.glow}"><span>Glow</span></div>
    </div>

    <!-- 模拟聊天 UI -->
    <div class="mock-ui" style="background:${c.card};border:1px solid ${c.border}">
      <div class="mock-toolbar" style="background:${c.secondary}">
        <div class="mock-avatar" style="background:${c.gradient}"></div>
        <div class="mock-input-flex" style="background:${isDark ? c.border : c.muted}"></div>
        <div class="mock-btn" style="background:${c.primary}"></div>
      </div>

      <div class="mock-chat-row" style="justify-content:flex-end">
        <div class="mock-bubble user" style="background:${c.gradient};color:${c.primaryFg}">
          你好呀，今天心情怎么样？
        </div>
      </div>
      <div class="mock-chat-row">
        <div class="mock-avatar" style="width:22px;height:22px;background:${c.gradient};border-radius:50%;flex-shrink:0;margin-top:4px"></div>
        <div class="mock-bubble ai" style="background:${c.secondary};color:${c.secondaryFg}">
          我很好哦！有什么我可以帮你的吗？✨
        </div>
      </div>

      <div class="mock-input-bar" style="background:${isDark ? c.muted : c.border}">
        <div class="mock-input-flex" style="background:${c.input}"></div>
        <div class="mock-btn" style="background:${c.primary}"></div>
      </div>
    </div>

    <!-- 模拟卡片 -->
    <div class="mock-cards">
      <div class="mock-card" style="background:${c.card};border:1px solid ${c.border}">
        <div class="mock-card-line" style="background:${c.primary}"></div>
        <div class="mock-card-line" style="background:${c.muted}"></div>
        <div class="mock-card-line" style="background:${c.border}"></div>
      </div>
      <div class="mock-card" style="background:${c.card};border:1px solid ${c.border}">
        <div class="mock-card-line" style="background:${c.gradient}"></div>
        <div class="mock-card-line" style="background:${c.muted}"></div>
        <div class="mock-card-line" style="background:${c.border}"></div>
      </div>
    </div>
  </div>`;
}

// 初始化
renderThemes();
