/**
 * 主题入场动画 — 每个颜色主题对应的唯美景色场景 + 粒子动画
 *
 * 使用方式：
 *   <EntranceAnimation theme="sakura" onDismiss={() => setShowing(false)} />
 *
 * 12 个主题 × 完整唯美场景：
 *   sakura     → 富士山樱花场景（樱吹雪）
 *   peach      → 桃花林日出（蜜桃梦境）
 *   violet     → 普罗旺斯薰衣草田（薰衣之梦）
 *   ocean      → 海底世界（深海物语）
 *   emerald    → 魔法森林（翠林萤火）
 *   sunset     → 大峡谷日落（落日余晖）
 *   rosegold   → 香山红叶秋景（玫瑰物语）⭐
 *   mint       → 清晨草地（薄荷晨露）
 *   midnight   → 银河星空 + 城市灯火（星河长夜）⭐
 *   tea        → 中式庭院品茗（茶香悠远）
 *   cyberpunk  → 霓虹城市夜景（赛博幻境）⭐
 *   chinese    → 中国山水画卷（华夏风韵）⭐
 *
 * 渲染顺序：clearRect → drawImage(bgCache) → drawDynamicScenery() → drawParticles() → drawTitle() → drawClickHint()
 * 性能优化：静态背景(sky+scenery)缓存到离屏Canvas，每帧仅拷贝缓存+绘制动态部分
 */

import { useEffect, useRef, useCallback } from "react";

// ═══════════════════════════════════════════
// 主题配置：每个主题的配色 + 动画参数
// ═══════════════════════════════════════════

interface ThemeConfig {
  name: string;           // 中文名
  nameEn: string;         // 英文名
  colors: {
    primary: string;      // 主色
    secondary: string;    // 辅色
    accent: string;       // 点缀色
    bg: string;           // 背景色
    glow: string;         // 发光色
  };
  /** 粒子类型: petal/butterfly/wave/firefly/ray/dew/star/steam/raindrop/lantern/ink/circuit/mapleleaf */
  type: "petal" | "butterfly" | "wave" | "firefly" | "ray" | "dew"
       | "star" | "steam" | "raindrop" | "lantern" | "ink" | "circuit" | "mapleleaf";
  particleCount: number;  // 粒子数量
}

const THEME_CONFIGS: Record<string, ThemeConfig> = {
  sakura: {
    name: "樱吹雪",
    nameEn: "Sakura",
    colors: { primary: "#ec4899", secondary: "#f472b6", accent: "#fce7f3", bg: "#1a0a12", glow: "rgba(236,72,153,0.4)" },
    type: "petal", particleCount: 55,
  },
  peach: {
    name: "蜜桃梦境",
    nameEn: "Peach",
    colors: { primary: "#f97316", secondary: "#fb923c", accent: "#fff7ed", bg: "#140a04", glow: "rgba(249,115,22,0.4)" },
    type: "petal", particleCount: 35,
  },
  violet: {
    name: "薰衣之梦",
    nameEn: "Violet",
    colors: { primary: "#8b5cf6", secondary: "#a78bfa", accent: "#ede9fe", bg: "#0e0a1a", glow: "rgba(139,92,246,0.4)" },
    type: "butterfly", particleCount: 20,
  },
  ocean: {
    name: "深海物语",
    nameEn: "Ocean",
    colors: { primary: "#3b82f6", secondary: "#60a5fa", accent: "#eff6ff", bg: "#040e1a", glow: "rgba(59,130,246,0.4)" },
    type: "wave", particleCount: 30,
  },
  emerald: {
    name: "翠林萤火",
    nameEn: "Emerald",
    colors: { primary: "#10b981", secondary: "#34d399", accent: "#ecfdf5", bg: "#040f0a", glow: "rgba(16,185,129,0.5)" },
    type: "firefly", particleCount: 35,
  },
  sunset: {
    name: "落日余晖",
    nameEn: "Sunset",
    colors: { primary: "#f59e0b", secondary: "#fbbf24", accent: "#fffbeb", bg: "#120a02", glow: "rgba(245,158,11,0.4)" },
    type: "ray", particleCount: 20,
  },
  rosegold: {
    name: "玫瑰物语",
    nameEn: "Rose Gold",
    colors: { primary: "#e11d48", secondary: "#fb7185", accent: "#fff1f2", bg: "#140408", glow: "rgba(225,29,72,0.4)" },
    type: "mapleleaf", particleCount: 70,
  },
  mint: {
    name: "薄荷晨露",
    nameEn: "Mint",
    colors: { primary: "#14b8a6", secondary: "#2dd4bf", accent: "#f0fdfa", bg: "#030f0e", glow: "rgba(20,184,166,0.4)" },
    type: "dew", particleCount: 30,
  },
  midnight: {
    name: "星河长夜",
    nameEn: "Midnight",
    colors: { primary: "#6366f1", secondary: "#818cf8", accent: "#eef2ff", bg: "#07071f", glow: "rgba(99,102,241,0.4)" },
    type: "star", particleCount: 100,
  },
  tea: {
    name: "茶香悠远",
    nameEn: "Tea",
    colors: { primary: "#6b8e5a", secondary: "#8ba86a", accent: "#f6f7ed", bg: "#0f100a", glow: "rgba(107,142,90,0.3)" },
    type: "steam", particleCount: 25,
  },
  cyberpunk: {
    name: "赛博幻境",
    nameEn: "Cyberpunk",
    colors: { primary: "#00f0ff", secondary: "#bf00ff", accent: "#0a0a0a", bg: "#000000", glow: "rgba(0,240,255,0.5)" },
    type: "circuit", particleCount: 60,
  },
  chinese: {
    name: "华夏风韵",
    nameEn: "Chinese",
    colors: { primary: "#c53d43", secondary: "#d4765a", accent: "#c49a3a", bg: "#0e0808", glow: "rgba(197,61,67,0.4)" },
    type: "lantern", particleCount: 15,
  },
};

// ═══════════════════════════════════════════
// 粒子接口
// ═══════════════════════════════════════════

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  rotation: number;
  rotationSpeed: number;
  opacity: number;
  life: number;
  maxLife: number;
  // 类型特有属性
  phase?: number;          // butterfly/flashing 用
  trail?: Array<{x:number; y:number}>; // firefly/circuit 尾迹
  twinklePhase?: number;   // star 闪烁相位
  wobble?: number;         // steam/petal 摆动
  scale?: number;          // 缩放（用于生长/消散）
}

interface ShootingStar {
  x: number; y: number; vx: number; vy: number;
  life: number; maxLife: number; length: number;
}

interface InkRipple {
  x: number; y: number; radius: number; maxRadius: number;
  opacity: number; color: string;
}

/** 动态景色状态接口 */
interface DynamicSceneryState {
  /** sakura: 云朵位置 */
  clouds?: Array<{x: number; y: number; rx: number; ry: number; speed: number}>;
  /** sakura: 小鸟位置 */
  birds?: Array<{x: number; y: number; speed: number; wingPhase: number}>;
  /** ocean: 光柱相位 */
  lightBeams?: Array<{x: number; width: number; phase: number}>;
  /** ocean: 海草相位 */
  seaweedPhases?: number[];
  /** ocean: 鱼群 */
  fishSchool?: Array<{x: number; y: number; size: number; speed: number}>;
  /** emerald: 月亮光晕相位 */
  moonGlowPhase?: number;
  /** emerald: 萤火虫聚集点 */
  fireflyClusters?: Array<{x: number; y: number; phase: number}>;
  /** sunset: 鹰的位置 */
  eaglePos?: {x: number; y: number; angle: number; orbitPhase: number};
  /** rosegold: 瀑布水流相位 */
  waterfallPhase?: number;
  /** rosegold: 红叶旋涡 */
  leafVortexes?: Array<{x: number; y: number; rotation: number; size: number}>;
  /** rosegold: 炊烟 */
  smokePlumes?: Array<{x: number; y: number; particles: Array<{x: number; y: number; opacity: number}>}>;
  /** midnight: 流星雨 */
  meteorShower?: Array<{x: number; y: number; vx: number; vy: number; life: number; maxLife: number}>;
  /** midnight: 灯塔光束角度 */
  lighthouseAngle?: number;
  /** tea: 热气粒子 */
  steamParticles?: Array<{x: number; y: number; size: number; opacity: number; life: number}>;
  /** cyberpunk: 全息广告牌 */
  holograms?: Array<{x: number; y: number; w: number; h: number; color: string; phase: number}>;
  /** cyberpunk: 飞行载具 */
  vehicles?: Array<{x: number; y: number; speed: number; lane: number}>;
  /** cyberpunk: 扫描线Y坐标 */
  scanlineY?: number;
  /** chinese: 更多飞鸟 */
  extraBirds?: Array<{x: number; y: number; speed: number; wingPhase: number; size: number}>;
}

// ═══════════════════════════════════════════
// Props
// ═══════════════════════════════════════════

interface EntranceAnimationProps {
  theme: string;               // data-theme 值
  onDismiss: () => void;       // 点击进入主界面回调
  duration?: number;           // 自动消失时间(ms)，0=不自动消失，默认 0
}

export function EntranceAnimation({ theme, onDismiss, duration = 0 }: EntranceAnimationProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const animRef = useRef<number>(0);
  const stateRef = useRef<{
    particles: Particle[];
    shootingStars: ShootingStar[];
    inkRipples: InkRipple[];
    time: number;
    width: number;
    height: number;
    fadingOut: boolean;
    fadeProgress: number;
    ready: boolean;
    bgCanvas: HTMLCanvasElement | null;  // ★ 离屏背景缓存
    dynamicState: DynamicSceneryState;   // ★ 动态景色状态
  }>({
    particles: [],
    shootingStars: [],
    inkRipples: [],
    time: 0,
    width: 0,
    height: 0,
    fadingOut: false,
    fadeProgress: 0,
    ready: false,
    bgCanvas: null,
    dynamicState: {},
  });

  const config = THEME_CONFIGS[theme] ?? THEME_CONFIGS.midnight;

  // ═════ 点击任意位置进入 ═════
  const handleDismiss = useCallback(() => {
    const state = stateRef.current;
    if (state.fadingOut) return;
    state.fadingOut = true;
    // 0.6s 淡出后调用 onDismiss
    setTimeout(onDismiss, 600);
  }, [onDismiss]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const state = stateRef.current;
    let lastTime = performance.now();

    // 初始化尺寸
    const initSize = () => {
      const rect = container.getBoundingClientRect();
      state.width = rect.width;
      state.height = rect.height;
      const dpr = Math.min(window.devicePixelRatio || 1, 2); // 限制 DPR 减少像素
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      canvas.style.width = `${rect.width}px`;
      canvas.style.height = `${rect.height}px`;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    initSize();

    // ═════★ 创建离屏背景缓存 Canvas ═════
    const buildBackgroundCache = () => {
      const { width, height } = state;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);

      const bgCanvas = document.createElement("canvas");
      const bgCtx = bgCanvas.getContext("2d")!;
      bgCanvas.width = width * dpr;
      bgCanvas.height = height * dpr;
      bgCtx.setTransform(dpr, 0, 0, dpr, 0, 0);

      // 绘制天空渐变到缓存
      drawSkyGradient(bgCtx, theme, width, height, config.colors);

      // 绘制静态景色到缓存（time=0 表示静态初始状态）
      drawScenery(bgCtx, theme, width, height, 0, config.colors);

      state.bgCanvas = bgCanvas;
    };

    // 首次构建背景缓存
    buildBackgroundCache();

    // 初始化动态景色状态
    initDynamicSceneryState(state, theme, state.width, state.height);

    // ═════ 根据主题类型生成初始粒子 ═════
    const spawnParticles = () => {
      const { width, height } = state;
      const { type, particleCount, colors } = config;
      const particles: Particle[] = [];

      switch (type) {
        case "petal": {
          for (let i = 0; i < particleCount; i++) {
            const s = 8 + Math.random() * 14;
            particles.push({
              x: Math.random() * width * 1.2 - width * 0.1,
              y: -Math.random() * height * 0.5 - 50,
              vx: -15 + Math.random() * 30,
              vy: 30 + Math.random() * 60,
              size: s,
              rotation: Math.random() * Math.PI * 2,
              rotationSpeed: (-1 + Math.random() * 2) * 2,
              opacity: 0.4 + Math.random() * 0.5,
              life: 3 + Math.random() * 5,
              maxLife: 8,
              wobble: Math.random() * Math.PI * 2,
              scale: 0.3 + Math.random() * 0.7,
            });
          }
          break;
        }

        case "mapleleaf": {
          // 枫叶形状粒子（rosegold专用）
          for (let i = 0; i < particleCount; i++) {
            const s = 10 + Math.random() * 16;
            particles.push({
              x: Math.random() * width * 1.3 - width * 0.15,
              y: -Math.random() * height * 0.6 - 60,
              vx: -20 + Math.random() * 40,
              vy: 25 + Math.random() * 55,
              size: s,
              rotation: Math.random() * Math.PI * 2,
              rotationSpeed: (-1.5 + Math.random() * 3) * 2.5,
              opacity: 0.5 + Math.random() * 0.45,
              life: 3 + Math.random() * 6,
              maxLife: 8,
              wobble: Math.random() * Math.PI * 2,
              scale: 0.4 + Math.random() * 0.6,
            });
          }
          break;
        }

        case "butterfly": {
          for (let i = 0; i < particleCount; i++) {
            particles.push({
              x: Math.random() * width,
              y: Math.random() * height,
              vx: -20 + Math.random() * 40,
              vy: -15 + Math.random() * 30,
              size: 10 + Math.random() * 12,
              rotation: 0,
              rotationSpeed: 0,
              opacity: 0.5 + Math.random() * 0.4,
              life: 9999,
              maxLife: 9999,
              phase: Math.random() * Math.PI * 2, // 翅膀扇动相位
              wobble: Math.random() * 100, // 漫游周期
              scale: 0.6 + Math.random() * 0.4,
            });
          }
          break;
        }

        case "wave": {
          // 波浪线（底部）+ 气泡
          for (let i = 0; i < 8; i++) {
            particles.push({
              x: (i / 8) * width * 1.2 - width * 0.1,
              y: height * 0.72 + Math.random() * 20,
              vx: 8 + Math.random() * 12,
              vy: 0,
              size: 3 + Math.random() * 4,
              rotation: 0,
              rotationSpeed: 0,
              opacity: 0.2 + Math.random() * 0.25,
              life: 9999,
              maxLife: 9999,
              phase: Math.random() * Math.PI * 2,
              wobble: 0.5 + Math.random() * 1.5,
              scale: 1,
            });
          }
          // 气泡
          for (let i = 0; i < particleCount - 8; i++) {
            const r = 2 + Math.random() * 8;
            particles.push({
              x: Math.random() * width,
              y: height + Math.random() * 50,
              vx: (-10 + Math.random() * 20) * 0.3,
              vy: -(40 + Math.random() * 80),
              size: r,
              rotation: 0,
              rotationSpeed: 0,
              opacity: 0.2 + Math.random() * 0.4,
              life: 2 + Math.random() * 4,
              maxLife: 5,
              wobble: Math.random() * Math.PI * 2,
              scale: 1,
            });
          }
          break;
        }

        case "firefly": {
          for (let i = 0; i < particleCount; i++) {
            particles.push({
              x: Math.random() * width,
              y: Math.random() * height,
              vx: -15 + Math.random() * 30,
              vy: -10 + Math.random() * 20,
              size: 2 + Math.random() * 4,
              rotation: 0,
              rotationSpeed: 0,
              opacity: 0,
              life: 9999,
              maxLife: 9999,
              phase: Math.random() * Math.PI * 2, // 闪烁相位
              trail: [], // 发光尾迹
              wobble: 2000 + Math.random() * 3000, // 方向改变周期
              scale: 1,
            });
          }
          break;
        }

        case "ray": {
          // 光束（从中心放射）
          for (let i = 0; i < particleCount; i++) {
            const angle = (i / particleCount) * Math.PI * 0.8 + Math.PI * 0.1;
            particles.push({
              x: width * 0.5,
              y: height * 0.9,
              vx: Math.cos(angle) * (50 + Math.random() * 100),
              vy: Math.sin(angle) * (50 + Math.random() * 100) - 80,
              size: 1.5 + Math.random() * 3,
              rotation: angle,
              rotationSpeed: 0,
              opacity: 0.05 + Math.random() * 0.15,
              life: 9999,
              maxLife: 9999,
              phase: Math.random() * Math.PI * 2, // 呼吸相位
              wobble: 0,
              scale: 1,
            });
          }
          // 金色尘埃
          for (let i = 0; i < 40; i++) {
            particles.push({
              x: Math.random() * width,
              y: height + Math.random() * 100,
              vx: (-8 + Math.random() * 16) * 0.5,
              vy: -(15 + Math.random() * 30),
              size: 1 + Math.random() * 2.5,
              rotation: 0,
              rotationSpeed: 0,
              opacity: 0.3 + Math.random() * 0.5,
              life: 4 + Math.random() * 6,
              maxLife: 8,
              wobble: Math.random() * Math.PI * 2,
              scale: 1,
            });
          }
          break;
        }

        case "dew": {
          for (let i = 0; i < particleCount; i++) {
            const gx = 50 + Math.random() * (width - 100);
            const gy = height * 0.55 + Math.random() * (height * 0.38);
            particles.push({
              x: gx,
              y: gy,
              vx: 0,
              vy: 0,
              size: 3 + Math.random() * 8,
              rotation: 0,
              rotationSpeed: 0,
              opacity: 0,
              life: 0,
              maxLife: 3 + Math.random() * 4,
              phase: Math.random() * 2, // 延迟出生
              wobble: 0,
              scale: 0,
            });
          }
          break;
        }

        case "star": {
          // 背景星星
          for (let i = 0; i < particleCount; i++) {
            particles.push({
              x: Math.random() * width,
              y: Math.random() * height * 0.75,
              vx: 0,
              vy: 0,
              size: 0.5 + Math.random() * 2.5,
              rotation: 0,
              rotationSpeed: 0,
              opacity: Math.random(),
              life: 9999,
              maxLife: 9999,
              twinklePhase: Math.random() * Math.PI * 2,
              wobble: 500 + Math.random() * 2000,
              scale: 1,
            });
          }
          break;
        }

        case "steam": {
          // 茶雾
          for (let i = 0; i < particleCount; i++) {
            particles.push({
              x: width * (0.3 + Math.random() * 0.4),
              y: height * (0.65 + Math.random() * 0.15),
              vx: -12 + Math.random() * 24,
              vy: -(20 + Math.random() * 30),
              size: 15 + Math.random() * 30,
              rotation: 0,
              rotationSpeed: (-0.2 + Math.random() * 0.4),
              opacity: 0,
              life: 0,
              maxLife: 2.5 + Math.random() * 2,
              phase: Math.random() * 2, // 延迟出生
              wobble: Math.random() * Math.PI * 2,
              scale: 0.3 + Math.random() * 0.5,
            });
          }
          // 茶叶
          for (let i = 0; i < 15; i++) {
            particles.push({
              x: Math.random() * width * 1.2 - width * 0.1,
              y: -20 - Math.random() * height * 0.3,
              vx: -20 + Math.random() * 40,
              vy: 25 + Math.random() * 45,
              size: 6 + Math.random() * 8,
              rotation: Math.random() * Math.PI * 2,
              rotationSpeed: (-1 + Math.random() * 2) * 1.5,
              opacity: 0.5 + Math.random() * 0.35,
              life: 4 + Math.random() * 6,
              maxLife: 8,
              wobble: Math.random() * Math.PI * 2,
              scale: 0.6 + Math.random() * 0.4,
            });
          }
          break;
        }

        case "circuit": {
          for (let i = 0; i < particleCount; i++) {
            const isVertical = Math.random() > 0.5;
            particles.push({
              x: isVertical ? Math.random() * width : (Math.random() > 0.5 ? -10 : width + 10),
              y: isVertical ? (Math.random() > 0.5 ? -10 : height + 10) : Math.random() * height,
              vx: isVertical ? (-2 + Math.random() * 4) : (Math.random() > 0.5 ? 80 + Math.random() * 120 : -(80 + Math.random() * 120)),
              vy: isVertical ? (Math.random() > 0.5 ? 80 + Math.random() * 120 : -(80 + Math.random() * 120)) : (-2 + Math.random() * 4),
              size: 1 + Math.random() * 2.5,
              rotation: 0,
              rotationSpeed: 0,
              opacity: 0.4 + Math.random() * 0.5,
              life: 1.5 + Math.random() * 3,
              maxLife: 4,
              trail: [],
              wobble: 0,
              scale: 1,
            });
          }
          break;
        }

        case "lantern": {
          for (let i = 0; i < particleCount; i++) {
            particles.push({
              x: width * (0.1 + (i / particleCount) * 0.8) + (Math.random() - 0.5) * 80,
              y: height + 50 + Math.random() * 100,
              vx: (-3 + Math.random() * 6) * 0.5,
              vy: -(15 + Math.random() * 20),
              size: 18 + Math.random() * 14,
              rotation: 0,
              rotationSpeed: (-0.3 + Math.random() * 0.6),
              opacity: 0,
              life: 0,
              maxLife: 5 + Math.random() * 4,
              phase: Math.random() * 1.5, // 延迟亮起
              wobble: Math.sin(i * 0.8) * 15, // 左右摆动幅度
              scale: 0.5,
            });
          }
          break;
        }

        default:
          break;
      }

      state.particles = particles;
    };

    spawnParticles();
    state.ready = true;

    // ═════ 渲染循环（优化版：使用离屏缓存） ═════
    const render = (now: number) => {
      const dt = Math.min((now - lastTime) / 1000, 0.033);
      lastTime = now;
      state.time += dt;

      const { width, height, fadingOut, fadeProgress, particles, shootingStars, inkRipples, bgCanvas } = state;
      const { type, colors } = config;
      const ctx2 = ctx;

      // ====== 1. 清屏 ======
      ctx2.clearRect(0, 0, width, height);

      // ====== 2. 直接拷贝缓存的背景（GPU内存复制，极快！）======
      if (bgCanvas) {
        ctx2.drawImage(bgCanvas, 0, 0, width, height);
      }

      // ====== 3. 绘制动态景色元素（云朵、鸟、萤火虫、瀑布等）======
      drawDynamicScenery(ctx2, theme, state, dt, width, height, colors);

      // 全局透明度（淡出时使用）
      const globalAlpha = fadingOut ? Math.max(0, 1 - fadeProgress) : 1;
      if (fadingOut) {
        state.fadeProgress += dt / 0.6; // 0.6s 淡出
      }
      ctx2.globalAlpha = globalAlpha;

      // ====== 4. 根据类型绘制前景粒子 ======
      switch (type) {
        case "petal":
          drawPetals(ctx2, state, dt, colors);
          break;
        case "mapleleaf":
          drawMapleLeaves(ctx2, state, dt, colors); // ★ 枫叶形状
          break;
        case "butterfly":
          drawButterflies(ctx2, state, dt, colors);
          break;
        case "wave":
          drawWavesAndBubbles(ctx2, state, dt, colors, width, height);
          break;
        case "firefly":
          drawFireflies(ctx2, state, dt, colors, width, height);
          break;
        case "ray":
          drawSunRays(ctx2, state, dt, colors, width, height);
          break;
        case "dew":
          drawDewDrops(ctx2, state, dt, colors, width, height);
          break;
        case "star":
          drawStarrySky(ctx2, state, dt, colors, width, height);
          break;
        case "steam":
          drawTeaScene(ctx2, state, dt, colors, width, height);
          break;
        case "circuit":
          drawCyberpunkRain(ctx2, state, dt, colors, width, height);
          break;
        case "lantern":
          drawChineseLanterns(ctx2, state, dt, colors, width, height);
          break;
      }

      // ====== 5. 绘制标题文字 ======
      drawTitle(ctx2, state, config, width, height);

      // ====== 6. 绘制点击提示 ======
      drawClickHint(ctx2, state.time, width, height);

      ctx2.globalAlpha = 1;
      animRef.current = requestAnimationFrame(render);
    };

    animRef.current = requestAnimationFrame(render);

    // 窗口resize时重建背景缓存
    const handleResize = () => {
      initSize();
      buildBackgroundCache();
      initDynamicSceneryState(state, theme, state.width, state.height);
    };
    window.addEventListener("resize", handleResize);

    // 自动消失定时器
    let autoTimer: ReturnType<typeof setTimeout> | undefined;
    if (duration > 0) {
      autoTimer = setTimeout(handleDismiss, duration);
    }

    return () => {
      cancelAnimationFrame(animRef.current);
      window.removeEventListener("resize", handleResize);
      if (autoTimer) clearTimeout(autoTimer);
    };
  }, [theme, onDismiss, duration, config]);

  return (
    <div
      ref={containerRef}
      className="fixed inset-0 cursor-pointer"
      style={{ zIndex: 99999, background: config.colors.bg }}
      onClick={handleDismiss}
      role="button"
      aria-label="点击进入"
      tabIndex={0}
      onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") handleDismiss(); }}
    >
      <canvas
        ref={canvasRef}
        className="absolute inset-0 w-full h-full"
        style={{ pointerEvents: "none" }}
      />
    </div>
  );
}


// ══════════════════════════════════════════════
// ★★★ 景色系统：天空渐变 + 中远景绘制 ★★★
// ══════════════════════════════════════════════

/**
 * 绘制天空渐变背景 — 每个主题独特的天空色彩
 */
function drawSkyGradient(
  ctx: CanvasRenderingContext2D,
  theme: string,
  w: number,
  h: number,
  c: ThemeConfig["colors"]
) {
  const grad = ctx.createLinearGradient(0, 0, 0, h);

  switch (theme) {
    // ── sakura：深紫→粉→淡粉（富士山樱花天空）──
    case "sakura":
      grad.addColorStop(0, "#1a0a2e");
      grad.addColorStop(0.3, "#4a1942");
      grad.addColorStop(0.6, "#8b3a62");
      grad.addColorStop(0.85, "#d4829a");
      grad.addColorStop(1, "#fce7f3");
      break;

    // ── peach：橙粉色黎明天空 ──
    case "peach":
      grad.addColorStop(0, "#1a0a04");
      grad.addColorStop(0.3, "#4a2010");
      grad.addColorStop(0.55, "#a85a28");
      grad.addColorStop(0.75, "#f59e5a");
      grad.addColorStop(0.92, "#ffd4a8");
      grad.addColorStop(1, "#fff7ed");
      break;

    // ── violet：紫色黄昏天空 ──
    case "violet":
      grad.addColorStop(0, "#0a0618");
      grad.addColorStop(0.3, "#1e1040");
      grad.addColorStop(0.55, "#4a2080");
      grad.addColorStop(0.75, "#7c3aed");
      grad.addColorStop(1, "#c4b5fd");
      break;

    // ── ocean：深海渐变（深蓝→蓝绿→墨蓝）──
    case "ocean":
      grad.addColorStop(0, "#001a33");
      grad.addColorStop(0.25, "#003366");
      grad.addColorStop(0.5, "#005580");
      grad.addColorStop(0.75, "#0077aa");
      grad.addColorStop(1, "#003350");
      break;

    // ── emerald：深绿→墨绿夜空 ──
    case "emerald":
      grad.addColorStop(0, "#021a0f");
      grad.addColorStop(0.3, "#042e18");
      grad.addColorStop(0.55, "#0a4a24");
      grad.addColorStop(0.75, "#146b38");
      grad.addColorStop(1, "#1a3d26");
      break;

    // ── sunset：壮丽日落（深橙→金→橙红→紫）──
    case "sunset":
      grad.addColorStop(0, "#1a0a02");
      grad.addColorStop(0.2, "#4a1804");
      grad.addColorStop(0.4, "#a64a08");
      grad.addColorStop(0.58, "#f59e0b");
      grad.addColorStop(0.75, "#fb923c");
      grad.addColorStop(0.88, "#dc2626");
      grad.addColorStop(1, "#7c2d92");
      break;

    // ── rosegold：秋日暖阳（浅橙→玫红）⭐ ──
    case "rosegold":
      grad.addColorStop(0, "#1a0608");
      grad.addColorStop(0.25, "#4a1020");
      grad.addColorStop(0.5, "#8b2040");
      grad.addColorStop(0.72, "#d44a68");
      grad.addColorStop(0.88, "#f08090");
      grad.addColorStop(1, "#ffe4e8");
      break;

    // ── mint：青绿清晨天空 ──
    case "mint":
      grad.addColorStop(0, "#031a18");
      grad.addColorStop(0.3, "#063832");
      grad.addColorStop(0.55, "#0a6660");
      grad.addColorStop(0.78, "#14b8a6");
      grad.addColorStop(0.92, "#5eead4");
      grad.addColorStop(1, "#ccfbf1");
      break;

    // ── midnight：深邃宇宙（靛蓝→深紫→黑）⭐ ──
    case "midnight":
      grad.addColorStop(0, "#000010");
      grad.addColorStop(0.2, "#070720");
      grad.addColorStop(0.4, "#0f0a3a");
      grad.addColorStop(0.65, "#1a1055");
      grad.addColorStop(0.85, "#2d1b69");
      grad.addColorStop(1, "#12081f");
      break;

    // ── tea：灰绿色水墨风格 ──
    case "tea":
      grad.addColorStop(0, "#0a1008");
      grad.addColorStop(0.35, "#162414");
      grad.addColorStop(0.65, "#2a3d22");
      grad.addColorStop(0.88, "#4a5a3a");
      grad.addColorStop(1, "#6b7a52");
      break;

    // ── cyberpunk：纯黑 ⭐ ──
    case "cyberpunk":
      grad.addColorStop(0, "#000000");
      grad.addColorStop(0.5, "#020208");
      grad.addColorStop(1, "#000005");
      break;

    // ── chinese：宣纸质感底色（米黄/淡灰）⭐ ──
    case "chinese":
      grad.addColorStop(0, "#d4c8a8");
      grad.addColorStop(0.3, "#e8dcc8");
      grad.addColorStop(0.6, "#f0e8d8");
      grad.addColorStop(0.85, "#f5efe0");
      grad.addColorStop(1, "#faf6eb");
      break;

    default:
      grad.addColorStop(0, c.bg);
      grad.addColorStop(1, c.bg + "ee");
      break;
  }

  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, w, h);
}

/**
 * 景色分发器 — 根据主题调用对应的中远景绘制函数
 */
function drawScenery(
  ctx: CanvasRenderingContext2D,
  theme: string,
  w: number,
  h: number,
  time: number,
  c: ThemeConfig["colors"]
) {
  switch (theme) {
    case "sakura":
      drawSakuraMountain(ctx, w, h, time, c);
      break;
    case "peach":
      drawPeachOrchard(ctx, w, h, time, c);
      break;
    case "violet":
      drawLavenderField(ctx, w, h, time, c);
      break;
    case "ocean":
      drawOceanFloor(ctx, w, h, time, c);
      break;
    case "emerald":
      drawForestSilhouette(ctx, w, h, time, c);
      break;
    case "sunset":
      drawCanyonSunset(ctx, w, h, time, c);
      break;
    case "rosegold":
      drawMapleMountain(ctx, w, h, time, c); // ★ 红叶山脉
      break;
    case "mint":
      drawMorningMeadow(ctx, w, h, time, c);
      break;
    case "midnight":
      drawGalaxyCity(ctx, w, h, time, c); // ★ 星空城市
      break;
    case "tea":
      drawTeaGarden(ctx, w, h, time, c);
      break;
    case "cyberpunk":
      drawCyberpunkCity(ctx, w, h, time, c); // ★ 霓虹城市
      break;
    case "chinese":
      drawInkLandscape(ctx, w, h, time, c); // ★ 水墨山水
      break;
  }
}


// ══════════════════════════════════════════════
// ★ 各主题景色绘制函数（静态部分-被缓存）★
// ══════════════════════════════════════════════

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 1. sakura — 富士山樱花场景 【增强版】
// 背景：远山层叠剪影（3层贝塞尔曲线）+ 山顶发光富士山雪顶
// 增强：粉色云朵、樱花林剪影、富士山湖面倒影
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawSakuraMountain(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  _c: ThemeConfig["colors"]
) {
  const baseY = h * 0.62;

  // 第3层（最远，最浅）
  ctx.beginPath();
  ctx.moveTo(0, h);
  ctx.lineTo(0, baseY + h * 0.12);
  ctx.bezierCurveTo(w * 0.15, baseY + h * 0.06, w * 0.3, baseY + h * 0.14, w * 0.4, baseY + h * 0.09);
  ctx.bezierCurveTo(w * 0.55, baseY + h * 0.03, w * 0.7, baseY + h * 0.11, w * 0.85, baseY + h * 0.07);
  ctx.lineTo(w, baseY + h * 0.13);
  ctx.lineTo(w, h);
  ctx.closePath();
  ctx.fillStyle = "rgba(138,80,120,0.35)";
  ctx.fill();

  // 第2层（中间）
  ctx.beginPath();
  ctx.moveTo(0, h);
  ctx.lineTo(0, baseY + h * 0.05);
  ctx.bezierCurveTo(w * 0.12, baseY - h * 0.02, w * 0.25, baseY + h * 0.08, w * 0.38, baseY);
  // 富士山峰形
  ctx.bezierCurveTo(w * 0.46, baseY - h * 0.18, w * 0.5, baseY - h * 0.28, w * 0.52, baseY - h * 0.25);
  ctx.bezierCurveTo(w * 0.56, baseY - h * 0.14, w * 0.65, baseY + h * 0.04, w * 0.78, baseY + h * 0.02);
  ctx.bezierCurveTo(w * 0.88, baseY + h * 0.07, w * 0.95, baseY + h * 0.01, w, baseY + h * 0.06);
  ctx.lineTo(w, h);
  ctx.closePath();
  ctx.fillStyle = "rgba(100,50,90,0.5)";
  ctx.fill();

  // 富士山雪顶效果（微微发光）
  const mtX = w * 0.52;
  const mtTopY = baseY - h * 0.25;
  const snowGrad = ctx.createRadialGradient(mtX, mtTopY, 0, mtX, mtTopY + h * 0.08, h * 0.12);
  snowGrad.addColorStop(0, "rgba(255,240,250,0.7)");
  snowGrad.addColorStop(0.4, "rgba(255,220,235,0.4)");
  snowGrad.addColorStop(1, "rgba(200,160,190,0)");
  ctx.fillStyle = snowGrad;
  ctx.beginPath();
  ctx.moveTo(mtX - w * 0.035, mtTopY + h * 0.06);
  ctx.quadraticCurveTo(mtX, mtTopY - h * 0.01, mtX + w * 0.035, mtTopY + h * 0.06);
  ctx.quadraticCurveTo(mtX, mtTopY + h * 0.04, mtX - w * 0.035, mtTopY + h * 0.06);
  ctx.fill();

  // 第1层（最近，最深）
  ctx.beginPath();
  ctx.moveTo(0, h);
  ctx.lineTo(0, baseY - h * 0.03);
  ctx.bezierCurveTo(w * 0.1, baseY - h * 0.08, w * 0.2, baseY + h * 0.02, w * 0.32, baseY - h * 0.04);
  ctx.bezierCurveTo(w * 0.48, baseY - h * 0.1, w * 0.6, baseY + h * 0.01, w * 0.75, baseY - h * 0.02);
  ctx.bezierCurveTo(w * 0.88, baseY - h * 0.06, w * 0.95, baseY + h * 0.03, w, baseY - h * 0.01);
  ctx.lineTo(w, h);
  ctx.closePath();
  ctx.fillStyle = "rgba(60,25,55,0.6)";
  ctx.fill();

  // ★增强：山脚下淡淡的樱花林剪影（底部小圆点模拟树冠）
  for (let tree = 0; tree < 40; tree++) {
    const tx = (tree * 37 + 13) % w;
    const ty = baseY + h * 0.04 + ((tree * 53) % int(h * 0.28));
    const tr = 3 + (tree % 4) * 2;
    const ta = 0.15 + (tree % 5) * 0.04;

    ctx.beginPath();
    ctx.arc(tx, ty, tr, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(180,100,140,${ta})`;
    ctx.fill();

    // 小三角形树冠
    ctx.beginPath();
    ctx.moveTo(tx, ty - tr * 1.5);
    ctx.lineTo(tx - tr * 0.8, ty);
    ctx.lineTo(tx + tr * 0.8, ty);
    ctx.closePath();
    ctx.fillStyle = `rgba(150,70,120,${ta * 0.8})`;
    ctx.fill();
  }

  // ★增强：富士山倒映在湖面中的效果（山脉下半部分垂直翻转+低透明度）
  const lakeY = h * 0.82;
  ctx.save();
  ctx.globalAlpha = 0.12;
  ctx.translate(0, lakeY * 2);
  ctx.scale(1, -0.4); // 垂直翻转并压缩

  // 重绘简化的富士山轮廓作为倒影
  ctx.beginPath();
  ctx.moveTo(0, h);
  ctx.lineTo(0, baseY + h * 0.05);
  ctx.bezierCurveTo(w * 0.38, baseY, w * 0.46, baseY - h * 0.18, w * 0.52, baseY - h * 0.25);
  ctx.bezierCurveTo(w * 0.56, baseY - h * 0.14, w * 0.78, baseY + h * 0.02, w, baseY + h * 0.06);
  ctx.lineTo(w, h);
  ctx.closePath();
  ctx.fillStyle = "rgba(180,140,170,0.5)";
  ctx.fill();

  // 倒影雪顶
  ctx.fillStyle = "rgba(255,230,245,0.3)";
  ctx.beginPath();
  ctx.moveTo(mtX - w * 0.03, mtTopY + h * 0.06);
  ctx.quadraticCurveTo(mtX, mtTopY, mtX + w * 0.03, mtTopY + h * 0.06);
  ctx.fill();

  ctx.restore();

  // ★增强：湖面水波纹效果
  ctx.strokeStyle = "rgba(220,190,210,0.06)";
  ctx.lineWidth = 0.5;
  for (let wave = 0; wave < 6; wave++) {
    const wy = lakeY + wave * h * 0.028;
    ctx.beginPath();
    ctx.moveTo(0, wy);
    for (let wx = 0; wx <= w; wx += 15) {
      ctx.lineTo(wx, wy + Math.sin(wx * 0.02 + wave * 0.8) * 2);
    }
    ctx.stroke();
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 2. peach — 桃花林日出 【增强版】
// 背景：柔和丘陵起伏（2层圆弧形山丘）+ 远处太阳光晕
// 增强：暖色光束、桃树剪影、草地纹理、花粉微粒
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawPeachOrchard(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  _c: ThemeConfig["colors"]
) {
  const horizonY = h * 0.65;

  // 远处太阳光晕（圆形径向渐变）
  const sunX = w * 0.7;
  const sunY = h * 0.42;
  const sunGrad = ctx.createRadialGradient(sunX, sunY, 0, sunX, sunY, h * 0.3);
  sunGrad.addColorStop(0, "rgba(255,230,180,0.5)");
  sunGrad.addColorStop(0.25, "rgba(255,180,100,0.3)");
  sunGrad.addColorStop(0.55, "rgba(255,130,60,0.12)");
  sunGrad.addColorStop(1, "rgba(255,100,40,0)");
  ctx.fillStyle = sunGrad;
  ctx.fillRect(0, 0, w, h);

  // 太阳本体
  ctx.beginPath();
  ctx.arc(sunX, sunY, h * 0.06, 0, Math.PI * 2);
  const sunBody = ctx.createRadialGradient(sunX, sunY, 0, sunX, sunY, h * 0.06);
  sunBody.addColorStop(0, "rgba(255,250,220,0.95)");
  sunBody.addColorStop(0.5, "rgba(255,200,120,0.7)");
  sunBody.addColorStop(1, "rgba(255,150,60,0.2)");
  ctx.fillStyle = sunBody;
  ctx.fill();

  // ★增强：天空中的暖色光束（从太阳位置放射的淡橙色射线）
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  for (let ray = 0; ray < 8; ray++) {
    const angle = -Math.PI * 0.4 + (ray / 7) * Math.PI * 0.8;
    const rayLen = h * (0.25 + Math.random() * 0.2);
    const endX = sunX + Math.cos(angle) * rayLen;
    const endY = sunY + Math.sin(angle) * rayLen;

    const rg = ctx.createLinearGradient(sunX, sunY, endX, endY);
    rg.addColorStop(0, "rgba(255,200,100,0.08)");
    rg.addColorStop(1, "rgba(255,150,60,0)");

    ctx.beginPath();
    ctx.moveTo(sunX, sunY);
    ctx.lineTo(endX - 3, endY);
    ctx.lineTo(endX + 3, endY);
    ctx.closePath();
    ctx.fillStyle = rg;
    ctx.fill();
  }
  ctx.restore();
  ctx.globalCompositeOperation = "source-over";

  // 远处丘陵（第2层，浅色）
  ctx.beginPath();
  ctx.moveTo(0, h);
  ctx.lineTo(0, horizonY + h * 0.06);
  ctx.arcTo(w * 0.2, horizonY - h * 0.04, w * 0.4, horizonY + h * 0.05, w * 0.25);
  ctx.arcTo(w * 0.55, horizonY - h * 0.07, w * 0.75, horizonY + h * 0.03, w * 0.3);
  ctx.arcTo(w * 0.9, horizonY - h * 0.02, w, horizonY + h * 0.06, w * 0.2);
  ctx.lineTo(w, h);
  ctx.closePath();
  ctx.fillStyle = "rgba(180,100,60,0.3)";
  ctx.fill();

  // 近处丘陵（第1层，深色）
  ctx.beginPath();
  ctx.moveTo(0, h);
  ctx.lineTo(0, horizonY - h * 0.01);
  ctx.arcTo(w * 0.15, horizonY - h * 0.08, w * 0.35, horizonY + h * 0.02, w * 0.3);
  ctx.arcTo(w * 0.6, horizonY - h * 0.05, w * 0.85, horizonY, w * 0.35);
  ctx.arcTo(w, horizonY - h * 0.03, w, horizonY + h * 0.04, w * 0.15);
  ctx.lineTo(w, h);
  ctx.closePath();
  ctx.fillStyle = "rgba(140,70,40,0.45)";
  ctx.fill();

  // ★增强：丘陵上散落的桃树剪影（带圆形树冠的小树）
  const peachTrees = [
    { x: w * 0.12, y: horizonY + h * 0.02, size: h * 0.04 },
    { x: w * 0.28, y: horizonY + h * 0.01, size: h * 0.055 },
    { x: w * 0.48, y: horizonY + h * 0.03, size: h * 0.035 },
    { x: w * 0.68, y: horizonY, size: h * 0.048 },
    { x: w * 0.85, y: horizonY + h * 0.02, size: h * 0.04 },
  ];
  for (const tree of peachTrees) {
    // 树干
    ctx.fillStyle = "rgba(100,55,30,0.5)";
    ctx.fillRect(tree.x - 1.5, tree.y, 3, tree.size * 0.6);
    // 树冠（圆形）
    ctx.beginPath();
    ctx.arc(tree.x, tree.y - tree.size * 0.2, tree.size, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(200,120,80,0.25)";
    ctx.fill();
    // 内层小圆
    ctx.beginPath();
    ctx.arc(tree.x, tree.y - tree.size * 0.15, tree.size * 0.6, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(220,140,90,0.2)";
    ctx.fill();
  }

  // ★增强：地面上的草地纹理线条（底部绿色细线）
  ctx.strokeStyle = "rgba(80,120,50,0.15)";
  ctx.lineWidth = 0.6;
  for (let g = 0; g < Math.floor(w / 8); g++) {
    const gx = g * 8 + (g % 3) * 2;
    const gh = 5 + (g % 5) * 3;
    ctx.beginPath();
    ctx.moveTo(gx, h);
    ctx.quadraticCurveTo(gx + (g % 2 ? 2 : -2), h - gh * 0.6, gx + (g % 2 ? 3 : -3), h - gh);
    ctx.stroke();
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 3. violet — 普罗旺斯薰衣草田 【增强版】
// 背景：远处绿色小山丘 + 底部薰衣草田垄线条
// 增强：紫色晚霞云彩、小木栅栏、风车剪影
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawLavenderField(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  _c: ThemeConfig["colors"]
) {
  const fieldBase = h * 0.6;

  // ★增强：天空中的紫色晚霞云彩（水平长条状渐变色块）
  const cloudPositions = [
    { y: h * 0.12, alpha: 0.12, color1: "rgba(139,92,246,0.15)", color2: "rgba(167,89,232,0)" },
    { y: h * 0.22, alpha: 0.1, color1: "rgba(124,58,237,0.12)", color2: "rgba(109,40,217,0)" },
    { y: h * 0.32, alpha: 0.08, color1: "rgba(147,79,202,0.1)", color2: "rgba(91,33,182,0)" },
  ];
  for (const cloud of cloudPositions) {
    const cg = ctx.createLinearGradient(0, cloud.y - h * 0.04, 0, cloud.y + h * 0.04);
    cg.addColorStop(0, cloud.color2);
    cg.addColorStop(0.5, cloud.color1);
    cg.addColorStop(1, cloud.color2);
    ctx.fillStyle = cg;
    ctx.fillRect(0, cloud.y - h * 0.04, w, h * 0.08);
  }

  // 远处绿色小山丘
  ctx.beginPath();
  ctx.moveTo(0, h);
  ctx.lineTo(0, fieldBase - h * 0.05);
  ctx.bezierCurveTo(w * 0.15, fieldBase - h * 0.12, w * 0.3, fieldBase - h * 0.03, w * 0.5, fieldBase - h * 0.08);
  ctx.bezierCurveTo(w * 0.7, fieldBase - h * 0.13, w * 0.85, fieldBase - h * 0.04, w, fieldBase - h * 0.06);
  ctx.lineTo(w, h);
  ctx.closePath();
  ctx.fillStyle = "rgba(60,90,50,0.35)";
  ctx.fill();

  // 薰衣草田垄（多层波浪线，紫色深浅交替）
  const lavenderColors = [
    "rgba(139,92,246,0.25)",
    "rgba(124,58,237,0.3)",
    "rgba(167,89,232,0.22)",
    "rgba(109,40,217,0.28)",
    "rgba(147,79,202,0.2)",
    "rgba(91,33,182,0.26)",
  ];

  for (let row = 0; row < lavenderColors.length; row++) {
    const rowY = fieldBase + h * 0.04 + row * h * 0.055;
    const waveAmp = 3 + row * 1.5;
    const freq = 0.012 + row * 0.002;
    const phaseOff = row * 0.8;

    ctx.beginPath();
    ctx.moveTo(0, rowY);
    for (let x = 0; x <= w; x += 6) {
      const wy = rowY + Math.sin(x * freq + phaseOff) * waveAmp + Math.sin(x * freq * 2.3 + phaseOff * 1.5) * (waveAmp * 0.4);
      ctx.lineTo(x, wy);
    }
    ctx.lineTo(w, rowY + h * 0.045);
    ctx.lineTo(0, rowY + h * 0.045);
    ctx.closePath();
    ctx.fillStyle = lavenderColors[row];
    ctx.fill();
  }

  // ★增强：田垄间的小木栅栏（竖线+横线交叉）
  const fenceY = fieldBase + h * 0.22;
  ctx.strokeStyle = "rgba(120,90,60,0.3)";
  ctx.lineWidth = 1;
  // 横梁
  ctx.beginPath();
  ctx.moveTo(0, fenceY);
  ctx.lineTo(w, fenceY);
  ctx.moveTo(0, fenceY + 6);
  ctx.lineTo(w, fenceY + 6);
  ctx.stroke();
  // 竖桩
  for (let post = 0; post < w; post += 25) {
    ctx.beginPath();
    ctx.moveTo(post, fenceY - 4);
    ctx.lineTo(post, fenceY + 12);
    ctx.stroke();
  }

  // ★增强：远处风车剪影（十字形旋转结构）
  const windmillX = w * 0.82;
  const windmillY = fieldBase - h * 0.02;
  const wmSize = h * 0.06;
  // 风车塔身
  ctx.fillStyle = "rgba(80,70,55,0.3)";
  ctx.fillRect(windmillX - 3, windmillY, 6, wmSize);
  // 风车叶片
  ctx.strokeStyle = "rgba(100,85,65,0.25)";
  ctx.lineWidth = 1.5;
  for (let blade = 0; blade < 4; blade++) {
    const angle = (blade / 4) * Math.PI * 2;
    ctx.beginPath();
    ctx.moveTo(windmillX, windmillY);
    ctx.lineTo(windmillX + Math.cos(angle) * wmSize * 0.8, windmillY + Math.sin(angle) * wmSize * 0.8);
    ctx.stroke();
  }
  // 风车中心
  ctx.beginPath();
  ctx.arc(windmillX, windmillY, 3, 0, Math.PI * 2);
  ctx.fillStyle = "rgba(90,75,55,0.35)";
  ctx.fill();
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 4. ocean — 海底世界 【增强版】
// 背景：水面光斑效果 + 海底沙地曲线 + 珊瑚礁石
// 增强：光柱、海草摆动、鱼群、气泡轨迹
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawOceanFloor(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  _c: ThemeConfig["colors"]
) {
  // 水面光斑（顶部动态椭圆）
  for (let i = 0; i < 5; i++) {
    const cx = w * (0.15 + i * 0.18);
    const cy = h * 0.05;
    const rx = w * (0.08 + (i % 3) * 0.015);
    const ry = h * (0.025 + (i % 2) * 0.008);

    ctx.save();
    ctx.translate(cx, cy);
    ctx.rotate(i * 0.5);
    ctx.beginPath();
    ctx.ellipse(0, 0, rx, ry, 0, 0, Math.PI * 2);
    const causticGrad = ctx.createRadialGradient(0, 0, 0, 0, 0, rx);
    causticGrad.addColorStop(0, "rgba(120,200,255,0.2)");
    causticGrad.addColorStop(0.6, "rgba(80,160,240,0.08)");
    causticGrad.addColorStop(1, "rgba(60,140,220,0)");
    ctx.fillStyle = causticGrad;
    ctx.fill();
    ctx.restore();
  }

  // ★增强：从水面射下的光柱（白色半透明宽条）
  const beamPositions = [w * 0.15, w * 0.4, w * 0.65, w * 0.88];
  for (const bx of beamPositions) {
    const bg = ctx.createLinearGradient(bx, 0, bx, h * 0.78);
    bg.addColorStop(0, "rgba(150,210,255,0.06)");
    bg.addColorStop(0.5, "rgba(100,180,240,0.03)");
    bg.addColorStop(1, "rgba(60,140,220,0)");
    ctx.fillStyle = bg;
    ctx.fillRect(bx - w * 0.03, 0, w * 0.06, h * 0.78);
  }

  // 海底沙地曲线
  const sandY = h * 0.78;
  ctx.beginPath();
  ctx.moveTo(0, h);
  ctx.lineTo(0, sandY);
  for (let x = 0; x <= w; x += 12) {
    const sy = sandY + Math.sin(x * 0.008) * 8 + Math.sin(x * 0.018 + 1) * 4;
    ctx.lineTo(x, sy);
  }
  ctx.lineTo(w, h);
  ctx.closePath();
  ctx.fillStyle = "rgba(40,60,50,0.4)";
  ctx.fill();

  // ★增强：海底的海草/水草（曲线，将在动态函数中摆动）
  const seaweedBases = [
    { x: w * 0.1, count: 5 },
    { x: w * 0.3, count: 4 },
    { x: w * 0.55, count: 6 },
    { x: w * 0.8, count: 4 },
  ];
  for (const sw of seaweedBases) {
    for (let s = 0; s < sw.count; s++) {
      const sx = sw.x + s * 8 - (sw.count * 4);
      const sh = h * (0.06 + (s % 4) * 0.025);
      ctx.strokeStyle = `rgba(40,140,80,${0.2 + (s % 3) * 0.05})`;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(sx, sandY);
      ctx.quadraticCurveTo(sx + 5, sandY - sh * 0.5, sx + (s % 2 ? 3 : -3), sandY - sh);
      ctx.stroke();
    }
  }

  // 珊瑚礁石（几块不规则形状）
  const corals = [
    { x: w * 0.12, y: h * 0.82, sw: w * 0.08, sh: h * 0.12, col: "rgba(220,100,120,0.35)" },
    { x: w * 0.35, y: h * 0.84, sw: w * 0.06, sh: h * 0.09, col: "rgba(100,180,160,0.3)" },
    { x: w * 0.68, y: h * 0.8, sw: w * 0.1, sh: h * 0.14, col: "rgba(180,120,80,0.3)" },
    { x: w * 0.88, y: h * 0.83, sw: w * 0.07, sh: h * 0.1, col: "rgba(140,100,180,0.28)" },
  ];

  for (const coral of corals) {
    ctx.beginPath();
    ctx.moveTo(coral.x - coral.sw * 0.5, coral.y + coral.sh);
    ctx.quadraticCurveTo(coral.x - coral.sw * 0.3, coral.y - coral.sh * 0.3, coral.x, coral.y - coral.sh * 0.6);
    ctx.quadraticCurveTo(coral.x + coral.sw * 0.35, coral.y - coral.sh * 0.25, coral.x + coral.sw * 0.5, coral.y + coral.sh * 0.8);
    ctx.quadraticCurveTo(coral.x + coral.sw * 0.2, coral.y + coral.sh * 0.5, coral.x - coral.sw * 0.5, coral.y + coral.sh);
    ctx.closePath();
    ctx.fillStyle = coral.col;
    ctx.fill();
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 5. emerald — 魔法森林 【增强版】
// 背景：密集树木剪影 + 远处萤火虫光晕团
// 增强：月亮、薄雾层、萤火虫聚集点、蜘蛛网
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawForestSilhouette(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  _c: ThemeConfig["colors"]
) {
  const treeLineY = h * 0.55;

  // ★增强：右上角月亮（大圆形，淡黄色发光，环形光晕）
  const moonX = w * 0.85;
  const moonY = h * 0.12;
  const moonR = h * 0.045;
  // 月亮外层光晕（多层）
  for (let mg = 3; mg >= 0; mg--) {
    const mgr = moonR * (1 + mg * 1.5);
    const mga = ctx.createRadialGradient(moonX, moonY, moonR * 0.5, moonX, moonY, mgr);
    mga.addColorStop(0, `rgba(230,240,180,${0.08 - mg * 0.015})`);
    mga.addColorStop(1, "rgba(200,220,150,0)");
    ctx.fillStyle = mga;
    ctx.beginPath();
    ctx.arc(moonX, moonY, mgr, 0, Math.PI * 2);
    ctx.fill();
  }
  // 月亮本体
  const moonBody = ctx.createRadialGradient(moonX - moonR * 0.2, moonY - moonR * 0.2, 0, moonX, moonY, moonR);
  moonBody.addColorStop(0, "rgba(255,250,220,0.95)");
  moonBody.addColorStop(0.6, "rgba(230,225,180,0.8)");
  moonBody.addColorStop(1, "rgba(200,200,160,0.3)");
  ctx.fillStyle = moonBody;
  ctx.beginPath();
  ctx.arc(moonX, moonY, moonR, 0, Math.PI * 2);
  ctx.fill();

  // 远处朦胧萤火虫光晕团
  for (let g = 0; g < 6; g++) {
    const gx = w * (0.1 + g * 0.16);
    const gy = treeLineY - h * 0.05;
    const gr = h * (0.06 + (g % 3) * 0.012);
    const glowAlpha = 0.06 + (g % 4) * 0.015;

    const fg = ctx.createRadialGradient(gx, gy, 0, gx, gy, gr);
    fg.addColorStop(0, `rgba(100,220,140,${glowAlpha})`);
    fg.addColorStop(0.5, `rgba(52,211,153,${glowAlpha * 0.4})`);
    fg.addColorStop(1, "rgba(16,185,129,0)");
    ctx.fillStyle = fg;
    ctx.fillRect(gx - gr, gy - gr, gr * 2, gr * 2);
  }

  // 密集松树/杉树剪影（多层三角形叠加）
  const treeRows = [
    { y: treeLineY, count: 18, minH: h * 0.12, maxH: h * 0.22, alpha: 0.2, color: "rgba(10,50,25,0.25)" },
    { y: treeLineY + h * 0.06, count: 14, minH: h * 0.15, maxH: h * 0.3, alpha: 0.35, color: "rgba(6,40,18,0.4)" },
    { y: treeLineY + h * 0.14, count: 10, minH: h * 0.2, maxH: h * 0.4, alpha: 0.5, color: "rgba(3,30,12,0.55)" },
  ];

  for (const row of treeRows) {
    const spacing = w / (row.count + 1);
    for (let t = 0; t < row.count; t++) {
      const tx = spacing * (t + 1) + (Math.sin(t * 3.7) * spacing * 0.25);
      const th = row.minH + ((t * 7.3) % 1) * (row.maxH - row.minH);
      const tw = th * 0.45;

      // 树冠（三角形）
      ctx.beginPath();
      ctx.moveTo(tx, row.y - th);
      ctx.lineTo(tx - tw, row.y);
      ctx.lineTo(tx + tw, row.y);
      ctx.closePath();
      ctx.fillStyle = row.color;
      ctx.fill();

      // 第二层小三角（层次感）
      if (th > h * 0.18) {
        ctx.beginPath();
        ctx.moveTo(tx, row.y - th * 0.65);
        ctx.lineTo(tx - tw * 0.65, row.y - th * 0.15);
        ctx.lineTo(tx + tw * 0.65, row.y - th * 0.15);
        ctx.closePath();
        ctx.fillStyle = row.color;
        ctx.fill();
      }

      // 树干（矩形）
      ctx.fillStyle = row.color;
      ctx.fillRect(tx - tw * 0.08, row.y, tw * 0.16, th * 0.12);
    }
  }

  // ★增强：树木间的薄雾层（半透明白色水平条带）
  for (let fogLayer = 0; fogLayer < 3; fogLayer++) {
    const fogY = treeLineY + h * (0.1 + fogLayer * 0.06);
    const fogGrad = ctx.createLinearGradient(0, fogY - h * 0.015, 0, fogY + h * 0.015);
    fogGrad.addColorStop(0, "rgba(200,230,210,0)");
    fogGrad.addColorStop(0.5, `rgba(200,230,210,${0.04 - fogLayer * 0.01})`);
    fogGrad.addColorStop(1, "rgba(200,230,210,0)");
    ctx.fillStyle = fogGrad;
    ctx.fillRect(0, fogY - h * 0.015, w, h * 0.03);
  }

  // ★增强：草地上的萤火虫聚集点（微弱绿色光斑）
  const clusterPoints = [
    { x: w * 0.15, y: treeLineY + h * 0.18 },
    { x: w * 0.42, y: treeLineY + h * 0.22 },
    { x: w * 0.72, y: treeLineY + h * 0.16 },
    { x: w * 0.88, y: treeLineY + h * 0.2 },
  ];
  for (const cp of clusterPoints) {
    const cg = ctx.createRadialGradient(cp.x, cp.y, 0, cp.x, cp.y, h * 0.04);
    cg.addColorStop(0, "rgba(100,220,140,0.08)");
    cg.addColorStop(1, "rgba(52,211,153,0)");
    ctx.fillStyle = cg;
    ctx.beginPath();
    ctx.arc(cp.x, cp.y, h * 0.04, 0, Math.PI * 2);
    ctx.fill();
  }

  // ★增强：树梢上的蜘蛛网（弧线+露珠点）
  const webPositions = [
    { x: w * 0.22, y: treeLineY + h * 0.02 },
    { x: w * 0.58, y: treeLineY + h * 0.05 },
    { x: w * 0.78, y: treeLineY + h * 0.01 },
  ];
  for (const web of webPositions) {
    ctx.strokeStyle = "rgba(200,230,210,0.12)";
    ctx.lineWidth = 0.5;
    // 弧线
    ctx.beginPath();
    ctx.arc(web.x, web.y, 12, Math.PI * 0.1, Math.PI * 0.9);
    ctx.stroke();
    // 露珠点
    for (let dew = 0; dew < 3; dew++) {
      const dewAngle = Math.PI * 0.2 + dew * Math.PI * 0.25;
      const dx = web.x + Math.cos(dewAngle) * 12;
      const dy = web.y + Math.sin(dewAngle) * 12;
      ctx.beginPath();
      ctx.arc(dx, dy, 1.2, 0, Math.PI * 2);
      ctx.fillStyle = "rgba(220,250,230,0.2)";
      ctx.fill();
    }
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 6. sunset — 大峡谷日落 【增强版】
// 背景：太阳在画面偏下位置 + 层叠峡谷/山峦剪影
// 增强：鹰剪影、河流反光、飞鸟群、镜头光晕
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawCanyonSunset(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  _c: ThemeConfig["colors"]
) {
  const sunX = w * 0.5;
  const sunY = h * 0.58;
  const sunR = h * 0.1;

  // 太阳强烈光晕
  for (let gl = 4; gl >= 0; gl--) {
    const gr = sunR * (1.5 + gl * 1.2);
    const ga = ctx.createRadialGradient(sunX, sunY, sunR * 0.5, sunX, sunY, gr);
    ga.addColorStop(0, `rgba(255,200,80,${0.12 - gl * 0.02})`);
    ga.addColorStop(0.5, `rgba(255,140,40,${0.06 - gl * 0.01})`);
    ga.addColorStop(1, "rgba(255,100,20,0)");
    ctx.fillStyle = ga;
    ctx.beginPath();
    ctx.arc(sunX, sunY, gr, 0, Math.PI * 2);
    ctx.fill();
  }

  // ★增强：太阳周围的镜头光晕（六角形光圈）
  const flareAngles = [0, Math.PI / 3, Math.PI * 2 / 3, Math.PI, Math.PI * 4 / 3, Math.PI * 5 / 3];
  for (let fi = 0; fi < flareAngles.length; fi++) {
    const fa = flareAngles[fi];
    const fd = sunR * (2.2 + fi * 0.4);
    const fx = sunX + Math.cos(fa) * fd;
    const fy = sunY + Math.sin(fa) * fd;
    const fgs = ctx.createRadialGradient(fx, fy, 0, fx, fy, sunR * 0.25);
    fgs.addColorStop(0, `rgba(255,220,120,${0.1 - fi * 0.012})`);
    fgs.addColorStop(1, "rgba(255,180,80,0)");
    ctx.fillStyle = fgs;
    ctx.beginPath();
    ctx.arc(fx, fy, sunR * 0.25, 0, Math.PI * 2);
    ctx.fill();
  }

  // 太阳本体
  ctx.beginPath();
  ctx.arc(sunX, sunY, sunR, 0, Math.PI * 2);
  const sunBg = ctx.createRadialGradient(sunX, sunY - sunR * 0.2, 0, sunX, sunY, sunR);
  sunBg.addColorStop(0, "rgba(255,250,200,0.98)");
  sunBg.addColorStop(0.4, "rgba(255,200,80,0.85)");
  sunBg.addColorStop(1, "rgba(255,140,40,0.4)");
  ctx.fillStyle = sunBg;
  ctx.fill();

  // 层叠峡谷剪影（4层，锯齿状贝塞尔曲线表现岩石质感）
  const canyonLayers = [
    { yOffset: h * 0.12, color: "rgba(120,60,30,0.25)", jag: 8 },
    { yOffset: h * 0.06, color: "rgba(90,45,22,0.35)", jag: 12 },
    { yOffset: 0, color: "rgba(60,30,15,0.5)", jag: 16 },
    { yOffset: -h * 0.04, color: "rgba(35,18,8,0.65)", jag: 20 },
  ];

  for (const layer of canyonLayers) {
    const ly = h * 0.68 + layer.yOffset;
    ctx.beginPath();
    ctx.moveTo(0, h);
    ctx.lineTo(0, ly);
    const segW = w / layer.jag;
    for (let j = 0; j <= layer.jag; j++) {
      const px = j * segW;
      const py = ly - Math.abs(Math.sin(j * 2.5 + layer.jag * 0.3)) * h * 0.08
                 - Math.cos(j * 4.1) * h * 0.03
                 - (j % 2 === 0 ? h * 0.02 : 0);
      if (j === 0) {
        ctx.lineTo(px, py);
      } else {
        const prevPx = (j - 1) * segW;
        const cpX = (prevPx + px) / 2;
        ctx.quadraticCurveTo(cpX, py - h * 0.015 + Math.sin(j * 3) * h * 0.01, px, py);
      }
    }
    ctx.lineTo(w, h);
    ctx.closePath();
    ctx.fillStyle = layer.color;
    ctx.fill();
  }

  // ★增强：峡谷中的河流反光（底部蜿蜒亮色线条）
  const riverY = h * 0.86;
  ctx.strokeStyle = "rgba(255,200,100,0.12)";
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(0, riverY);
  for (let rx = 0; rx <= w; rx += 20) {
    const ry = riverY + Math.sin(rx * 0.015) * 4 + Math.cos(rx * 0.008) * 2;
    ctx.lineTo(rx, ry);
  }
  ctx.stroke();

  // ★增强：远处飞鸟群（几个小点排成一列）
  const birdGroupX = w * 0.25;
  const birdGroupY = h * 0.28;
  ctx.strokeStyle = "rgba(60,35,20,0.2)";
  ctx.lineWidth = 0.7;
  for (let bi = 0; bi < 5; bi++) {
    const bix = birdGroupX + bi * 12 + (bi % 2) * 4;
    const biy = birdGroupY + (bi % 3) * 3 - 3;
    const bsize = 4 + (bi % 3);
    ctx.beginPath();
    ctx.moveTo(bix, biy);
    ctx.lineTo(bix - bsize, biy - bsize * 0.35);
    ctx.moveTo(bix, biy);
    ctx.lineTo(bix + bsize, biy - bsize * 0.35);
    ctx.stroke();
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 7. rosegold — 香山红叶秋景 ⭐【重点增强】
// 背景：秋日暖阳天空 + 多层连绵红色山脉剪影 + 山间薄雾
// 增强：瀑布、枫树林、红叶旋涡、炊烟、水面倒影
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawMapleMountain(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  _c: ThemeConfig["colors"]
) {
  const mtnBase = h * 0.58;

  // 多层红色山脉（从远到近：淡红→玫红→深红）
  const mountainRanges = [
    { yOff: h * 0.1, color: "rgba(200,120,140,0.25)", pts: generateMountainPath(w, 5, 0.06, 1.2) },
    { yOff: h * 0.05, color: "rgba(180,80,110,0.35)", pts: generateMountainPath(w, 6, 0.08, 1.8) },
    { yOff: 0, color: "rgba(160,50,85,0.45)", pts: generateMountainPath(w, 7, 0.1, 2.3) },
    { yOff: -h * 0.04, color: "rgba(130,30,60,0.6)", pts: generateMountainPath(w, 8, 0.12, 3.0) },
  ];

  for (const range of mountainRanges) {
    ctx.beginPath();
    ctx.moveTo(0, h);
    ctx.lineTo(0, mtnBase + range.yOff);
    for (let i = 0; i < range.pts.length; i++) {
      const pt = range.pts[i];
      if (i === 0) {
        ctx.lineTo(pt.x, mtnBase + range.yOff - pt.h);
      } else {
        const prev = range.pts[i - 1];
        ctx.bezierCurveTo(
          (prev.x + pt.x) / 2, mtnBase + range.yOff - prev.h - pt.h * 0.1,
          (prev.x + pt.x) / 2, mtnBase + range.yOff - pt.h + pt.h * 0.1,
          pt.x, mtnBase + range.yOff - pt.h
        );
      }
    }
    ctx.lineTo(w, mtnBase + range.yOff - range.pts[range.pts.length - 1].h * 0.5);
    ctx.lineTo(w, h);
    ctx.closePath();
    ctx.fillStyle = range.color;
    ctx.fill();
  }

  // 山间薄雾效果（半透明白色条带）
  for (let fog = 0; fog < 3; fog++) {
    const fogY = mtnBase + h * (0.02 + fog * 0.05);
    const fogGrad = ctx.createLinearGradient(0, fogY - h * 0.015, 0, fogY + h * 0.015);
    fogGrad.addColorStop(0, "rgba(255,255,255,0)");
    fogGrad.addColorStop(0.5, `rgba(255,240,245,${0.06 - fog * 0.015})`);
    fogGrad.addColorStop(1, "rgba(255,255,255,0)");
    ctx.fillStyle = fogGrad;
    ctx.fillRect(0, fogY - h * 0.015, w, h * 0.03);
  }

  // ★增强：山脚下的红色枫树林（密集三角形/圆形树冠）
  const forestY = mtnBase + h * 0.06;
  for (let tree = 0; tree < 50; tree++) {
    const tx = (tree * 47 + 7) % w;
    const ty = forestY + ((tree * 31) % int(h * 0.3));
    const tsize = 4 + (tree % 5) * 2.5;
    const redVariant = 0.2 + (tree % 6) * 0.06;
    const depthFade = 0.15 + (tree % 4) * 0.05;

    // 圆形树冠
    ctx.beginPath();
    ctx.arc(tx, ty, tsize, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(${180 + tree % 40},${40 + tree % 30},${50 + tree % 20},${depthFade})`;
    ctx.fill();

    // 三角形层次
    ctx.beginPath();
    ctx.moveTo(tx, ty - tsize * 1.3);
    ctx.lineTo(tx - tsize * 0.9, ty + tsize * 0.2);
    ctx.lineTo(tx + tsize * 0.9, ty + tsize * 0.2);
    ctx.closePath();
    ctx.fillStyle = `rgba(${160 + tree % 50},${30 + tree % 25},${45 + tree % 15},${depthFade * 0.8})`;
    ctx.fill();
  }

  // ★增强：水面倒影（山的下半部镜像）
  const reflectY = h * 0.88;
  ctx.save();
  ctx.globalAlpha = 0.06;
  ctx.translate(0, reflectY * 1.8);
  ctx.scale(1, -0.35);
  // 简化倒影山脉
  for (let ri = 0; ri < 2; ri++) {
    const range = mountainRanges[ri + 2];
    ctx.beginPath();
    ctx.moveTo(0, h);
    ctx.lineTo(0, mtnBase + range.yOff);
    for (let i = 0; i < range.pts.length; i++) {
      const pt = range.pts[i];
      ctx.lineTo(pt.x, mtnBase + range.yOff - pt.h);
    }
    ctx.lineTo(w, h);
    ctx.closePath();
    ctx.fillStyle = range.color.replace(/[\d.]+\)$/, "0.3)");
    ctx.fill();
  }
  ctx.restore();

  // 倒影波纹
  ctx.strokeStyle = "rgba(200,100,120,0.04)";
  ctx.lineWidth = 0.5;
  for (let ripple = 0; ripple < 5; ripple++) {
    const rpy = reflectY + ripple * h * 0.025;
    ctx.beginPath();
    ctx.moveTo(0, rpy);
    for (let rx = 0; rx <= w; rx += 18) {
      ctx.lineTo(rx, rpy + Math.sin(rx * 0.015 + ripple) * 2);
    }
    ctx.stroke();
  }
}

/** 辅助：生成山脉路径点 */
function generateMountainPath(w: number, segments: number, ampFactor: number, seedOffset: number): Array<{x: number; h: number}> {
  const pts: Array<{x: number; h: number}> = [];
  const segW = w / segments;
  for (let i = 0; i <= segments; i++) {
    const x = i * segW;
    const h_val = w * ampFactor * (0.3 + Math.abs(Math.sin(i * 1.7 + seedOffset)) * 0.7)
                    * (0.5 + Math.sin(i * 3.3 + seedOffset * 2) * 0.5);
    pts.push({ x, h: h_val });
  }
  return pts;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 8. mint — 清晨草地 【增强版】
// 背景：青绿清晨天空 + 地平线 + 草地纹理 + 草尖露珠反光点
// 增强：左上角太阳、小花、晨雾、栅栏
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawMorningMeadow(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  _c: ThemeConfig["colors"]
) {
  const grassBase = h * 0.6;

  // ★增强：左上角的太阳（淡青白色圆形，柔和光芒）
  const msunX = w * 0.12;
  const msunY = h * 0.15;
  const msunR = h * 0.045;
  // 太阳光芒
  for (let mr = 4; mr >= 0; mr--) {
    const mgr = msunR * (1 + mr * 1.3);
    const mga = ctx.createRadialGradient(msunX, msunY, msunR * 0.3, msunX, msunY, mgr);
    mga.addColorStop(0, `rgba(200,255,235,${0.1 - mr * 0.018})`);
    mga.addColorStop(1, "rgba(150,240,215,0)");
    ctx.fillStyle = mga;
    ctx.beginPath();
    ctx.arc(msunX, msunY, mgr, 0, Math.PI * 2);
    ctx.fill();
  }
  // 太阳本体
  const msunBody = ctx.createRadialGradient(msunX, msunY, 0, msunX, msunY, msunR);
  msunBody.addColorStop(0, "rgba(240,255,248,0.95)");
  msunBody.addColorStop(0.6, "rgba(180,245,225,0.7)");
  msunBody.addColorStop(1, "rgba(140,230,205,0.2)");
  ctx.fillStyle = msunBody;
  ctx.beginPath();
  ctx.arc(msunX, msunY, msunR, 0, Math.PI * 2);
  ctx.fill();

  // 远处地平线微光
  const horizonGlow = ctx.createLinearGradient(0, grassBase - h * 0.05, 0, grassBase + h * 0.05);
  horizonGlow.addColorStop(0, "rgba(150,240,210,0)");
  horizonGlow.addColorStop(0.5, "rgba(150,240,210,0.08)");
  horizonGlow.addColorStop(1, "rgba(100,200,170,0)");
  ctx.fillStyle = horizonGlow;
  ctx.fillRect(0, grassBase - h * 0.05, w, h * 0.1);

  // 草地底层
  ctx.fillStyle = "rgba(10,60,45,0.3)";
  ctx.fillRect(0, grassBase, w, h - grassBase);

  // 草叶纹理（多条细线从底部向上延伸）
  const grassBladeCount = Math.floor(w / 4);
  ctx.strokeStyle = "rgba(30,100,70,0.25)";
  ctx.lineWidth = 0.8;
  for (let b = 0; b < grassBladeCount; b++) {
    const bx = b * 4 + Math.sin(b * 0.8) * 2;
    const bh = 8 + Math.abs(Math.sin(b * 1.3)) * 20 + Math.sin(b * 3.7) * 8;

    ctx.beginPath();
    ctx.moveTo(bx, h);
    ctx.quadraticCurveTo(bx, h - bh * 0.6, bx, h - bh);
    ctx.stroke();
  }

  // ★增强：草丛中的小花（五瓣花形状）
  const flowerPositions = [
    { x: w * 0.15, y: grassBase + h * 0.12, size: 5 },
    { x: w * 0.35, y: grassBase + h * 0.18, size: 4 },
    { x: w * 0.58, y: grassBase + h * 0.1, size: 6 },
    { x: w * 0.78, y: grassBase + h * 0.22, size: 4.5 },
    { x: w * 0.9, y: grassBase + h * 0.15, size: 5 },
  ];
  for (const flower of flowerPositions) {
    // 五瓣花
    for (let petal = 0; petal < 5; petal++) {
      const pa = (petal / 5) * Math.PI * 2;
      const px = flower.x + Math.cos(pa) * flower.size * 0.5;
      const py = flower.y + Math.sin(pa) * flower.size * 0.5;
      ctx.beginPath();
      ctx.ellipse(px, py, flower.size * 0.4, flower.size * 0.25, pa, 0, Math.PI * 2);
      ctx.fillStyle = petal % 2 === 0 ? "rgba(255,255,240,0.2)" : "rgba(255,250,200,0.15)";
      ctx.fill();
    }
    // 花心
    ctx.beginPath();
    ctx.arc(flower.x, flower.y, flower.size * 0.2, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(230,220,150,0.25)";
    ctx.fill();
  }

  // 草尖露珠反光点（随机分布的小亮点）
  for (let d = 0; d < 25; d++) {
    const dx = (d * 37 + 13) % w;
    const dy = grassBase + h * 0.05 + ((d * 53) % (int(h * 0.3)));
    const dr = 1 + (d % 3) * 0.8;
    const da = 0.15 + (d % 4) * 0.04;

    ctx.beginPath();
    ctx.arc(dx, dy, dr, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(200,255,230,${da})`;
    ctx.fill();
  }

  // ★增强：草尖上的晨雾（半透明白色模糊区域）
  const mistGrad = ctx.createLinearGradient(0, grassBase + h * 0.25, 0, grassBase + h * 0.45);
  mistGrad.addColorStop(0, "rgba(200,255,240,0)");
  mistGrad.addColorStop(0.5, "rgba(200,255,240,0.04)");
  mistGrad.addColorStop(1, "rgba(200,255,240,0)");
  ctx.fillStyle = mistGrad;
  ctx.fillRect(0, grassBase + h * 0.25, w, h * 0.2);

  // ★增强：远处的小栅栏（几根竖线+横线）
  const fenceBaseY = grassBase + h * 0.08;
  ctx.strokeStyle = "rgba(60,120,90,0.18)";
  ctx.lineWidth = 0.8;
  // 横梁
  ctx.beginPath();
  ctx.moveTo(w * 0.05, fenceBaseY);
  ctx.lineTo(w * 0.3, fenceBaseY);
  ctx.moveTo(w * 0.05, fenceBaseY + 5);
  ctx.lineTo(w * 0.3, fenceBaseY + 5);
  ctx.stroke();
  // 竖桩
  for (let fp = 0; fp < 6; fp++) {
    const fpx = w * (0.06 + fp * 0.04);
    ctx.beginPath();
    ctx.moveTo(fpx, fenceBaseY - 3);
    ctx.lineTo(fpx, fenceBaseY + 10);
    ctx.stroke();
  }
}

// TypeScript helper: ensure int conversion
function int(n: number): number { return n | 0; }

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 9. midnight — 银河星空 + 城市灯火 ⭐【重点增强】
// 背景：深邃宇宙渐变 + 银河带效果 + 城市天际线剪影 + 窗户灯光
// 增强：月亮、星座连线、霓虹招牌闪烁、流星雨、灯塔光束
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawGalaxyCity(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  _c: ThemeConfig["colors"]
) {
  // ★增强：月亮（大的弯月或满月，带表面纹理——环形山暗斑）
  const moonX = w * 0.82;
  const moonY = h * 0.14;
  const moonR = h * 0.05;
  // 月亮光晕
  for (let mh = 3; mh >= 0; mh--) {
    const mhr = moonR * (1 + mh * 1.8);
    const mhg = ctx.createRadialGradient(moonX, moonY, moonR * 0.5, moonX, moonY, mhr);
    mhg.addColorStop(0, `rgba(200,210,255,${0.06 - mh * 0.012})`);
    mhg.addColorStop(1, "rgba(150,160,220,0)");
    ctx.fillStyle = mhg;
    ctx.beginPath();
    ctx.arc(moonX, moonY, mhr, 0, Math.PI * 2);
    ctx.fill();
  }
  // 月亮本体
  const moonBody = ctx.createRadialGradient(moonX - moonR * 0.2, moonY - moonR * 0.2, 0, moonX, moonY, moonR);
  moonBody.addColorStop(0, "rgba(245,245,255,0.98)");
  moonBody.addColorStop(0.7, "rgba(210,210,235,0.85)");
  moonBody.addColorStop(1, "rgba(170,175,210,0.4)");
  ctx.fillStyle = moonBody;
  ctx.beginPath();
  ctx.arc(moonX, moonY, moonR, 0, Math.PI * 2);
  ctx.fill();
  // 环形山暗斑
  const craters = [
    { ox: moonR * 0.2, oy: -moonR * 0.15, r: moonR * 0.12 },
    { ox: -moonR * 0.25, oy: moonR * 0.1, r: moonR * 0.08 },
    { ox: moonR * 0.1, oy: moonR * 0.25, r: moonR * 0.06 },
  ];
  for (const cr of craters) {
    ctx.beginPath();
    ctx.arc(moonX + cr.ox, moonY + cr.oy, cr.r, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(150,155,185,0.15)";
    ctx.fill();
  }

  // 银河带效果（横贯画面的斜向模糊宽条带，多层半透明叠加模拟blur）
  const milkyWayY = h * 0.32;
  const milkyWayAngle = -0.15; // 轻微倾斜

  ctx.save();
  ctx.translate(w * 0.5, milkyWayY);
  ctx.rotate(milkyWayAngle);
  ctx.translate(-w * 0.5, -milkyWayY);

  // 银河核心（多层椭圆叠加）
  for (let mw = 0; mw < 5; mw++) {
    const mwH = h * (0.12 + mw * 0.06);
    const mwW = w * (0.8 + mw * 0.15);
    const mwAlpha = 0.04 - mw * 0.006;

    ctx.beginPath();
    ctx.ellipse(w * 0.5, milkyWayY, mwW * 0.5, mwH, 0, 0, Math.PI * 2);
    const mg = ctx.createRadialGradient(w * 0.5, milkyWayY, 0, w * 0.5, milkyWayY, mwH);
    if (mw % 3 === 0) {
      mg.addColorStop(0, `rgba(200,210,255,${mwAlpha})`);
      mg.addColorStop(0.5, `rgba(150,140,230,${mwAlpha * 0.5})`);
    } else if (mw % 3 === 1) {
      mg.addColorStop(0, `rgba(180,170,255,${mwAlpha})`);
      mg.addColorStop(0.5, `rgba(130,120,210,${mwAlpha * 0.5})`);
    } else {
      mg.addColorStop(0, `rgba(220,200,255,${mwAlpha})`);
      mg.addColorStop(0.5, `rgba(170,150,240,${mwAlpha * 0.5})`);
    }
    mg.addColorStop(1, "rgba(100,80,180,0)");
    ctx.fillStyle = mg;
    ctx.fill();
  }

  // 银河中的星星团（随机亮点）
  for (let starCluster = 0; starCluster < 30; starCluster++) {
    const scx = w * (0.1 + Math.random() * 0.8);
    const scy = milkyWayY + (Math.random() - 0.5) * h * 0.15;
    const scr = 0.3 + Math.random() * 1.2;
    const sca = 0.2 + Math.random() * 0.5;

    ctx.beginPath();
    ctx.arc(scx, scy, scr, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(220,220,255,${sca * 0.3})`;
    ctx.fill();
  }

  ctx.restore();

  // ★增强：星座连线（将附近星星连成线形成星座图案）
  const constellationLines = [
    // 北斗七星形状
    [[w * 0.15, h * 0.15], [w * 0.2, h * 0.17], [w * 0.24, h * 0.14], [w * 0.28, h * 0.16]],
    [[w * 0.28, h * 0.16], [w * 0.33, h * 0.19], [w * 0.37, h * 0.17], [w * 0.4, h * 0.21]],
    // 猎户座腰带
    [[w * 0.55, h * 0.22], [w * 0.58, h * 0.23], [w * 0.61, h * 0.22]],
  ];
  ctx.strokeStyle = "rgba(150,150,220,0.1)";
  ctx.lineWidth = 0.5;
  for (const clines of constellationLines) {
    ctx.beginPath();
    ctx.moveTo(clines[0][0], clines[0][1]);
    for (let ci = 1; ci < clines.length; ci++) {
      ctx.lineTo(clines[ci][0], clines[ci][1]);
    }
    ctx.stroke();
    // 星座节点星星
    for (const cpt of clines) {
      ctx.beginPath();
      ctx.arc(cpt[0], cpt[1], 1, 0, Math.PI * 2);
      ctx.fillStyle = "rgba(200,200,255,0.25)";
      ctx.fill();
    }
  }

  // 城市天际线剪影（高低错落的矩形建筑群）
  const cityBaseY = h * 0.78;
  const buildings = generateCityBuildings(w, cityBaseY);

  for (const bld of buildings) {
    // 建筑主体
    ctx.fillStyle = "rgba(8,8,20,0.85)";
    ctx.fillRect(bld.x, bld.y, bld.w, bld.h);

    // 窗户灯光（建筑内部随机黄色小方块）
    const windowCols = Math.floor(bld.w / 6);
    const windowRows = Math.floor(bld.h / 8);
    for (let wr = 0; wr < windowRows; wr++) {
      for (let wc = 0; wc < windowCols; wc++) {
        // 使用伪随机但确定性的方式决定窗户是否亮灯
        const hash = ((bld.x * 7 + wc * 13 + wr * 31) % 100);
        if (hash < 30) { // 30%概率亮灯
          ctx.fillStyle = `rgba(255,220,120,${0.3 + (hash % 40) * 0.01})`;
          ctx.fillRect(bld.x + wc * 6 + 1, bld.y + wr * 8 + 1, 4, 6);
        }
      }
    }

    // ★增强：城市中的霓虹招牌闪烁（彩色小矩形）
    if (bld.h > 40 && bld.w > 20 && (bld.x * 13 + bld.w * 7) % 7 === 0) {
      const neonColors = ["rgba(255,50,50,0.3)", "rgba(0,240,255,0.3)", "rgba(191,0,255,0.3)", "rgba(50,255,100,0.3)"];
      const nc = neonColors[(bld.x | 0) % neonColors.length];
      ctx.fillStyle = nc;
      ctx.fillRect(bld.x + 2, bld.y + 4, bld.w - 4, 5);
    }
  }
}

/** 辅助：生成城市建筑数据 */
function generateCityBuildings(w: number, baseY: number): Array<{x: number; y: number; w: number; h: number}> {
  const buildings: Array<{x: number; y: number; w: number; h: number}> = [];
  let cx = 0;
  while (cx < w) {
    const bw = 12 + Math.floor(Math.random() * 35);
    const bh = 20 + Math.floor(Math.random() * (baseY * 0.5));
    if (cx + bw <= w + 10) {
      buildings.push({ x: cx, y: baseY - bh, w: bw, h: bh });
    }
    cx += bw + (Math.random() > 0.7 ? 2 + Math.random() * 6 : 0);
  }
  return buildings;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 10. tea — 中式庭院品茗 【增强版】
// 背景：灰绿色水墨风格天空 + 中式屋檐剪影 + 竹林 + 假山石
// 增强：石桌石凳、茶杯热气、竹叶飘落、拱桥
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawTeaGarden(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  _c: ThemeConfig["colors"]
) {
  // 中式屋檐剪影（飞檐翘角的建筑轮廓线）
  const roofY = h * 0.5;
  ctx.fillStyle = "rgba(30,40,25,0.5)";

  // 主屋檐
  ctx.beginPath();
  ctx.moveTo(w * 0.05, roofY);
  ctx.lineTo(w * 0.15, roofY + h * 0.03);
  // 左侧飞檐上翘
  ctx.quadraticCurveTo(w * 0.2, roofY - h * 0.04, w * 0.22, roofY - h * 0.07);
  ctx.quadraticCurveTo(w * 0.25, roofY - h * 0.03, w * 0.3, roofY + h * 0.01);
  // 屋脊平段
  ctx.lineTo(w * 0.7, roofY + h * 0.01);
  // 右侧飞檐上翘
  ctx.quadraticCurveTo(w * 0.75, roofY - h * 0.03, w * 0.78, roofY - h * 0.07);
  ctx.quadraticCurveTo(w * 0.8, roofY - h * 0.04, w * 0.85, roofY + h * 0.03);
  ctx.lineTo(w * 0.95, roofY);
  ctx.lineTo(w * 0.95, h);
  ctx.lineTo(w * 0.05, h);
  ctx.closePath();
  ctx.fill();

  // 屋檐下柱子
  ctx.fillStyle = "rgba(25,35,20,0.4)";
  ctx.fillRect(w * 0.18, roofY + h * 0.02, w * 0.015, h * 0.48);
  ctx.fillRect(w * 0.8, roofY + h * 0.02, w * 0.015, h * 0.48);

  // 竹林（竖直细线 + 竹节节点）
  const bambooCount = 12;
  for (let bam = 0; bam < bambooCount; bam++) {
    const bx = w * (0.03 + bam * 0.075) + Math.sin(bam * 2.5) * w * 0.015;
    const bh = h * (0.2 + Math.sin(bam * 3.7) * 0.15);

    // 竹竿
    ctx.strokeStyle = "rgba(50,70,40,0.35)";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(bx, h);
    ctx.quadraticCurveTo(bx, h - bh * 0.5, bx, h - bh);
    ctx.stroke();

    // 竹节（小横线节点）
    const nodeCount = Math.floor(bh / h * 4);
    for (let n = 1; n < nodeCount; n++) {
      const ny = h - (bh / nodeCount) * n;
      ctx.beginPath();
      ctx.moveTo(bx - 3, ny);
      ctx.lineTo(bx + 3, ny);
      ctx.stroke();
    }
  }

  // 假山石轮廓（右侧几块石头）
  const rocks = [
    { x: w * 0.88, y: h * 0.75, pts: [[0,0], [15,-25], [30,-18], [38,-35], [32,-50], [18,-48], [5,-35], [-8,-28], [0,0]] },
    { x: w * 0.93, y: h * 0.82, pts: [[0,0], [10,-15], [20,-10], [25,-22], [18,-30], [5,-25], [0,0]] },
  ];

  for (const rock of rocks) {
    ctx.beginPath();
    ctx.moveTo(rock.x + rock.pts[0][0], rock.y + rock.pts[0][1]);
    for (let p = 1; p < rock.pts.length; p++) {
      ctx.lineTo(rock.x + rock.pts[p][0], rock.y + rock.pts[p][1]);
    }
    ctx.closePath();
    ctx.fillStyle = "rgba(45,55,38,0.35)";
    ctx.fill();
  }

  // ★增强：石桌石凳（底部几何形状）
  const tableX = w * 0.55;
  const tableY = h * 0.82;
  const tableW = w * 0.08;
  const tableH = h * 0.025;
  // 石桌
  ctx.fillStyle = "rgba(50,60,42,0.35)";
  ctx.fillRect(tableX - tableW / 2, tableY, tableW, tableH);
  // 桌腿
  ctx.fillRect(tableX - tableW * 0.35, tableY + tableH, tableW * 0.08, h * 0.04);
  ctx.fillRect(tableX + tableW * 0.27, tableY + tableH, tableW * 0.08, h * 0.04);
  // 石凳（左右各一）
  ctx.fillRect(tableX - tableW * 0.7, tableY + h * 0.035, tableW * 0.25, tableH * 0.5);
  ctx.fillRect(tableX + tableW * 0.45, tableY + h * 0.035, tableW * 0.25, tableH * 0.5);

  // ★增强：茶杯（桌上的简单椭圆）
  ctx.beginPath();
  ctx.ellipse(tableX, tableY - 3, 6, 4, 0, 0, Math.PI * 2);
  ctx.fillStyle = "rgba(180,170,150,0.3)";
  ctx.fill();
  ctx.strokeStyle = "rgba(120,110,90,0.25)";
  ctx.lineWidth = 0.8;
  ctx.stroke();

  // ★增强：远处拱桥（弧形线条）
  const bridgeX = w * 0.25;
  const bridgeY = h * 0.76;
  ctx.strokeStyle = "rgba(60,70,50,0.3)";
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(bridgeX, bridgeY + h * 0.06, w * 0.1, Math.PI, Math.PI * 2);
  ctx.stroke();
  // 桥栏杆
  ctx.lineWidth = 0.6;
  ctx.beginPath();
  ctx.arc(bridgeX, bridgeY + h * 0.04, w * 0.095, Math.PI, Math.PI * 2);
  ctx.stroke();
  // 桥墩
  ctx.fillStyle = "rgba(50,60,42,0.25)";
  ctx.fillRect(bridgeX - 2, bridgeY + h * 0.06, 4, h * 0.08);
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 11. cyberpunk — 霓虹城市夜景 ⭐【重点增强】
// 背景：纯黑 + 赛博朋克城市天际线 + 全息网格地面 + 雨
// 增强：全息广告牌、飞行载具、无人机、积水反光、扫描线
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawCyberpunkCity(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  c: ThemeConfig["colors"]
) {
  // 赛博朋克城市天际线（尖锐几何建筑）
  const cyberBaseY = h * 0.7;
  const cyberBuildings = [
    { x: 0, bw: w * 0.04, bh: h * 0.25, hasAntenna: false },
    { x: w * 0.045, bw: w * 0.06, bh: h * 0.4, hasAntenna: true },
    { x: w * 0.115, bw: w * 0.035, bh: h * 0.28, hasAntenna: false },
    { x: w * 0.158, bw: w * 0.07, bh: h * 0.55, hasAntenna: true },
    { x: w * 0.236, bw: w * 0.045, bh: h * 0.35, hasAntenna: false },
    { x: w * 0.289, bw: w * 0.055, bh: h * 0.48, hasAntenna: true },
    { x: w * 0.352, bw: w * 0.03, bh: h * 0.22, hasAntenna: false },
    { x: w * 0.39, bw: w * 0.08, bh: h * 0.62, hasAntenna: true }, // 最高楼
    { x: w * 0.478, bw: w * 0.04, bh: h * 0.32, hasAntenna: false },
    { x: w * 0.526, bw: w * 0.06, bh: h * 0.42, hasAntenna: true },
    { x: w * 0.595, bw: w * 0.035, bh: h * 0.3, hasAntenna: false },
    { x: w * 0.638, bw: w * 0.065, bh: h * 0.5, hasAntenna: true },
    { x: w * 0.712, bw: w * 0.04, bh: h * 0.26, hasAntenna: false },
    { x: w * 0.76, bw: w * 0.055, bh: h * 0.38, hasAntenna: false },
    { x: w * 0.824, bw: w * 0.045, bh: h * 0.45, hasAntenna: true },
    { x: w * 0.877, bw: w * 0.035, bh: h * 0.28, hasAntenna: false },
    { x: w * 0.92, bw: w * 0.06, bh: h * 0.35, hasAntenna: false },
  ];

  for (const bld of cyberBuildings) {
    const by = cyberBaseY - bld.bh;

    // 建筑主体填充
    ctx.fillStyle = "rgba(5,5,12,0.95)";
    ctx.fillRect(bld.x, by, bld.bw, bld.bh);

    // 霓虹边缘描边（交替青色和品红色）
    const edgeColor = (cyberBuildings.indexOf(bld) % 2 === 0) ? c.primary : c.secondary;
    ctx.strokeStyle = edgeColor;
    ctx.lineWidth = 1;
    ctx.shadowColor = edgeColor;
    ctx.shadowBlur = 6;
    ctx.strokeRect(bld.x, by, bld.bw, bld.bh);
    ctx.shadowBlur = 0;

    // 天线
    if (bld.hasAntenna) {
      ctx.strokeStyle = c.primary;
      ctx.lineWidth = 0.8;
      ctx.beginPath();
      ctx.moveTo(bld.x + bld.bw * 0.5, by);
      ctx.lineTo(bld.x + bld.bw * 0.5, by - h * 0.04);
      ctx.stroke();

      // 天线顶端闪烁红灯
      ctx.beginPath();
      ctx.arc(bld.x + bld.bw * 0.5, by - h * 0.04, 1.5, 0, Math.PI * 2);
      ctx.fillStyle = "rgba(255,50,50,0.6)";
      ctx.fill();
    }

    // 建筑窗户（霓虹网格感）
    const winW = 2;
    const winH = 3;
    const cols = Math.floor(bld.bw / (winW + 2));
    const rows = Math.floor(bld.bh / (winH + 3));
    for (let wr = 0; wr < rows; wr++) {
      for (let wc = 0; wc < cols; wc++) {
        const whash = ((bld.x | 0) * 17 + wc * 23 + wr * 41) % 100;
        if (whash < 25) {
          ctx.fillStyle = (wc + wr) % 3 === 0
            ? `rgba(0,240,255,${0.2 + whash * 0.004})`
            : `rgba(191,0,255,${0.15 + whash * 0.003})`;
          ctx.fillRect(bld.x + wc * (winW + 2) + 1, by + wr * (winH + 3) + 1, winW, winH);
        }
      }
    }
  }

  // ★增强：全息广告牌（建筑物之间悬浮的大矩形，用渐变色块模拟）
  const hologramData = [
    { x: w * 0.17, y: h * 0.25, hw: w * 0.06, hh: h * 0.1, color: c.primary },
    { x: w * 0.47, y: h * 0.15, hw: w * 0.08, hh: h * 0.12, color: c.secondary },
    { x: w * 0.74, y: h * 0.28, hw: w * 0.055, hh: h * 0.08, color: c.primary },
  ];
  for (const hg of hologramData) {
    // 广告牌外框
    ctx.strokeStyle = hg.color;
    ctx.lineWidth = 1;
    ctx.shadowColor = hg.color;
    ctx.shadowBlur = 10;
    ctx.strokeRect(hg.x, hg.y, hg.hw, hg.hh);
    ctx.shadowBlur = 0;
    // 内部渐变填充
    const hgg = ctx.createLinearGradient(hg.x, hg.y, hg.x + hg.hw, hg.y + hg.hh);
    hgg.addColorStop(0, hg.color.replace(")", ",0.08)").replace("rgb", "rgba"));
    hgg.addColorStop(0.5, hg.color.replace(")", ",0.04)").replace("rgb", "rgba"));
    hgg.addColorStop(1, hg.color.replace(")", ",0.08)").replace("rgb", "rgba"));
    ctx.fillStyle = hgg;
    ctx.fillRect(hg.x + 1, hg.y + 1, hg.hw - 2, hg.hh - 2);
    // 广告牌文字占位符（横向线条模拟文字）
    ctx.fillStyle = `${hg.color}20`;
    for (let line = 0; line < 4; line++) {
      ctx.fillRect(hg.x + 4, hg.y + 6 + line * (hg.hh / 5), hg.hw - 8, 2);
    }
  }

  // 全息网格地面（透视网格线，从底部中心向外扩散）
  const gridVanishY = cyberBaseY + h * 0.05;
  const gridBottomY = h;
  const horizonX = w * 0.5;

  // 水平网格线（从消失点向下扩散，间距递增）
  ctx.strokeStyle = "rgba(0,240,255,0.08)";
  ctx.lineWidth = 0.5;
  for (let gl = 0; gl < 15; gl++) {
    const ratio = gl / 15;
    const gy = gridVanishY + (gridBottomY - gridVanishY) * Math.pow(ratio, 1.5);
    ctx.beginPath();
    ctx.moveTo(0, gy);
    ctx.lineTo(w, gy);
    ctx.stroke();
  }

  // 放射网格线（从底部中心向外）
  for (let ray = -8; ray <= 8; ray++) {
    const angle = (ray / 8) * 0.6;
    ctx.beginPath();
    ctx.moveTo(horizonX, gridVanishY);
    ctx.lineTo(horizonX + Math.tan(angle) * (gridBottomY - gridVanishY), gridBottomY);
    ctx.strokeStyle = `rgba(0,240,255,${0.05 + Math.abs(ray) * 0.005})`;
    ctx.stroke();
  }

  // ★增强：地面的霓虹积水反光（底部建筑倒影+拉伸变形）
  const puddleY = h * 0.94;
  ctx.save();
  ctx.globalAlpha = 0.06;
  for (const bld of cyberBuildings.slice(0, 8)) {
    const by = cyberBaseY - bld.bh;
    // 简化的建筑倒影（拉伸变形）
    const reflectH = bld.bh * 0.15;
    const reflectGrad = ctx.createLinearGradient(bld.x, puddleY, bld.x + bld.bw, puddleY);
    reflectGrad.addColorStop(0, (cyberBuildings.indexOf(bld) % 2 === 0 ? c.primary : c.secondary).replace(")", ",0.3)").replace("rgb", "rgba"));
    reflectGrad.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = `${(cyberBuildings.indexOf(bld) % 2 === 0 ? c.primary : c.secondary)}08`;
    ctx.fillRect(bld.x, puddleY - reflectH, bld.bw, reflectH);
  }
  ctx.restore();

  // 雨滴效果
  ctx.strokeStyle = "rgba(150,200,255,0.12)";
  ctx.lineWidth = 0.6;
  const rainCount = 60;
  for (let r = 0; r < rainCount; r++) {
    const rx = ((r * 137.5) % w);
    const ry = ((r * 73) % (h + 40)) - 20;
    const rl = 10 + (r % 4) * 5;
    ctx.beginPath();
    ctx.moveTo(rx, ry);
    ctx.lineTo(rx - 1, ry + rl);
    ctx.stroke();
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 12. chinese — 中国山水画卷 ⭐【重点增强】
// 背景：宣纸质感底色 + 远山淡墨 + 近景山石 + 瀑布 + 飞鸟
// 增强：更多层次远山、近景松树、亭台楼阁、水面波纹、印章落款、更多飞鸟
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function drawInkLandscape(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  _time: number,
  c: ThemeConfig["colors"]
) {
  // ★增强：更多层次的远山（5-6层，越来越淡）
  const inkMountains = [
    { yBase: h * 0.32, color: "rgba(180,175,165,0.12)", amp: 0.05, freq: 6 },
    { yBase: h * 0.37, color: "rgba(170,165,155,0.15)", amp: 0.06, freq: 5 },
    { yBase: h * 0.42, color: "rgba(160,155,145,0.2)", amp: 0.08, freq: 3 },
    { yBase: h * 0.48, color: "rgba(145,140,130,0.26)", amp: 0.09, freq: 4 },
    { yBase: h * 0.54, color: "rgba(125,120,110,0.33)", amp: 0.1, freq: 5 },
    { yBase: h * 0.6, color: "rgba(105,100,90,0.4)", amp: 0.11, freq: 4 },
  ];

  for (const mtn of inkMountains) {
    ctx.beginPath();
    ctx.moveTo(0, h);
    ctx.lineTo(0, mtn.yBase);
    const segs = mtn.freq;
    const segW = w / segs;
    for (let s = 0; s <= segs; s++) {
      const sx = s * segW;
      const sy = mtn.yBase - h * mtn.amp * (
        0.3 + Math.sin(s * 1.3 + mtn.freq) * 0.4 +
        Math.sin(s * 2.7 + mtn.freq * 0.5) * 0.3
      );
      if (s === 0) {
        ctx.lineTo(sx, sy);
      } else {
        ctx.bezierCurveTo(
          sx - segW * 0.5, sy - h * 0.008,
          sx - segW * 0.5, sy + h * 0.008,
          sx, sy
        );
      }
    }
    ctx.lineTo(w, h);
    ctx.closePath();
    ctx.fillStyle = mtn.color;
    ctx.fill();
  }

  // ★增强：亭台楼阁（远处的中国建筑剪影）
  const pavilionX = w * 0.72;
  const pavilionY = h * 0.44;
  const pvSize = h * 0.06;
  // 亭子基座
  ctx.fillStyle = "rgba(100,95,85,0.25)";
  ctx.fillRect(pavilionX - pvSize * 0.4, pavilionY, pvSize * 0.8, pvSize * 0.3);
  // 亭子屋顶（飞檐）
  ctx.beginPath();
  ctx.moveTo(pavilionX - pvSize * 0.55, pavilionY);
  ctx.lineTo(pavilionX - pvSize * 0.35, pavilionY - pvSize * 0.3);
  ctx.lineTo(pavilionX, pavilionY - pvSize * 0.4);
  ctx.lineTo(pavilionX + pvSize * 0.35, pavilionY - pvSize * 0.3);
  ctx.lineTo(pavilionX + pvSize * 0.55, pavilionY);
  ctx.closePath();
  ctx.fillStyle = "rgba(90,85,75,0.28)";
  ctx.fill();
  // 亭柱
  ctx.fillStyle = "rgba(85,80,70,0.2)";
  ctx.fillRect(pavilionX - pvSize * 0.3, pavilionY, pvSize * 0.06, pvSize * 0.28);
  ctx.fillRect(pavilionX + pvSize * 0.24, pavilionY, pvSize * 0.06, pvSize * 0.28);

  // 近景山石（较深的墨色填充）
  ctx.beginPath();
  ctx.moveTo(0, h);
  ctx.lineTo(0, h * 0.68);
  ctx.bezierCurveTo(w * 0.12, h * 0.62, w * 0.2, h * 0.72, w * 0.32, h * 0.65);
  ctx.bezierCurveTo(w * 0.42, h * 0.58, w * 0.5, h * 0.7, w * 0.6, h * 0.63);
  ctx.bezierCurveTo(w * 0.72, h * 0.55, w * 0.8, h * 0.67, w * 0.9, h * 0.6);
  ctx.lineTo(w, h * 0.65);
  ctx.lineTo(w, h);
  ctx.closePath();
  ctx.fillStyle = "rgba(70,65,55,0.45)";
  ctx.fill();

  // ★增强：近景松树（右侧详细的松树剪影——弯曲树干+扇形树冠）
  const pineX = w * 0.88;
  const pineY = h * 0.62;
  const pineH = h * 0.25;
  // 松树树干（弯曲）
  ctx.strokeStyle = "rgba(60,55,45,0.5)";
  ctx.lineWidth = 3;
  ctx.beginPath();
  ctx.moveTo(pineX, h);
  ctx.quadraticCurveTo(pineX - 8, pineY + pineH * 0.4, pineX - 3, pineY + pineH * 0.15);
  ctx.stroke();
  // 松针层（扇形分布的多层三角形）
  const pineLayers = [
    { offY: 0, wFactor: 1.0, color: "rgba(60,55,45,0.4)" },
    { offY: -pineH * 0.2, wFactor: 0.8, color: "rgba(65,60,50,0.35)" },
    { offY: -pineH * 0.38, wFactor: 0.6, color: "rgba(70,65,55,0.3)" },
    { offY: -pineH * 0.55, wFactor: 0.4, color: "rgba(75,70,60,0.25)" },
  ];
  for (const pl of pineLayers) {
    const py = pineY + pineH * 0.15 + pl.offY;
    const pw = pineH * 0.35 * pl.wFactor;
    ctx.beginPath();
    ctx.moveTo(pineX - 3, py);
    ctx.lineTo(pineX - pw, py + pineH * 0.12);
    ctx.lineTo(pineX + pw * 0.7, py + pineH * 0.1);
    ctx.closePath();
    ctx.fillStyle = pl.color;
    ctx.fill();
  }

  // 瀑布（白色垂直线条，从山上流下）
  const waterfallX = w * 0.55;
  const waterfallTop = h * 0.61;
  const waterfallBottom = h * 0.75;
  const wfWidth = w * 0.025;

  // 瀑布主体（多层白色半透明垂直线）
  for (let wl = 0; wl < 5; wl++) {
    const wx = waterfallX + (wl - 2) * (wfWidth / 5);
    const wa = 0.08 - wl * 0.012;

    ctx.beginPath();
    ctx.moveTo(wx, waterfallTop);
    ctx.lineTo(wx, waterfallBottom);
    ctx.strokeStyle = `rgba(220,215,205,${wa})`;
    ctx.lineWidth = 1.5 - wl * 0.2;
    ctx.stroke();
  }

  // 瀑布底部水雾
  const mistGrad = ctx.createRadialGradient(waterfallX, waterfallBottom, 0, waterfallX, waterfallBottom, w * 0.06);
  mistGrad.addColorStop(0, "rgba(220,215,205,0.1)");
  mistGrad.addColorStop(1, "rgba(220,215,205,0)");
  ctx.fillStyle = mistGrad;
  ctx.beginPath();
  ctx.ellipse(waterfallX, waterfallBottom, w * 0.06, h * 0.02, 0, 0, Math.PI * 2);
  ctx.fill();

  // ★增强：水面波纹（底部水平波浪线）
  const waterY = h * 0.82;
  ctx.strokeStyle = "rgba(150,145,135,0.08)";
  ctx.lineWidth = 0.5;
  for (let ripple = 0; ripple < 10; ripple++) {
    const rpy = waterY + ripple * h * 0.02;
    ctx.beginPath();
    ctx.moveTo(0, rpy);
    for (let rx = 0; rx <= w; rx += 18) {
      ctx.lineTo(rx, rpy + Math.sin(rx * 0.018 + ripple * 0.6) * 2.5);
    }
    ctx.stroke();
  }

  // ★增强：印章/落款（右下角红色方形印章框+竖排文字占位符）
  const sealX = w * 0.88;
  const sealY = h * 0.9;
  const sealSize = h * 0.045;
  // 印章外框
  ctx.strokeStyle = c.primary.replace(")", ",0.5)").replace("rgb", "rgba");
  ctx.lineWidth = 1.5;
  ctx.strokeRect(sealX, sealY, sealSize, sealSize);
  // 印章填充（半透明红色）
  ctx.fillStyle = c.primary.replace(")", ",0.12)").replace("rgb", "rgba");
  ctx.fillRect(sealX + 1, sealY + 1, sealSize - 2, sealSize - 2);
  // 竖排文字占位符（模拟篆刻文字）
  ctx.fillStyle = c.primary.replace(")", ",0.6)").replace("rgb", "rgba");
  ctx.font = `${sealSize * 0.22}px serif`;
  ctx.textAlign = "center";
  for (let charIdx = 0; charIdx < 3; charIdx++) {
    ctx.fillText("印", sealX + sealSize * 0.5, sealY + sealSize * (0.2 + charIdx * 0.28));
  }

  // ★增强：更多飞鸟（3-5只不同位置的飞鸟）
  const extraBirdData = [
    { x: w * 0.15, y: h * 0.22, size: 7 },
    { x: w * 0.42, y: h * 0.3, size: 5 },
    { x: w * 0.65, y: h * 0.25, size: 8 },
    { x: w * 0.78, y: h * 0.33, size: 4.5 },
  ];
  ctx.strokeStyle = "rgba(80,75,65,0.25)";
  ctx.lineWidth = 0.7;
  for (const bird of extraBirdData) {
    ctx.beginPath();
    ctx.moveTo(bird.x, bird.y);
    ctx.lineTo(bird.x - bird.size, bird.y - bird.size * 0.35);
    ctx.moveTo(bird.x, bird.y);
    ctx.lineTo(bird.x + bird.size, bird.y - bird.size * 0.35);
    ctx.stroke();
  }
}


// ════════════════════════════════════════════
// ★★★ 动态景色系统：每帧更新的动画元素 ★★★
// ════════════════════════════════════════════

/**
 * 初始化各主题的动态景色状态
 */
function initDynamicSceneryState(
  state: { dynamicState: DynamicSceneryState; width: number; height: number },
  theme: string,
  w: number,
  h: number
) {
  const ds: DynamicSceneryState = {};

  switch (theme) {
    case "sakura": {
      ds.clouds = [
        { x: w * 0.15, y: h * 0.1, rx: w * 0.1, ry: h * 0.03, speed: 8 },
        { x: w * 0.5, y: h * 0.18, rx: w * 0.14, ry: h * 0.04, speed: 12 },
        { x: w * 0.78, y: h * 0.08, rx: w * 0.09, ry: h * 0.025, speed: 6 },
        { x: w * 0.35, y: h * 0.24, rx: w * 0.07, ry: h * 0.02, speed: 10 },
      ];
      ds.birds = [
        { x: -w * 0.1, y: h * 0.22, speed: 30, wingPhase: 0 },
        { x: w * 0.6, y: h * 0.28, speed: 25, wingPhase: Math.PI },
        { x: w * 0.3, y: h * 0.18, speed: 35, wingPhase: Math.PI * 0.5 },
      ];
      break;
    }
    case "ocean": {
      ds.lightBeams = [
        { x: w * 0.18, width: w * 0.05, phase: 0 },
        { x: w * 0.45, width: w * 0.06, phase: 1.5 },
        { x: w * 0.72, width: w * 0.04, phase: 3 },
        { x: w * 0.9, width: w * 0.055, phase: 0.8 },
      ];
      ds.seaweedPhases = [0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 5.5, 6, 6.5];
      ds.fishSchool = Array.from({ length: 7 }, (_, i) => ({
        x: w * (0.1 + i * 0.12), y: h * (0.65 + Math.random() * 0.12),
        size: 3 + Math.random() * 4, speed: 15 + Math.random() * 20,
      }));
      break;
    }
    case "emerald": {
      ds.moonGlowPhase = 0;
      ds.fireflyClusters = [
        { x: w * 0.15, y: h * 0.72, phase: 0 },
        { x: w * 0.42, y: h * 0.76, phase: 1.2 },
        { x: w * 0.7, y: h * 0.71, phase: 2.4 },
        { x: w * 0.88, y: h * 0.74, phase: 0.8 },
      ];
      break;
    }
    case "sunset": {
      ds.eaglePos = { x: w * 0.3, y: h * 0.25, angle: 0, orbitPhase: 0 };
      break;
    }
    case "rosegold": {
      ds.waterfallPhase = 0;
      ds.leafVortexes = [
        { x: w * 0.3, y: h * 0.55, rotation: 0, size: 12 },
        { x: w * 0.62, y: h * 0.6, rotation: Math.PI, size: 10 },
        { x: w * 0.82, y: h * 0.52, rotation: Math.PI * 0.5, size: 8 },
      ];
      ds.smokePlumes = [
        { x: w * 0.45, y: h * 0.58, particles: [] },
        { x: w * 0.7, y: h * 0.56, particles: [] },
      ];
      break;
    }
    case "midnight": {
      ds.meteorShower = [];
      ds.lighthouseAngle = -Math.PI * 0.3;
      break;
    }
    case "tea": {
      ds.steamParticles = [];
      for (let i = 0; i < 12; i++) {
        ds.steamParticles.push({
          x: (Math.random() - 0.5) * 10, y: (Math.random() - 0.5) * 5,
          size: 3 + Math.random() * 5, opacity: 0, life: Math.random() * 3,
        });
      }
      break;
    }
    case "cyberpunk": {
      ds.vehicles = [
        { x: -w * 0.1, y: h * 0.68, speed: 80, lane: 0 },
        { x: w * 1.1, y: h * 0.72, speed: -60, lane: 1 },
        { x: -w * 0.05, y: h * 0.75, speed: 100, lane: 2 },
      ];
      ds.scanlineY = 0;
      break;
    }
    case "chinese": {
      ds.extraBirds = [
        { x: w * 0.12, y: h * 0.26, speed: 20, wingPhase: 0, size: 6 },
        { x: w * 0.38, y: h * 0.32, speed: 18, wingPhase: 1.5, size: 5 },
        { x: w * 0.58, y: h * 0.24, speed: 22, wingPhase: 0.8, size: 7 },
        { x: w * 0.76, y: h * 0.3, speed: 16, wingPhase: 2.2, size: 5.5 },
        { x: w * 0.48, y: h * 0.36, speed: 19, wingPhase: 3, size: 4.5 },
      ];
      break;
    }
  }

  state.dynamicState = ds;
}

/** 动态景色分发器 */
function drawDynamicScenery(ctx: CanvasRenderingContext2D, theme: string, state: { dynamicState: DynamicSceneryState; time: number; width: number; height: number }, dt: number, w: number, h: number, c: ThemeConfig["colors"]) {
  const { dynamicState } = state;
  switch (theme) {
    case "sakura": drawSakuraDynamic(ctx, dynamicState, dt, w, h); break;
    case "ocean": drawOceanDynamic(ctx, dynamicState, dt, w, h); break;
    case "emerald": drawEmeraldDynamic(ctx, dynamicState, dt, w, h); break;
    case "sunset": drawSunsetDynamic(ctx, dynamicState, dt, w, h); break;
    case "rosegold": drawRosegoldDynamic(ctx, dynamicState, dt, w, h); break;
    case "midnight": drawMidnightDynamic(ctx, dynamicState, dt, w, h); break;
    case "tea": drawTeaDynamic(ctx, dynamicState, dt, w, h); break;
    case "cyberpunk": drawCyberpunkDynamic(ctx, dynamicState, dt, w, h, c); break;
    case "chinese": drawChineseDynamic(ctx, dynamicState, dt, w, h); break;
  }
}

/** sakura动态：云朵漂移+小鸟飞过 */
function drawSakuraDynamic(ctx: CanvasRenderingContext2D, ds: DynamicSceneryState, dt: number, w: number, _h: number) {
  if (!ds.clouds || !ds.birds) return;
  for (const cloud of ds.clouds) {
    cloud.x += cloud.speed * dt;
    if (cloud.x > w + cloud.rx * 2) cloud.x = -cloud.rx * 2;
    ctx.save(); ctx.globalAlpha = 0.08; ctx.fillStyle = "rgba(240,180,210,0.5)";
    ctx.beginPath(); ctx.ellipse(cloud.x, cloud.y, cloud.rx, cloud.ry, 0, 0, Math.PI * 2); ctx.fill();
    ctx.globalAlpha = 0.06; ctx.fillStyle = "rgba(255,200,230,0.5)";
    ctx.beginPath(); ctx.ellipse(cloud.x - cloud.rx * 0.2, cloud.y - cloud.ry * 0.3, cloud.rx * 0.6, cloud.ry * 0.6, 0, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
  }
  for (const bird of ds.birds) {
    bird.x += bird.speed * dt; bird.wingPhase += dt * 6;
    if (bird.x > w + 50) bird.x = -50;
    const flapAngle = Math.sin(bird.wingPhase) * 0.3, span = 8;
    ctx.strokeStyle = "rgba(80,60,70,0.25)"; ctx.lineWidth = 0.8;
    ctx.beginPath(); ctx.moveTo(bird.x, bird.y);
    ctx.lineTo(bird.x - span, bird.y - span * 0.35 + flapAngle * 3);
    ctx.moveTo(bird.x, bird.y); ctx.lineTo(bird.x + span, bird.y - span * 0.35 - flapAngle * 3);
    ctx.stroke();
  }
}

/** ocean动态：光柱摆动+海草摆动+鱼群游动 */
function drawOceanDynamic(ctx: CanvasRenderingContext2D, ds: DynamicSceneryState, dt: number, w: number, h: number) {
  if (!ds.lightBeams || !ds.seaweedPhases || !ds.fishSchool) return;
  for (const beam of ds.lightBeams) {
    beam.phase += dt * 0.8;
    const sway = Math.sin(beam.phase) * w * 0.01;
    const bg = ctx.createLinearGradient(beam.x + sway, 0, beam.x + sway, h * 0.78);
    bg.addColorStop(0, `rgba(150,210,255,${0.04 + Math.sin(beam.phase * 0.5) * 0.02})`);
    bg.addColorStop(1, "rgba(60,140,220,0)");
    ctx.fillStyle = bg; ctx.fillRect(beam.x + sway - beam.width / 2, 0, beam.width, h * 0.78);
  }
  const sandY = h * 0.78;
  let pi = 0;
  const bases = [{ x: w * 0.1, c: 5 }, { x: w * 0.3, c: 4 }, { x: w * 0.55, c: 6 }, { x: w * 0.8, c: 4 }];
  for (const sw of bases) {
    for (let s = 0; s < sw.c && pi < ds.seaweedPhases.length; s++, pi++) {
      ds.seaweedPhases[pi] += dt * (1.5 + s * 0.3);
      const sx = sw.x + s * 8 - (sw.c * 4), sh = h * (0.06 + (s % 4) * 0.025);
      const swayAmt = Math.sin(ds.seaweedPhases[pi]) * 6;
      ctx.strokeStyle = `rgba(40,140,80,${0.2 + (s % 3) * 0.05})`; ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.moveTo(sx, sandY);
      ctx.quadraticCurveTo(sx + swayAmt, sandY - sh * 0.5, sx + swayAmt * 0.7 + (s % 2 ? 3 : -3), sandY - sh);
      ctx.stroke();
    }
  }
  for (const fish of ds.fishSchool) {
    fish.x += fish.speed * dt; if (fish.x > w + 30) fish.x = -30;
    ctx.save(); ctx.translate(fish.x, fish.y); ctx.scale(fish.speed > 0 ? 1 : -1, 1);
    ctx.fillStyle = "rgba(100,180,220,0.2)";
    ctx.beginPath(); ctx.ellipse(0, 0, fish.size, fish.size * 0.4, 0, 0, Math.PI * 2); ctx.fill();
    ctx.beginPath(); ctx.moveTo(-fish.size, 0); ctx.lineTo(-fish.size * 1.4, -fish.size * 0.3); ctx.lineTo(-fish.size * 1.4, fish.size * 0.3); ctx.closePath(); ctx.fill();
    ctx.restore();
  }
}

/** emerald动态：月亮光晕呼吸+萤火虫聚集点闪烁 */
function drawEmeraldDynamic(ctx: CanvasRenderingContext2D, ds: DynamicSceneryState, dt: number, w: number, h: number) {
  if (ds.moonGlowPhase !== undefined) {
    ds.moonGlowPhase += dt * 0.5;
    const pa = 0.06 + Math.sin(ds.moonGlowPhase) * 0.02, mx = w * 0.85, my = h * 0.12;
    const mg = ctx.createRadialGradient(mx, my, h * 0.045, mx, my, h * 0.12);
    mg.addColorStop(0, `rgba(230,240,180,${pa})`); mg.addColorStop(1, "rgba(200,220,150,0)");
    ctx.fillStyle = mg; ctx.fillRect(mx - h * 0.15, my - h * 0.15, h * 0.3, h * 0.3);
  }
  if (ds.fireflyClusters) {
    for (const fc of ds.fireflyClusters) {
      fc.phase += dt * 2;
      const flicker = Math.pow(Math.max(0, Math.sin(fc.phase)), 3) * 0.12;
      const cg = ctx.createRadialGradient(fc.x, fc.y, 0, fc.x, fc.y, h * 0.05);
      cg.addColorStop(0, `rgba(100,220,140,${flicker})`); cg.addColorStop(1, "rgba(52,211,153,0)");
      ctx.fillStyle = cg; ctx.beginPath(); ctx.arc(fc.x, fc.y, h * 0.05, 0, Math.PI * 2); ctx.fill();
    }
  }
}

/** sunset动态：鹰剪影盘旋 */
function drawSunsetDynamic(ctx: CanvasRenderingContext2D, ds: DynamicSceneryState, dt: number, w: number, h: number) {
  if (!ds.eaglePos) return;
  const e = ds.eaglePos; e.orbitPhase += dt * 0.3;
  e.x = w * 0.5 + Math.cos(e.orbitPhase) * w * 0.15;
  e.y = h * 0.28 + Math.sin(e.orbitPhase * 1.5) * h * 0.08;
  e.angle = Math.atan2(Math.cos(e.orbitPhase) * h * 0.08 * 1.5, -Math.sin(e.orbitPhase) * w * 0.15);
  ctx.save(); ctx.translate(e.x, e.y); ctx.rotate(e.angle + Math.PI * 0.1);
  ctx.strokeStyle = "rgba(50,30,15,0.25)"; ctx.lineWidth = 1.2;
  ctx.beginPath(); ctx.moveTo(0, 0); ctx.quadraticCurveTo(-15, -8, -25, -3);
  ctx.moveTo(0, 0); ctx.quadraticCurveTo(15, -8, 25, -3);
  ctx.moveTo(-3, 0); ctx.lineTo(5, 0); ctx.stroke(); ctx.restore();
}

/** rosegold动态：瀑布水流+红叶旋涡+炊烟 */
function drawRosegoldDynamic(ctx: CanvasRenderingContext2D, ds: DynamicSceneryState, dt: number, w: number, h: number) {
  if (ds.waterfallPhase !== undefined) {
    ds.waterfallPhase += dt * 4;
    const wfX = w * 0.55, wfT = h * 0.61, wfB = h * 0.75;
    for (let wl = 0; wl < 5; wl++) {
      const wx = wfX + (wl - 2) * (w * 0.005), wob = Math.sin(ds.waterfallPhase + wl * 1.5) * 2;
      ctx.beginPath(); ctx.moveTo(wx + wob, wfT);
      ctx.lineTo(wx + wob * 1.3 + Math.sin(ds.waterfallPhase * 1.2 + wl) * 1, wfB);
      ctx.strokeStyle = `rgba(255,250,245,${0.07 - wl * 0.01})`; ctx.lineWidth = 1.5 - wl * 0.2; ctx.stroke();
    }
  }
  if (ds.leafVortexes) {
    for (const lv of ds.leafVortexes) {
      lv.rotation += dt * 1.5; lv.y += dt * 8;
      if (lv.y > h + 20) { lv.y = h * 0.4; lv.x = w * (0.2 + Math.random() * 0.6); }
      ctx.save(); ctx.translate(lv.x, lv.y); ctx.rotate(lv.rotation); ctx.globalAlpha = 0.3;
      for (let leaf = 0; leaf < 5; leaf++) {
        const la = (leaf / 5) * Math.PI * 2, lr = lv.size * 0.6;
        ctx.fillStyle = `rgba(${200 + leaf * 10},${40 + leaf * 8},${50 + leaf * 5},0.25)`;
        ctx.beginPath();
        ctx.moveTo(Math.cos(la) * lv.size * 0.3, Math.sin(la) * lv.size * 0.3);
        ctx.lineTo(Math.cos(la) * lv.size * 0.3 + Math.cos(la + 0.5) * lr, Math.sin(la) * lv.size * 0.3 + Math.sin(la + 0.5) * lr);
        ctx.lineTo(Math.cos(la) * lv.size * 0.3 + Math.cos(la - 0.5) * lr, Math.sin(la) * lv.size * 0.3 + Math.sin(la - 0.5) * lr);
        ctx.closePath(); ctx.fill();
      }
      ctx.restore();
    }
  }
  if (ds.smokePlumes) {
    for (const sp of ds.smokePlumes) {
      if (Math.random() < 0.05 && sp.particles.length < 15)
        sp.particles.push({ x: sp.x + (Math.random() - 0.5) * 8, y: sp.y, opacity: 0.15 });
      for (let pi = sp.particles.length - 1; pi >= 0; pi--) {
        const p = sp.particles[pi]; p.y -= dt * 15; p.x += (Math.random() - 0.5) * 3; p.opacity -= dt * 0.03;
        if (p.opacity <= 0) { sp.particles.splice(pi, 1); continue; }
        ctx.beginPath(); ctx.arc(p.x, p.y, 4 + (sp.particles.length - pi) * 0.5, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(120,110,100,${p.opacity})`; ctx.fill();
      }
    }
  }
}

/** midnight动态：流星雨+灯塔光束 */
function drawMidnightDynamic(ctx: CanvasRenderingContext2D, ds: DynamicSceneryState, dt: number, w: number, h: number) {
  if (ds.meteorShower !== undefined) {
    if (Math.random() < 0.03 && ds.meteorShower.length < 6)
      ds.meteorShower.push({ x: Math.random() * w * 0.8, y: Math.random() * h * 0.2, vx: 120 + Math.random() * 180, vy: 60 + Math.random() * 90, life: 0, maxLife: 0.6 + Math.random() * 0.5 });
    for (let mi = ds.meteorShower.length - 1; mi >= 0; mi--) {
      const m = ds.meteorShower[mi]; m.life += dt; m.x += m.vx * dt; m.y += m.vy * dt;
      const prog = m.life / m.maxLife;
      if (prog >= 1 || m.x > w + 100 || m.y > h + 100) { ds.meteorShower.splice(mi, 1); continue; }
      const alpha = prog < 0.15 ? prog / 0.15 : prog > 0.75 ? (1 - prog) / 0.25 : 1;
      ctx.beginPath(); ctx.moveTo(m.x, m.y); ctx.lineTo(m.x - m.vx * 0.04 * 50, m.y - m.vy * 0.04 * 50);
      const stg = ctx.createLinearGradient(m.x, m.y, m.x - m.vx * 0.04 * 50, m.y - m.vy * 0.04 * 50);
      stg.addColorStop(0, `rgba(255,255,255,${alpha})`); stg.addColorStop(1, "transparent");
      ctx.strokeStyle = stg; ctx.lineWidth = 1.5; ctx.stroke();
    }
  }
  if (ds.lighthouseAngle !== undefined) {
    ds.lighthouseAngle += dt * 0.4;
    ctx.save(); ctx.translate(0, h * 0.7); ctx.rotate(ds.lighthouseAngle);
    const lhg = ctx.createLinearGradient(0, 0, Math.sqrt(w * w + h * h), 0);
    lhg.addColorStop(0, "rgba(255,250,220,0.06)"); lhg.addColorStop(1, "rgba(255,250,220,0)");
    ctx.fillStyle = lhg; ctx.beginPath(); ctx.moveTo(0, -3); ctx.lineTo(Math.sqrt(w * w + h * h), -15); ctx.lineTo(Math.sqrt(w * w + h * h), 15); ctx.closePath(); ctx.fill();
    ctx.restore();
  }
}

/** tea动态：茶杯热气上升 */
function drawTeaDynamic(ctx: CanvasRenderingContext2D, ds: DynamicSceneryState, dt: number, _w: number, _h: number) {
  if (!ds.steamParticles) return;
  for (const sp of ds.steamParticles) {
    sp.life -= dt;
    if (sp.life <= 0) { sp.life = 2 + Math.random() * 2; sp.opacity = 0; sp.x = (Math.random() - 0.5) * 8; sp.y = 0; continue; }
    if (sp.life > 1.5) { sp.opacity = Math.min(0.15, (2 - sp.life) * 0.3); sp.y -= dt * 12; sp.x += Math.sin(sp.life * 4) * 3; }
    else { sp.opacity *= 0.98; sp.y -= dt * 6; }
    ctx.beginPath(); ctx.arc(sp.x, sp.y, sp.size, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(240,245,235,${sp.opacity})`; ctx.fill();
  }
}

/** cyberpunk动态：载具+无人机+扫描线 */
function drawCyberpunkDynamic(ctx: CanvasRenderingContext2D, ds: DynamicSceneryState, dt: number, w: number, h: number, c: ThemeConfig["colors"]) {
  if (ds.vehicles) {
    for (const v of ds.vehicles) {
      v.x += v.speed * dt; if (v.x > w + 30) v.x = -30; if (v.x < -30) v.x = w + 30;
      ctx.fillStyle = (v.lane % 2 === 0) ? c.primary : c.secondary;
      ctx.shadowColor = ctx.fillStyle as string; ctx.shadowBlur = 6;
      ctx.fillRect(v.x, v.y, 12, 4); ctx.shadowBlur = 0;
    }
  }
  const t = Date.now() * 0.001;
  for (let di = 0; di < 3; di++) {
    const dx = w * (0.2 + di * 0.3) + Math.sin(t + di * 2) * w * 0.05, dy = h * (0.15 + (di % 2) * 0.08) + Math.cos(t * 0.7 + di) * h * 0.03;
    const blinkOn = Math.sin(t * 5 + di * 3) > 0;
    ctx.strokeStyle = `rgba(0,240,255,${blinkOn ? 0.4 : 0.1})`; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(dx - 5, dy); ctx.lineTo(dx + 5, dy); ctx.moveTo(dx, dy - 5); ctx.lineTo(dx, dy + 5); ctx.stroke();
    if (blinkOn) { ctx.fillStyle = "rgba(255,50,50,0.6)"; ctx.beginPath(); ctx.arc(dx, dy, 1.5, 0, Math.PI * 2); ctx.fill(); }
  }
  if (ds.scanlineY !== undefined) {
    ds.scanlineY += h * dt * 0.3; if (ds.scanlineY > h) ds.scanlineY = 0;
    const sg = ctx.createLinearGradient(0, ds.scanlineY - 2, 0, ds.scanlineY + 2);
    sg.addColorStop(0, "rgba(0,240,255,0)"); sg.addColorStop(0.5, "rgba(0,240,255,0.04)"); sg.addColorStop(1, "rgba(0,240,255,0)");
    ctx.fillStyle = sg; ctx.fillRect(0, ds.scanlineY - 2, w, 4);
  }
}

/** chinese动态：更多飞鸟动画 */
function drawChineseDynamic(ctx: CanvasRenderingContext2D, ds: DynamicSceneryState, dt: number, w: number, _h: number) {
  if (!ds.extraBirds) return;
  for (const bird of ds.extraBirds) {
    bird.x += bird.speed * dt; bird.wingPhase += dt * 5; bird.y += Math.sin(Date.now() * 0.001 + bird.x * 0.01) * 0.3;
    if (bird.x > w + 30) bird.x = -30;
    const flap = Math.sin(bird.wingPhase) * 0.3;
    ctx.strokeStyle = "rgba(80,75,65,0.2)"; ctx.lineWidth = 0.7;
    ctx.beginPath(); ctx.moveTo(bird.x, bird.y);
    ctx.lineTo(bird.x - bird.size, bird.y - bird.size * 0.35 + flap * 3);
    ctx.moveTo(bird.x, bird.y); ctx.lineTo(bird.x + bird.size, bird.y - bird.size * 0.35 - flap * 3);
    ctx.stroke();
  }
}

// ═══════════════════════════════════════════
// 粒子绘制函数系统
// ═══════════════════════════════════════════

/** 绘制樱花花瓣粒子 */
function drawPetals(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; width: number; height: number }, dt: number, colors: ThemeConfig["colors"]) {
  const { particles, width, height } = state;
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    p.x += p.vx * dt;
    p.y += p.vy * dt;
    p.vy += 15 * dt; // 重力
    p.rotation += p.rotationSpeed * dt;
    if (p.wobble !== undefined) p.wobble += dt * 2;
    const sway = Math.sin(p.wobble || 0) * 30;
    const alpha = Math.min(1, p.life / p.maxLife) * p.opacity;
    ctx.save();
    ctx.translate(p.x + sway, p.y);
    ctx.rotate(p.rotation);
    ctx.globalAlpha = alpha;
    // 绘制花瓣形状（五瓣花简化为心形）
    ctx.fillStyle = colors.primary;
    ctx.beginPath();
    ctx.moveTo(0, -p.size);
    ctx.bezierCurveTo(p.size * 0.8, -p.size * 1.2, p.size * 1.2, -p.size * 0.3, 0, p.size * 0.5);
    ctx.bezierCurveTo(-p.size * 1.2, -p.size * 0.3, -p.size * 0.8, -p.size * 1.2, 0, -p.size);
    ctx.fill();
    // 花瓣中心高光
    ctx.fillStyle = "rgba(255,255,255,0.3)";
    ctx.beginPath(); ctx.arc(0, -p.size * 0.2, p.size * 0.2, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
    // 边界回收
    if (p.y > height + 50 || p.x < -50 || p.x > width + 50) {
      p.life = 0;
    }
  }
}

/** 绘制枫叶粒子 */
function drawMapleLeaves(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; width: number; height: number }, dt: number, colors: ThemeConfig["colors"]) {
  const { particles, width, height } = state;
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    p.x += p.vx * dt;
    p.y += p.vy * dt;
    p.vy += 12 * dt; // 重力稍轻
    p.rotation += p.rotationSpeed * dt;
    if (p.wobble !== undefined) p.wobble += dt * 1.5;
    const sway = Math.sin(p.wobble || 0) * 25;
    const prog = p.life / p.maxLife;
    const alpha = Math.min(1, prog) * p.opacity;
    const scale = p.scale || 1;
    ctx.save();
    ctx.translate(p.x + sway, p.y);
    ctx.rotate(p.rotation);
    ctx.globalAlpha = alpha;
    ctx.scale(scale, scale);
    drawMapleLeafShape(ctx, p.size, colors.primary, colors.secondary);
    ctx.restore();
    if (p.y > height + 50 || p.x < -50 || p.x > width + 50) {
      p.life = 0;
    }
  }
}

/** 绘制单个枫叶形状（复用函数） */
function drawMapleLeafShape(ctx: CanvasRenderingContext2D, size: number, primaryColor: string, secondaryColor: string) {
  ctx.fillStyle = primaryColor;
  ctx.beginPath();
  // 枫叶轮廓：5个尖角
  const s = size;
  ctx.moveTo(0, -s * 1.2);           // 顶点
  ctx.lineTo(s * 0.15, -s * 0.7);    // 右上内凹
  ctx.lineTo(s * 0.6, -s * 0.9);     // 右上角
  ctx.lineTo(s * 0.4, -s * 0.3);     // 右上内凹2
  ctx.lineTo(s * 0.9, -s * 0.1);     // 右角
  ctx.lineTo(s * 0.35, s * 0.2);     // 右内凹
  ctx.lineTo(s * 0.5, s * 0.7);      // 右下角
  ctx.lineTo(0, s * 0.4);            // 底部中点
  ctx.lineTo(-s * 0.5, s * 0.7);     // 左下角
  ctx.lineTo(-s * 0.35, s * 0.2);    // 左内凹
  ctx.lineTo(-s * 0.9, -s * 0.1);    // 左角
  ctx.lineTo(-s * 0.4, -s * 0.3);    // 左内凹2
  ctx.lineTo(-s * 0.6, -s * 0.9);    // 左上角
  ctx.lineTo(-s * 0.15, -s * 0.7);   // 左上内凹
  ctx.closePath();
  ctx.fill();
  // 叶脉
  ctx.strokeStyle = secondaryColor;
  ctx.lineWidth = 0.5;
  ctx.beginPath(); ctx.moveTo(0, -s * 1.1); ctx.lineTo(0, s * 0.6); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(0, -s * 0.3); ctx.lineTo(s * 0.5, -s * 0.5); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(0, -s * 0.3); ctx.lineTo(-s * 0.5, -s * 0.5); ctx.stroke();
}

/** 绘制蝴蝶粒子 */
function drawButterflies(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; width: number; height: number }, dt: number, colors: ThemeConfig["colors"]) {
  const { particles, width, height } = state;
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    p.x += p.vx * dt;
    p.y += p.vy * dt + Math.sin((p.phase || 0) * 3) * 0.5;
    p.phase = (p.phase || 0) + dt * 4;
    p.rotation += p.rotationSpeed * dt;
    const prog = p.life / p.maxLife;
    const alpha = Math.min(1, prog) * p.opacity;
    const wingFlap = Math.sin(p.phase * 8) * 0.4;
    ctx.save();
    ctx.translate(p.x, p.y);
    ctx.rotate(p.rotation);
    ctx.globalAlpha = alpha;
    // 身体
    ctx.fillStyle = "rgba(60,40,30,0.8)";
    ctx.beginPath(); ctx.ellipse(0, 0, p.size * 0.15, p.size * 0.5, 0, 0, Math.PI * 2); ctx.fill();
    // 上翅膀
    ctx.fillStyle = colors.primary;
    ctx.beginPath();
    ctx.moveTo(0, -p.size * 0.1);
    ctx.quadraticCurveTo(p.size * 0.8 * (1 + wingFlap), -p.size * 0.8, p.size * 0.6 * (1 + wingFlap * 0.5), 0);
    ctx.quadraticCurveTo(p.size * 0.8 * (1 + wingFlap), p.size * 0.2, 0, p.size * 0.1);
    ctx.closePath(); ctx.fill();
    ctx.beginPath();
    ctx.moveTo(0, -p.size * 0.1);
    ctx.quadraticCurveTo(-p.size * 0.8 * (1 + wingFlap), -p.size * 0.8, -p.size * 0.6 * (1 + wingFlap * 0.5), 0);
    ctx.quadraticCurveTo(-p.size * 0.8 * (1 + wingFlap), p.size * 0.2, 0, p.size * 0.1);
    ctx.closePath(); ctx.fill();
    // 下翅膀
    ctx.fillStyle = colors.secondary;
    ctx.globalAlpha = alpha * 0.7;
    ctx.beginPath();
    ctx.moveTo(0, p.size * 0.15);
    ctx.quadraticCurveTo(p.size * 0.5 * (1 + wingFlap * 0.7), p.size * 0.5, p.size * 0.35 * (1 + wingFlap * 0.3), p.size * 0.25);
    ctx.quadraticCurveTo(p.size * 0.2, p.size * 0.35, 0, p.size * 0.2);
    ctx.closePath(); ctx.fill();
    ctx.beginPath();
    ctx.moveTo(0, p.size * 0.15);
    ctx.quadraticCurveTo(-p.size * 0.5 * (1 + wingFlap * 0.7), p.size * 0.5, -p.size * 0.35 * (1 + wingFlap * 0.3), p.size * 0.25);
    ctx.quadraticCurveTo(-p.size * 0.2, p.size * 0.35, 0, p.size * 0.2);
    ctx.closePath(); ctx.fill();
    // 翅膀斑点
    ctx.fillStyle = "rgba(255,255,255,0.4)";
    ctx.beginPath(); ctx.arc(p.size * 0.35, -p.size * 0.25, p.size * 0.12, 0, Math.PI * 2); ctx.fill();
    ctx.beginPath(); ctx.arc(-p.size * 0.35, -p.size * 0.25, p.size * 0.12, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
    if (p.y < -50 || p.y > height + 50 || p.x < -50 || p.x > width + 50) {
      p.life = 0;
    }
  }
}

/** 绘制波浪和气泡粒子（海洋主题） */
function drawWavesAndBubbles(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; time: number }, dt: number, _colors: ThemeConfig["colors"], width: number, height: number) {
  const { particles, time } = state;
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    p.x += p.vx * dt;
    p.y += p.vy * dt;
    p.phase = (p.phase || 0) + dt * 2;
    const prog = p.life / p.maxLife;
    const alpha = Math.min(1, prog) * p.opacity;
    ctx.save();
    ctx.globalAlpha = alpha;
    // 气泡效果
    const bubbleSize = p.size * (0.8 + Math.sin(p.phase * 3) * 0.2);
    const gradient = ctx.createRadialGradient(
      p.x - bubbleSize * 0.3, p.y - bubbleSize * 0.3, 0,
      p.x, p.y, bubbleSize
    );
    gradient.addColorStop(0, "rgba(180,220,255,0.6)");
    gradient.addColorStop(0.5, "rgba(100,180,255,0.3)");
    gradient.addColorStop(1, "rgba(60,140,220,0.1)");
    ctx.fillStyle = gradient;
    ctx.beginPath(); ctx.arc(p.x, p.y, bubbleSize, 0, Math.PI * 2); ctx.fill();
    // 高光
    ctx.fillStyle = "rgba(255,255,255,0.5)";
    ctx.beginPath(); ctx.arc(p.x - bubbleSize * 0.3, p.y - bubbleSize * 0.3, bubbleSize * 0.2, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
    if (p.y < -50) p.life = 0;
  }
  // 绘制底部波浪线
  const waveY = height * 0.85;
  ctx.strokeStyle = "rgba(100,180,255,0.15)";
  ctx.lineWidth = 2;
  for (let w = 0; w < 3; w++) {
    ctx.beginPath();
    for (let x = 0; x <= width; x += 5) {
      const y = waveY + w * 8 + Math.sin(x * 0.02 + time * 2 + w) * 6 + Math.sin(x * 0.01 + time * 1.5) * 4;
      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
  }
}

/** 绘制萤火虫粒子（翡翠/森林主题） */
function drawFireflies(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; width: number; height: number }, dt: number, colors: ThemeConfig["colors"], _width: number, _height: number) {
  const { particles } = state;
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    // 萤火虫随机漂移
    p.vx += (Math.random() - 0.5) * 20 * dt;
    p.vy += (Math.random() - 0.5) * 20 * dt;
    p.vx *= 0.98; p.vy *= 0.98; // 阻尼
    p.x += p.vx * dt;
    p.y += p.vy * dt;
    p.phase = (p.phase || 0) + dt * 3;
    const prog = p.life / p.maxLife;
    // 闪烁效果
    const flicker = Math.pow(Math.sin(p.phase * 5), 4) * 0.7 + 0.3;
    const alpha = Math.min(1, prog) * p.opacity * flicker;
    ctx.save();
    ctx.globalAlpha = alpha;
    // 光晕
    const glowSize = p.size * 3;
    const glow = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, glowSize);
    glow.addColorStop(0, colors.glow);
    glow.addColorStop(0.4, colors.primary.replace(")", ",0.3)").replace("rgb", "rgba"));
    glow.addColorStop(1, "transparent");
    ctx.fillStyle = glow;
    ctx.beginPath(); ctx.arc(p.x, p.y, glowSize, 0, Math.PI * 2); ctx.fill();
    // 萤火虫本体
    ctx.fillStyle = "#ffffaa";
    ctx.beginPath(); ctx.arc(p.x, p.y, p.size * 0.3, 0, Math.PI * 2); ctx.fill();
    // 尾迹
    if (p.trail && p.trail.length > 0) {
      ctx.strokeStyle = colors.glow;
      ctx.lineWidth = 1;
      ctx.beginPath();
      for (let t = 0; t < p.trail.length; t++) {
        const pt = p.trail[t];
        const trailAlpha = (t / p.trail.length) * 0.3;
        ctx.globalAlpha = alpha * trailAlpha;
        if (t === 0) ctx.moveTo(pt.x, pt.y);
        else ctx.lineTo(pt.x, pt.y);
      }
      ctx.stroke();
    }
    ctx.restore();
  }
}

/** 绘制阳光射线（日落主题） */
function drawSunRays(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; time: number; width: number; height: number }, dt: number, colors: ThemeConfig["colors"], width: number, height: number) {
  const { particles, time } = state;
  const sunX = width * 0.5;
  const sunY = height * 0.35;
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    p.phase = (p.phase || 0) + dt * 0.5;
    const prog = p.life / p.maxLife;
    const alpha = Math.min(1, prog) * p.opacity * (0.5 + Math.sin(p.phase) * 0.3);
    const angle = (p.x / width) * Math.PI * 0.6 - Math.PI * 0.3 + Math.sin(time * 0.3 + p.y * 0.01) * 0.1;
    const rayLength = height * (0.6 + Math.sin(p.phase * 2) * 0.2);
    ctx.save();
    ctx.globalAlpha = alpha * 0.15;
    ctx.translate(sunX, sunY);
    ctx.rotate(angle);
    const rayGrad = ctx.createLinearGradient(0, 0, rayLength, 0);
    rayGrad.addColorStop(0, colors.primary);
    rayGrad.addColorStop(0.3, colors.secondary);
    rayGrad.addColorStop(1, "transparent");
    ctx.strokeStyle = rayGrad;
    ctx.lineWidth = p.size * 2;
    ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(rayLength, 0); ctx.stroke();
    ctx.restore();
  }
  // 太阳光晕
  const sunGlow = ctx.createRadialGradient(sunX, sunY, 0, sunX, sunY, height * 0.25);
  sunGlow.addColorStop(0, "rgba(255,200,100,0.15)");
  sunGlow.addColorStop(0.5, "rgba(255,150,50,0.05)");
  sunGlow.addColorStop(1, "transparent");
  ctx.fillStyle = sunGlow;
  ctx.beginPath(); ctx.arc(sunX, sunY, height * 0.25, 0, Math.PI * 2); ctx.fill();
}

/** 绘制露珠粒子（薄荷主题） */
function drawDewDrops(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; width: number; height: number }, dt: number, colors: ThemeConfig["colors"], _width: number, height: number) {
  const { particles } = state;
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    p.phase = (p.phase || 0) + dt;
    const prog = p.life / p.maxLife;
    // 露珠生长效果
    const growScale = p.scale || (prog < 0.2 ? prog / 0.2 : 1);
    const currentSize = p.size * growScale;
    const alpha = prog < 0.8 ? Math.min(1, prog * 1.5) * p.opacity : (1 - prog) / 0.2 * p.opacity;
    ctx.save();
    ctx.globalAlpha = alpha;
    // 霠珠主体（折射效果）
    const dropGrad = ctx.createRadialGradient(
      p.x - currentSize * 0.3, p.y - currentSize * 0.3, 0,
      p.x, p.y, currentSize
    );
    dropGrad.addColorStop(0, "rgba(255,255,255,0.9)");
    dropGrad.addColorStop(0.3, "rgba(200,240,255,0.6)");
    dropGrad.addColorStop(0.7, colors.primary.replace(")", ",0.4)").replace("#", "rgba(").replace(/([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})/i, (_, r, g, b) => `${parseInt(r,16)},${parseInt(g,16)},${parseInt(b,16)}`));
    dropGrad.addColorStop(1, "rgba(100,200,180,0.2)");
    ctx.fillStyle = dropGrad;
    ctx.beginPath(); ctx.arc(p.x, p.y, currentSize, 0, Math.PI * 2); ctx.fill();
    // 高光
    ctx.fillStyle = "rgba(255,255,255,0.8)";
    ctx.beginPath(); ctx.arc(p.x - currentSize * 0.3, p.y - currentSize * 0.3, currentSize * 0.25, 0, Math.PI * 2); ctx.fill();
    // 底部阴影
    ctx.fillStyle = "rgba(0,50,40,0.1)";
    ctx.beginPath(); ctx.ellipse(p.x, p.y + currentSize * 0.8, currentSize * 0.8, currentSize * 0.2, 0, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
    // 停留在底部附近
    if (p.y > height * 0.85) {
      p.vy = 0;
      p.vx *= 0.95;
    }
  }
}

/** 绘制星空粒子（午夜主题） */
function drawStarrySky(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; shootingStars: ShootingStar[]; width: number; height: number; time: number }, dt: number, colors: ThemeConfig["colors"], width: number, height: number) {
  const { particles, shootingStars, time } = state;
  // 普通星星
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.twinklePhase = (p.twinklePhase || Math.random() * Math.PI * 2) + dt * (1 + Math.random() * 2);
    const twinkle = Math.pow(Math.sin(p.twinklePhase), 8) * 0.6 + 0.4;
    const alpha = p.opacity * twinkle;
    ctx.save();
    ctx.globalAlpha = alpha;
    // 星星光芒
    const starGlow = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.size * 3);
    starGlow.addColorStop(0, "#ffffff");
    starGlow.addColorStop(0.3, colors.glow);
    starGlow.addColorStop(1, "transparent");
    ctx.fillStyle = starGlow;
    ctx.beginPath(); ctx.arc(p.x, p.y, p.size * 3, 0, Math.PI * 2); ctx.fill();
    // 十字星芒（大星星）
    if (p.size > 2) {
      ctx.strokeStyle = `rgba(255,255,255,${alpha * 0.5})`;
      ctx.lineWidth = 0.5;
      const len = p.size * 2;
      ctx.beginPath(); ctx.moveTo(p.x - len, p.y); ctx.lineTo(p.x + len, p.y); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(p.x, p.y - len); ctx.lineTo(p.x, p.y + len); ctx.stroke();
    }
    // 星星核心
    ctx.fillStyle = "#ffffff";
    ctx.beginPath(); ctx.arc(p.x, p.y, p.size * 0.5, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
  }
  // 流星
  for (let i = shootingStars.length - 1; i >= 0; i--) {
    const s = shootingStars[i];
    s.life -= dt;
    if (s.life <= 0) { shootingStars.splice(i, 1); continue; }
    s.x += s.vx * dt;
    s.y += s.vy * dt;
    const prog = s.life / s.maxLife;
    const alpha = prog < 0.1 ? prog / 0.1 : prog > 0.8 ? (1 - prog) / 0.2 : 1;
    ctx.save();
    ctx.globalAlpha = alpha;
    const tailGrad = ctx.createLinearGradient(s.x, s.y, s.x - s.vx * 0.03 * s.length, s.y - s.vy * 0.03 * s.length);
    tailGrad.addColorStop(0, "#ffffff");
    tailGrad.addColorStop(0.3, colors.glow);
    tailGrad.addColorStop(1, "transparent");
    ctx.strokeStyle = tailGrad;
    ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(s.x, s.y);
    ctx.lineTo(s.x - s.vx * 0.03 * s.length, s.y - s.vy * 0.03 * s.length);
    ctx.stroke();
    ctx.restore();
  }
  // 银河背景带
  const milkyWay = ctx.createLinearGradient(0, height * 0.2, 0, height * 0.6);
  milkyWay.addColorStop(0, "transparent");
  milkyWay.addColorStop(0.5, "rgba(150,140,200,0.03)");
  milkyWay.addColorStop(1, "transparent");
  ctx.fillStyle = milkyWay;
  ctx.fillRect(0, height * 0.2, width, height * 0.4);
}

/** 绘制茶场景蒸汽粒子 */
function drawTeaScene(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; width: number; height: number }, dt: number, colors: ThemeConfig["colors"], _width: number, height: number) {
  const { particles } = state;
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    p.y -= p.vy * dt; // 向上飘
    p.x += Math.sin((p.phase || 0) + p.y * 0.02) * 0.3; // 左右摇摆
    p.phase = (p.phase || 0) + dt * 2;
    p.vy *= 0.995; // 缓慢减速
    const prog = p.life / p.maxLife;
    const alpha = prog < 0.2 ? prog / 0.2 : (prog > 0.7 ? (1 - prog) / 0.3 : 1) * p.opacity;
    const steamSize = p.size * (1 + (1 - prog) * 0.5); // 越往上越大越淡
    ctx.save();
    ctx.globalAlpha = alpha * 0.4;
    // 蒸汽团
    const steamGrad = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, steamSize);
    steamGrad.addColorStop(0, "rgba(240,245,235,0.3)");
    steamGrad.addColorStop(0.6, "rgba(220,230,210,0.15)");
    steamGrad.addColorStop(1, "transparent");
    ctx.fillStyle = steamGrad;
    ctx.beginPath(); ctx.arc(p.x, p.y, steamSize, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
    if (p.y < height * 0.2) p.life = 0;
  }
}

/** 绘制赛博朋克雨滴/电路粒子 */
function drawCyberpunkRain(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; width: number; height: number }, dt: number, colors: ThemeConfig["colors"], width: number, height: number) {
  const { particles } = state;
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    p.x += p.vx * dt;
    p.y += p.vy * dt;
    p.phase = (p.phase || 0) + dt * 10;
    const prog = p.life / p.maxLife;
    const alpha = Math.min(1, prog) * p.opacity;
    ctx.save();
    ctx.globalAlpha = alpha;
    // 电路雨滴效果
    const isCircuit = Math.random() > 0.5;
    if (isCircuit) {
      // 电路板轨迹
      ctx.strokeStyle = Math.random() > 0.5 ? colors.primary : colors.secondary;
      ctx.lineWidth = 1;
      ctx.shadowColor = ctx.strokeStyle as string;
      ctx.shadowBlur = 8;
      ctx.beginPath();
      ctx.moveTo(p.x, p.y);
      // 90度转折
      const segLen = 15;
      if (Math.floor(p.phase) % 3 === 0) {
        ctx.lineTo(p.x + segLen, p.y);
        ctx.lineTo(p.x + segLen, p.y + segLen * 2);
      } else if (Math.floor(p.phase) % 3 === 1) {
        ctx.lineTo(p.x - segLen, p.y);
        ctx.lineTo(p.x - segLen, p.y + segLen * 2);
      } else {
        ctx.lineTo(p.x, p.y + segLen * 3);
      }
      ctx.stroke();
      ctx.shadowBlur = 0;
    } else {
      // 直线雨滴
      const rainGrad = ctx.createLinearGradient(p.x, p.y, p.x, p.y + 30);
      rainGrad.addColorStop(0, "transparent");
      rainGrad.addColorStop(0.5, colors.primary);
      rainGrad.addColorStop(1, colors.secondary);
      ctx.strokeStyle = rainGrad;
      ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(p.x + p.vx * 0.02 * 20, p.y + 30); ctx.stroke();
    }
    ctx.restore();
    if (p.y > height + 50 || p.x < -50 || p.x > width + 50) {
      p.life = 0;
    }
  }
}

/** 绘制中国风灯笼粒子 */
function drawChineseLanterns(ctx: CanvasRenderingContext2D, state: { particles: Particle[]; width: number; height: number }, dt: number, colors: ThemeConfig["colors"], _width: number, height: number) {
  const { particles } = state;
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    p.y += p.vy * dt;
    p.x += Math.sin((p.phase || 0) * 1.5) * 0.3; // 轻微左右摇摆
    p.phase = (p.phase || 0) + dt;
    p.rotation += Math.sin(p.phase * 2) * 0.01; // 轻微旋转摆动
    const prog = p.life / p.maxLife;
    const alpha = prog < 0.15 ? prog / 0.15 : (prog > 0.85 ? (1 - prog) / 0.15 : 1) * p.opacity;
    const lanternSize = p.size * (prog < 0.2 ? prog / 0.2 : 1); // 出现时从小变大
    ctx.save();
    ctx.translate(p.x, p.y);
    ctx.rotate(p.rotation);
    ctx.globalAlpha = alpha;
    // 灯笼光晕
    const lanternGlow = ctx.createRadialGradient(0, 0, 0, 0, 0, lanternSize * 2.5);
    lanternGlow.addColorStop(0, "rgba(255,100,50,0.3)");
    lanternGlow.addColorStop(0.5, "rgba(255,50,30,0.1)");
    lanternGlow.addColorStop(1, "transparent");
    ctx.fillStyle = lanternGlow;
    ctx.beginPath(); ctx.arc(0, 0, lanternSize * 2.5, 0, Math.PI * 2); ctx.fill();
    // 灯笼主体（椭圆形）
    const bodyGrad = ctx.createRadialGradient(-lanternSize * 0.2, -lanternSize * 0.2, 0, 0, 0, lanternSize);
    bodyGrad.addColorStop(0, "#ff6633");
    bodyGrad.addColorStop(0.5, colors.primary);
    bodyGrad.addColorStop(1, "#cc2200");
    ctx.fillStyle = bodyGrad;
    ctx.beginPath(); ctx.ellipse(0, 0, lanternSize * 0.7, lanternSize, 0, 0, Math.PI * 2); ctx.fill();
    // 灯笼骨架线条
    ctx.strokeStyle = "rgba(200,180,50,0.6)";
    ctx.lineWidth = 0.8;
    for (let l = -3; l <= 3; l++) {
      if (l === 0) continue;
      ctx.beginPath();
      ctx.ellipse(0, l * lanternSize * 0.25, lanternSize * 0.65, lanternSize * 0.08, 0, 0, Math.PI * 2);
      ctx.stroke();
    }
    // 灯笼顶部和底座
    ctx.fillStyle = "#cc8833";
    ctx.fillRect(-lanternSize * 0.25, -lanternSize - lanternSize * 0.15, lanternSize * 0.5, lanternSize * 0.15);
    ctx.fillRect(-lanternSize * 0.25, lanternSize + lanternSize * 0.02, lanternSize * 0.5, lanternSize * 0.12);
    // 穗子
    ctx.strokeStyle = colors.secondary;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0, lanternSize + lanternSize * 0.14);
    ctx.lineTo(0, lanternSize + lanternSize * 0.5);
    ctx.stroke();
    // 穗子流苏
    for (let t = 0; t < 3; t++) {
      const tx = (t - 1) * lanternSize * 0.15;
      ctx.beginPath();
      ctx.moveTo(tx, lanternSize + lanternSize * 0.5);
      ctx.lineTo(tx + (Math.random() - 0.5) * 5, lanternSize + lanternSize * 0.7 + Math.random() * 5);
      ctx.stroke();
    }
    ctx.restore();
    if (p.y > height + 100) p.life = 0;
  }
}

// ═══════════════════════════════════════════
// UI绘制函数
// ═══════════════════════════════════════════

/** 绘制标题文字 */
function drawTitle(ctx: CanvasRenderingContext2D, state: { time: number; fadeProgress: number; fadingOut: boolean }, config: ThemeConfig, width: number, height: number) {
  const { time, fadeProgress, fadingOut } = state;
  const titleAlpha = fadingOut ? Math.max(0, 1 - fadeProgress) : Math.min(1, time * 0.8);
  if (titleAlpha <= 0) return;
  
  ctx.save();
  ctx.globalAlpha = titleAlpha;
  
  const centerX = width / 2;
  const centerY = height * 0.42;
  
  // 标题文字
  const titleText = config.nameEn.toUpperCase();
  const subtitleText = config.name;
  
  // 主标题
  const titleFontSize = Math.min(width * 0.08, 56);
  ctx.font = `700 ${titleFontSize}px "PingFang SC", "Microsoft YaHei", -apple-system, sans-serif`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  
  // 文字发光效果
  ctx.shadowColor = config.colors.glow;
  ctx.shadowBlur = 20;
  ctx.fillStyle = config.colors.accent;
  ctx.fillText(titleText, centerX, centerY);
  
  // 描边
  ctx.shadowBlur = 0;
  ctx.strokeStyle = config.colors.primary;
  ctx.lineWidth = 1;
  ctx.strokeText(titleText, centerX, centerY);
  
  // 副标题
  const subFontSize = Math.min(width * 0.035, 24);
  ctx.font = `400 ${subFontSize}px "PingFang SC", "Microsoft YaHei", -apple-system, sans-serif`;
  ctx.fillStyle = `rgba(255,255,255,${0.6 * titleAlpha})`;
  ctx.fillText(subtitleText, centerX, centerY + titleFontSize * 0.8);
  
  // 装饰线
  const lineWidth = Math.min(width * 0.15, 120);
  const lineY = centerY + titleFontSize * 1.3;
  const lineGrad = ctx.createLinearGradient(centerX - lineWidth / 2, lineY, centerX + lineWidth / 2, lineY);
  lineGrad.addColorStop(0, "transparent");
  lineGrad.addColorStop(0.5, config.colors.primary);
  lineGrad.addColorStop(1, "transparent");
  ctx.strokeStyle = lineGrad;
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(centerX - lineWidth / 2, lineY);
  ctx.lineTo(centerX + lineWidth / 2, lineY);
  ctx.stroke();
  
  ctx.restore();
}

/** 绘制点击提示 */
function drawClickHint(ctx: CanvasRenderingContext2D, time: number, width: number, height: number) {
  const hintAlpha = time > 1.5 ? Math.min(1, (time - 1.5)) * (0.4 + Math.sin(time * 2) * 0.2) : 0;
  if (hintAlpha <= 0) return;
  
  ctx.save();
  ctx.globalAlpha = hintAlpha;
  
  const centerX = width / 2;
  const hintY = height * 0.72;
  
  // 提示文字
  const hintFontSize = Math.min(width * 0.028, 18);
  ctx.font = `300 ${hintFontSize}px "PingFang SC", "Microsoft YaHei", -apple-system, sans-serif`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillStyle = "rgba(255,255,255,0.6)";
  ctx.fillText("点击任意位置进入", centerX, hintY);
  
  // 向下箭头动画
  const arrowY = hintY + hintFontSize * 1.8;
  const arrowOffset = Math.sin(time * 3) * 5;
  ctx.strokeStyle = "rgba(255,255,255,0.4)";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(centerX - 8, arrowY - 6 + arrowOffset);
  ctx.lineTo(centerX, arrowY + arrowOffset);
  ctx.lineTo(centerX + 8, arrowY - 6 + arrowOffset);
  ctx.stroke();
  
  ctx.restore();
}