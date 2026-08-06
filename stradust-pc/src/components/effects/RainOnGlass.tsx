/**
 * 雨滴打湿玻璃效果 — v13 性能优化版
 *
 * 双层渲染架构：
 *   底层 Canvas：雨滴下落 + 溅射粒子 + 水痕 + 氛围雾（纯视觉效果）
 *   顶层 DOM：碰撞点透镜水滴 — 使用 backdrop-filter 真实扭曲背后 UI
 *
 * v13 性能优化：
 *   - 透镜数量上限从 15→8（backdrop-filter 是最昂贵的操作）
 *   - 雨滴数量从 180→120（减少 Canvas 绘制压力）
 *   - 溅射粒子从 60→40（减少径向渐变创建）
 *   - 透镜使用 transform3d 触发 GPU 合成层
 *   - 碰撞面扫描间隔从 2s→3s（减少 DOM 查询）
 *   - 透镜容器 CSS contain 隔离重排
 *   - 帧率自适应：检测到掉帧时自动跳帧
 */

import { useEffect, useRef } from "react";
import { useActivationStore } from "@/stores/useActivationStore";

// ═══════════════════════════════════════════
// 数据类型
// ═══════════════════════════════════════════

interface RainDrop {
  x: number;
  y: number;
  speed: number;
  length: number;
  thickness: number;
  opacity: number;
  windOffset: number;
}

interface SplashParticle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  opacity: number;
  life: number;
  maxLife: number;
}

interface WaterStreak {
  x: number;
  y: number;
  length: number;
  speed: number;
  opacity: number;
  thickness: number;
}

/** 透镜水滴（DOM 元素） */
interface LensDroplet {
  el: HTMLDivElement;       // DOM 节点引用
  x: number;                // 中心 X（px）
  y: number;                // 中心 Y（px）
  size: number;             // 直径 px
  phase: "grow" | "hold" | "disperse"; // 生命周期阶段
  age: number;              // 已存在时间 s
  lifeTime: number;         // 总寿命 s
  blurAmount: number;       // 当前模糊量
  scaleAmount: number;      // 当前放大倍数
  opacity: number;          // 当前透明度
  rotation: number;         // 随机旋转角度
  shapeType: number;        // 形状变体 0~2
}

interface Obstacle {
  x: number;
  y: number;
  width: number;
  height: number;
  label: string;
  weight: number;
  bandHeight: number;
}

interface RainState {
  raindrops: RainDrop[];
  splashes: SplashParticle[];
  streaks: WaterStreak[];
  obstacles: Obstacle[];
  lenses: LensDroplet[];    // 透镜水滴数组
  spawnAccumulator: number;
  time: number;
  width: number;
  height: number;
  initialized: boolean;
}

interface RainOnGlassProps {
  active?: boolean;
  intensity?: number;
}

// ═══════════════════════════════════════════
// 常量配置
// ═══════════════════════════════════════════

const MAX_RAINDROPS = 120;    // v13: 180→120（减少 Canvas 绘制）
const MAX_SPLASH = 40;         // v13: 60→40（减少径向渐变创建开销）
const MAX_STREAKS = 20;        // v13: 25→20
const MAX_LENS = 8;            // v13: 15→8（backdrop-filter 最昂贵，严格控制）

export function RainOnGlass({ active = true, intensity = 0.6 }: RainOnGlassProps) {
  const { isPremiumUnlocked } = useActivationStore();

  // 高级功能门控：未解锁时不渲染雨滴效果
  if (!isPremiumUnlocked) return null;

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const lensContainerRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const animRef = useRef<number>(0);

  const stateRef = useRef({
    raindrops: [] as RainDrop[],
    splashes: [] as SplashParticle[],
    streaks: [] as WaterStreak[],
    obstacles: [] as Obstacle[],
    lenses: [] as LensDroplet[],

    spawnAccumulator: 0,
    time: 0,
    width: 0,
    height: 0,
    initialized: false,
  });

  // === 核心渲染循环 ===
  useEffect(() => {
    if (!active) return;

    console.log("[RainOnGlass] v12 activated — rain + splash + optical lens distortion");

    const canvas = canvasRef.current;
    const container = containerRef.current;
    const lensContainer = lensContainerRef.current;
    if (!canvas || !container || !lensContainer) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const state = stateRef.current;
    let lastTime = performance.now();

    // ═════ 精确碰撞面扫描系统 ═════
    const containerRect = container.getBoundingClientRect();
    const cw = containerRect.width;
    const ch = containerRect.height;

    const SELECTOR_RULES: [string, number, string][] = [
      [".titlebar-surface",          0.15, "标题栏"],
      [".sidebar-item",              0.55, "侧边栏项"],
      [".sidebar-section-label",     0.30, "侧边栏分组标签"],
      [".chat-input-surface",        0.90, "聊天输入框"],
      [".input-field",               0.85, "输入框"],
      [".bubble-user",               0.80, "用户气泡"],
      [".bubble-assistant",          0.80, "AI气泡"],
      [".bubble-system",             0.60, "系统消息"],
      [".suggested-reply",           0.75, "建议回复按钮"],
      [".btn-primary",               0.70, "主按钮"],
      [".btn-ghost",                 0.45, "幽灵按钮"],
      [".chip",                      0.65, "标签芯片"],
      [".chip.active",               0.60, "激活标签"],
      [".surface-card.featured-card",0.85, "置顶角色卡"],
      [".surface-card",              0.75, "角色卡片"],
      [".persona-card",              0.75, "人物卡片"],
      [".glass-card",                0.72, "设置卡片"],
      ["[class*='calendar'] .glass-card", 0.78, "日历面板"],
      ["[class*='CalendarPage'] button[class*='rounded']", 0.70, "日期格子"],
      [".category-pill",             0.68, "分类筛选"],
      [".input-glow",                0.82, "发光搜索框"],
      [".tea-warmth",                0.77, "日记详情区"],
      ["article.glass-card",         0.80, "动态卡片"],
      [".text-h1",                   0.40, "一级标题"],
      [".text-h2",                   0.42, "二级标题"],
      [".text-h3",                   0.44, "三级标题"],
      ["[class*='empty-state-icon']",0.65, "空状态图标"],
    ];

    const CONTAINER_EXCLUDES = [
      ".sidebar-surface",
      "[role='navigation']",
      "[role='main']",
      "[class*='page-content']",
      "[class*='PageContainer']",
      "[class*='scroll-area']",
      "[class*='ScrollArea']",
      "main", "aside", "nav",
      "[class*='flex-1'][class*='overflow-hidden']",
    ];

    const isLargeContainer = (el: HTMLElement): boolean => {
      const r = el.getBoundingClientRect();
      if (r.width > cw * 0.5 && r.height > ch * 0.35) return true;
      for (const sel of CONTAINER_EXCLUDES) {
        try { if (el.matches(sel)) return true; } catch { /* ignore */ }
      }
      return false;
    };

    const scanObstacles = (): Obstacle[] => {
      const results: Obstacle[] = [];
      const seen = new Set<string>();

      for (const [selector, weight, label] of SELECTOR_RULES) {
        try {
          document.querySelectorAll(selector).forEach((el) => {
            if (!(el instanceof HTMLElement)) return;
            if (isLargeContainer(el)) return;
            const r = el.getBoundingClientRect();
            if (r.width < 16 || r.height < 8) return;
            if (r.bottom < containerRect.top || r.top > containerRect.bottom) return;
            if (r.right < containerRect.left || r.left > containerRect.right) return;

            const ox = r.left - containerRect.left;
            const oy = r.top - containerRect.top;
            const key = `${Math.round(ox/10)}_${Math.round(oy/10)}_${Math.round(r.width/10)}_${Math.round(r.height/10)}`;
            if (seen.has(key)) return;
            seen.add(key);

            results.push({ x: ox, y: oy, width: r.width, height: r.height, label: `${label}`, weight, bandHeight: 12 });
          });
        } catch { /* ignore */ }
      }

      results.sort((a, b) => a.y - b.y);
      return results;
    };

    // ═════ 创建透镜 DOM 元素 ═════
    const createLensElement = (x: number, y: number, size: number): HTMLDivElement => {
      const el = document.createElement("div");
      el.className = "rain-lens";
      el.setAttribute("aria-hidden", "true");

      // 形状变体：0=椭圆, 1=水滴形, 2=斜切圆
      const shapeType = Math.floor(Math.random() * 3);
      let borderRadius: string;
      let clipPath: string = "";

      switch (shapeType) {
        case 0:
          borderRadius = `${size * 0.48}px`;
          break;
        case 1:
          borderRadius = `${size * 0.55}px ${size * 0.45}px ${size * 0.7}px ${size * 0.5}px / ${size * 0.5}px ${size * 0.6}px ${size * 0.45}px ${size * 0.55}px`;
          break;
        case 2:
          borderRadius = `${size * 0.42}px`;
          clipPath = `polygon(${10}% 0%, 100% 0%, 90% 100%, 0% 100%)`;
          break;
        default:
          borderRadius = `${size * 0.5}px`;
      }

      const rotation = (Math.random() - 0.5) * 25; // ±12.5°

      Object.assign(el.style, {
        position: "absolute",
        left: `${x - size / 2}px`,
        top: `${y - size / 2}px`,
        width: `${size}px`,
        height: `${size}px`,
        borderRadius,
        clipPath,
        transform: `rotate(${rotation}deg) scale3d(0.05, 0.05, 1)`, // v13: scale3d 触发 GPU 合成层
        opacity: "0",
        pointerEvents: "none",
        willChange: "transform, opacity", // v13: 缩小 willChange 范围（filter/backdrop-filter 不支持 will-change）
        backdropFilter: `blur(0px)`,
        filter: `blur(0px) brightness(1.02)`,
        background: `
          radial-gradient(
            ellipse at 35% 30%,
            rgba(220,235,255,0.06) 0%,
            rgba(200,220,245,0.03) 40%,
            transparent 70%
          )
        `,
        boxShadow: `
          inset 0 0 ${size * 0.3}px ${size * 0.03}px rgba(180,210,245,0.15),
          0 0 ${size * 0.15}px ${size * 0.04}px rgba(160,195,235,0.1)
        `,
      });

      lensContainer.appendChild(el);
      return el;
    };

    // ═════ 在碰撞点生成透镜 ═════
    const spawnLensAt = (x: number, y: number, intensityVal: number) => {
      if (state.lenses.length >= MAX_LENS) return;

      const size = 28 + Math.random() * 50 * intensityVal; // 28~78px 直径
      const el = createLensElement(x, y, size);

      const lifeTime = 1.8 + Math.random() * 1.2; // 1.8~3s 寿命
      const growDuration = 0.35 + Math.random() * 0.2; // 生长时间
      const holdDuration = lifeTime * 0.4; // 停留期
      const disperseStart = growDuration + holdDuration; // 散开开始时间

      state.lenses.push({
        el,
        x,
        y,
        size,
        phase: "grow",
        age: 0,
        lifeTime,
        blurAmount: 0,
        scaleAmount: 0.05,
        opacity: 0,
        rotation: parseFloat(el.style.transform.match(/rotate\(([^)]+)\)/)?.[1] ?? "0"),
        shapeType: Math.floor(Math.random() * 3),
      });
    };

    // ═════ 更新单个透镜状态 & DOM style ═════
    const updateLens = (lens: LensDroplet, dt: number, intVal: number): boolean => {
      lens.age += dt;
      const t = lens.age;
      const T = lens.lifeTime;
      const growT = 0.35;
      const holdEnd = T * 0.45;

      // ─── 阶段判断 ───
      if (t < growT) {
        // 生长阶段：从小到大，blur 和 scale 同步增加
        lens.phase = "grow";
        const p = t / growT; // 0→1
        const ease = 1 - Math.pow(1 - p, 3); // easeOutCubic
        lens.scaleAmount = 0.05 + ease * 0.95; // 最终到 ~1.0
        lens.blurAmount = ease * (2 + Math.random() * 3) * intVal; // 2~5px blur
        lens.opacity = ease * (0.6 + Math.random() * 0.25); // 0.6~0.85
      } else if (t < holdEnd) {
        // 停留阶段：满大小，轻微脉动
        lens.phase = "hold";
        const pulse = Math.sin((t - growT) * 4) * 0.03; // 微小呼吸
        lens.scaleAmount = 1 + pulse;
        lens.blurAmount = (2 + Math.random() * 3) * intVal;
        lens.opacity = 0.7 + Math.sin(t * 2) * 0.08;
      } else {
        // 散开阶段：放大 + blur 增强 + fade out
        lens.phase = "disperse";
        const dp = (t - holdEnd) / (T - holdEnd); // 0→1
        const ease = dp * dp; // easeIn 加速散开
        lens.scaleAmount = 1 + ease * 0.7; // 放大到 1.7x
        lens.blurAmount = (2 + ease * 6) * intVal; // blur 增强到 8px+
        lens.opacity = Math.max(0, 0.7 * (1 - ease));
      }

      // 应用样式到 DOM（v13: 使用 scale3d 触发 GPU 加速）
      const { el, rotation } = lens;
      el.style.transform = `rotate(${rotation}deg) scale3d(${lens.scaleAmount}, ${lens.scaleAmount}, 1)`;
      el.style.opacity = `${lens.opacity}`;
      el.style.backdropFilter = `blur(${lens.blurAmount.toFixed(1)}px)`;
      el.style.filter = `blur(${(lens.blurAmount * 0.3).toFixed(1)}px) brightness(${1 + lens.blurAmount * 0.015})`;

      // 死亡检测
      return lens.age < lens.lifeTime;
    };

    // ═════ 销毁透镜 DOM ═════
    const destroyLens = (lens: LensDroplet) => {
      try {
        if (lens.el.parentNode) {
          lens.el.parentNode.removeChild(lens.el);
        }
      } catch { /* already removed */ }
    };

    // 初始化
    const init = () => {
      const rect = container.getBoundingClientRect();
      state.width = rect.width;
      state.height = rect.height;

      const dpr = window.devicePixelRatio || 1;
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      canvas.style.width = `${rect.width}px`;
      canvas.style.height = `${rect.height}px`;

      state.obstacles = scanObstacles();

      // 清理旧透镜
      state.lenses.forEach(destroyLens);
      state.lenses = [];

      state.raindrops = [];
      for (let i = 0; i < Math.floor(80 * intensity); i++) {
        state.raindrops.push(spawnRainDrop(state));
      }

      state.initialized = true;
    };

    init();

    const observer = new ResizeObserver(() => { init(); });
    observer.observe(container);
    // v13: 低频定时扫描（3s，减少 DOM 查询开销）
    const scanTimer = setInterval(() => {
      state.obstacles = scanObstacles();
    }, 3000);

    // v13: 帧率自适应 — 连续掉帧时自动跳帧
    let frameTimeSum = 0;
    let frameCount = 0;
    let skipNext = false;

    const render = (now: number) => {
      const dt = Math.min((now - lastTime) / 1000, 0.033);
      lastTime = now;

      // v13: 帧率监控 — 每 30 帧检测一次平均帧耗时
      frameTimeSum += dt;
      frameCount++;
      if (frameCount >= 30) {
        const avgFrame = frameTimeSum / frameCount; // 目标 ~0.0167 (60fps)
        // 如果平均帧耗时 > 20ms（<50fps），下一帧跳过 Canvas 绘制（仅更新透镜）
        skipNext = avgFrame > 0.02;
        frameTimeSum = 0;
        frameCount = 0;
      }
      state.time += dt;

      if (!state.initialized || state.width === 0) {
        animRef.current = requestAnimationFrame(render);
        return;
      }

      const dpr = window.devicePixelRatio || 1;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, state.width, state.height);

      // v13: 掉帧时跳过 Canvas 绘制（透镜仍更新，保证交互响应）
      if (!skipNext) {
        // ═════ Canvas 层：水痕 ═════
        updateAndDrawStreaks(ctx, state, dt, intensity);

        // ═════ Canvas 层：雨滴（碰撞时生成透镜！） ═════
        updateAndDrawRain(ctx, state, dt, intensity, (x, y) => {
          spawnLensAt(x, y, intensity);
        });

        // ═════ Canvas 层：溅射 ═════
        updateAndDrawSplashes(ctx, state, dt, intensity);

        // ═════ Canvas 层：氛围雾 ═════
        drawAtmosphere(ctx, state, intensity);
      } else {
        // 跳帧模式：仅推进雨滴位置，不绘制（减少一半计算量）
        updateRainPositionsOnly(state, dt, intensity, (x, y) => {
          spawnLensAt(x, y, intensity);
        });
        skipNext = false;
      }

      // ═════ DOM 层：更新所有透镜水滴 ═════
      for (let i = state.lenses.length - 1; i >= 0; i--) {
        const alive = updateLens(state.lenses[i], dt, intensity);
        if (!alive) {
          destroyLens(state.lenses[i]);
          // 快速移除
          if (i < state.lenses.length - 1) {
            state.lenses[i] = state.lenses[state.lenses.length - 1];
          }
          state.lenses.pop();
        }
      }

      animRef.current = requestAnimationFrame(render);
    };

    animRef.current = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animRef.current);
      observer.disconnect();
      clearInterval(scanTimer);
      // 清理所有透镜 DOM
      state.lenses.forEach(destroyLens);
      state.lenses = [];
      state.raindrops = [];
      state.splashes = [];
      state.streaks = [];
      state.initialized = false;
    };
  }, [active]);

  if (!active) return null;

  return (
    <div
      ref={containerRef}
      className="fixed inset-0 pointer-events-none overflow-hidden"
      style={{ zIndex: 9999 }}
      aria-hidden="true"
    >
      {/* Canvas 层：雨滴 + 溅射 + 水痕 + 雾 */}
      <canvas
        ref={canvasRef}
        className="absolute inset-0 w-full h-full"
        style={{ pointerEvents: "none" }}
      />

      {/* DOM 透镜层：真实光线扭曲（v13: contain 隔离重排） */}
      <div
        ref={lensContainerRef}
        className="absolute inset-0 overflow-hidden"
        style={{
          pointerEvents: "none",
          contain: "strict", // v13: CSS containment — 隔离透镜层的布局/重绘，不影响主 UI
        }}
      />
    </div>
  );
}

// ════════════════════════════════════════════════
// 生成函数
// ════════════════════════════════════════════════

function spawnRainDrop(state: { width: number; height: number }): RainDrop {
  const w = state.width || 1920;
  const h = state.height || 1080;
  return {
    x: Math.random() * w * 1.15 - w * 0.07,
    y: -Math.random() * h * 0.3 - 20,
    speed: 500 + Math.random() * 600,
    length: 12 + Math.random() * 28,
    thickness: 0.6 + Math.random() * 1.3,
    opacity: 0.15 + Math.random() * 0.45,
    windOffset: -30 + Math.random() * 60,
  };
}

function spawnSplash(x: number, y: number, dropSpeed: number): SplashParticle[] {
  const count = 4 + Math.floor(Math.random() * 6);
  const particles: SplashParticle[] = [];

  for (let i = 0; i < count; i++) {
    const angle = -Math.PI / 2 + (Math.random() - 0.5) * Math.PI * 0.9;
    const speed = 40 + Math.random() * dropSpeed * 0.35;
    particles.push({
      x, y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      radius: 0.6 + Math.random() * 2.2,
      opacity: 0.7 + Math.random() * 0.3,
      life: 0.15 + Math.random() * 0.35,
      maxLife: 0.5,
    });
  }
  return particles;
}

function spawnStreak(state: { width: number; height: number }): WaterStreak {
  const w = state.width || 1920;
  return {
    x: Math.random() * w,
    y: -10 - Math.random() * 50,
    length: 15 + Math.random() * 45,
    speed: 18 + Math.random() * 35,
    opacity: 0.04 + Math.random() * 0.1,
    thickness: 0.4 + Math.random() * 0.8,
  };
}

// ════════════════════════════════════════════════
// 更新 & 绘制函数
// ════════════════════════════════════════════════

function updateAndDrawRain(
  ctx: CanvasRenderingContext2D,
  state: RainState,
  dt: number,
  intensity: number,
  onHit: (x: number, y: number) => void  // 碰撞回调：通知外层创建透镜
) {
  const { raindrops, splashes, obstacles, width, height } = state;

  state.spawnAccumulator += dt * intensity * 60;
  while (state.spawnAccumulator >= 1 && raindrops.length < MAX_RAINDROPS) {
    state.spawnAccumulator -= 1;
    raindrops.push(spawnRainDrop(state));
  }
  if (state.spawnAccumulator > 10) state.spawnAccumulator = 10;

  ctx.lineCap = "round";

  for (let i = raindrops.length - 1; i >= 0; i--) {
    const d = raindrops[i];

    const moveX = (d.windOffset / 60) * d.speed * dt;
    const moveY = d.speed * dt;
    d.x += moveX;
    d.y += moveY;

    // 碰撞检测
    let hit = false;
    for (const obs of obstacles) {
      const inBandX = d.x >= obs.x && d.x <= obs.x + obs.width;
      const inBandY = d.y >= obs.y - 2 && d.y <= obs.y + obs.bandHeight;

      if (inBandX && inBandY) {
        if (Math.random() < obs.weight) {
          hit = true;
          // 生成溅射
          const newSplashes = spawnSplash(d.x, obs.y, d.speed);
          if (splashes.length + newSplashes.length < MAX_SPLASH) {
            splashes.push(...newSplashes);
          }
          // ★ 回调：在碰撞位置创建透镜！
          onHit(d.x, obs.y);
          break;
        }
      }
    }

    // 出屏幕底部也产生小溅射 + 小透镜
    if (!hit && d.y > height) {
      if (splashes.length < MAX_SPLASH - 3) {
        const miniSplashes = spawnSplash(d.x, height, d.speed * 0.4);
        miniSplashes.forEach(s => { s.radius *= 0.6; s.life *= 0.5; });
        splashes.push(...miniSplashes);
      }
      // 底部也偶尔创建透镜（概率降低）
      if (Math.random() < 0.3) {
        onHit(d.x, height);
      }
    }

    if (hit || d.y > height + 50 || d.x < -100 || d.x > width + 100) {
      if (i < raindrops.length - 1) {
        raindrops[i] = raindrops[raindrops.length - 1];
      }
      raindrops.pop();
      continue;
    }

    // 绘制雨滴线条
    const tailX = d.x - (d.windOffset / 60) * d.length * 0.7;
    const tailY = d.y - d.length;

    ctx.beginPath();
    ctx.moveTo(d.x, d.y);
    ctx.lineTo(tailX, tailY);

    const grad = ctx.createLinearGradient(d.x, d.y, tailX, tailY);
    grad.addColorStop(0, `rgba(200,225,255,${d.opacity * intensity})`);
    grad.addColorStop(0.4, `rgba(185,215,250,${d.opacity * 0.6 * intensity})`);
    grad.addColorStop(1, `rgba(170,205,245,0)`);

    ctx.strokeStyle = grad;
    ctx.lineWidth = d.thickness;
    ctx.stroke();

    // 头部亮点
    ctx.beginPath();
    ctx.arc(d.x, d.y, d.thickness * 0.8, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(230,242,255,${d.opacity * 0.6 * intensity})`;
    ctx.fill();
  }
}

function updateAndDrawSplashes(
  ctx: CanvasRenderingContext2D,
  state: RainState,
  dt: number,
  intensity: number
) {
  const { splashes } = state;

  for (let i = splashes.length - 1; i >= 0; i--) {
    const p = splashes[i];

    p.vy += 400 * dt;
    p.vx *= 1 - dt * 2;
    p.vy *= 1 - dt * 0.8;
    p.x += p.vx * dt;
    p.y += p.vy * dt;
    p.life -= dt;
    p.opacity = Math.max(0, p.life / p.maxLife);

    if (p.life <= 0) {
      if (i < splashes.length - 1) splashes[i] = splashes[splashes.length - 1];
      splashes.pop();
      continue;
    }

    // 绘制溅射粒子
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);

    const sg = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.radius);
    sg.addColorStop(0, `rgba(240,248,255,${p.opacity * 0.85 * intensity})`);
    sg.addColorStop(0.5, `rgba(210,235,255,${p.opacity * 0.5 * intensity})`);
    sg.addColorStop(1, `rgba(190,225,255,0)`);

    ctx.fillStyle = sg;
    ctx.fill();

    if (p.radius > 1.2) {
      ctx.beginPath();
      ctx.arc(p.x - p.radius * 0.25, p.y - p.radius * 0.25, p.radius * 0.35, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(255,255,255,${p.opacity * 0.5 * intensity})`;
      ctx.fill();
    }

    const spd = Math.sqrt(p.vx * p.vx + p.vy * p.vy);
    if (spd > 60) {
      ctx.beginPath();
      ctx.moveTo(p.x, p.y);
      ctx.lineTo(p.x - p.vx * dt * 3, p.y - p.vy * dt * 3);
      ctx.strokeStyle = `rgba(200,230,255,${p.opacity * 0.25 * intensity})`;
      ctx.lineWidth = p.radius * 0.6;
      ctx.stroke();
    }
  }
}

function updateAndDrawStreaks(
  ctx: CanvasRenderingContext2D,
  state: RainState,
  dt: number,
  intensity: number
) {
  const { streaks, width, height } = state;

  if (Math.random() < dt * 2 * intensity && streaks.length < MAX_STREAKS) {
    streaks.push(spawnStreak(state));
  }

  for (let i = streaks.length - 1; i >= 0; i--) {
    const s = streaks[i];

    s.y += s.speed * dt;
    s.opacity *= 1 - dt * 0.15;
    s.thickness *= 1 - dt * 0.05;

    if (s.y > height + s.length || s.opacity < 0.005) {
      if (i < streaks.length - 1) streaks[i] = streaks[streaks.length - 1];
      streaks.pop();
      continue;
    }

    ctx.beginPath();
    ctx.moveTo(s.x, s.y);

    const midY = s.y + s.length * 0.5;
    const endY = s.y + s.length;
    ctx.quadraticCurveTo(
      s.x + Math.sin(s.y * 0.02) * 2, midY,
      s.x + Math.sin(endY * 0.01) * 3, endY
    );

    const sg = ctx.createLinearGradient(s.x, s.y, s.x, endY);
    sg.addColorStop(0, `rgba(185,212,245,${s.opacity * 0.3 * intensity})`);
    sg.addColorStop(0.5, `rgba(175,208,242,${s.opacity * 0.65 * intensity})`);
    sg.addColorStop(1, `rgba(165,200,238,${s.opacity * 0.2 * intensity})`);

    ctx.strokeStyle = sg;
    ctx.lineWidth = s.thickness;
    ctx.lineCap = "round";
    ctx.stroke();
  }
}

function drawAtmosphere(
  ctx: CanvasRenderingContext2D,
  state: RainState,
  intensity: number
) {
  const { width, height, time } = state;

  ctx.save();
  ctx.globalCompositeOperation = "soft-light";
  const fg = ctx.createLinearGradient(0, 0, 0, height);
  fg.addColorStop(0, `rgba(150,172,208,${0.06 * intensity})`);
  fg.addColorStop(0.4, `rgba(145,168,206,${0.09 * intensity})`);
  fg.addColorStop(0.75, `rgba(140,164,204,${0.05 * intensity})`);
  fg.addColorStop(1, `rgba(138,160,200,${0.02 * intensity})`);
  ctx.fillStyle = fg;
  ctx.fillRect(0, 0, width, height);
  ctx.restore();

  ctx.save();
  ctx.globalCompositeOperation = "screen";
  for (let i = 0; i < 8; i++) {
    const cx = ((Math.sin(time * 0.0003 + i * 2.5) + 1) / 2) * width;
    const cy = ((Math.cos(time * 0.0002 + i * 1.8) + 1) / 2) * height;
    const cr = 50 + (i * 37) % 90 + Math.sin(time * 0.0004 + i) * 12;
    const cg = ctx.createRadialGradient(cx, cy, 0, cx, cy, cr);
    cg.addColorStop(0, `rgba(188,214,245,${0.05 * intensity})`);
    cg.addColorStop(0.5, `rgba(172,200,235,${0.02 * intensity})`);
    cg.addColorStop(1, "rgba(165,192,228,0)");
    ctx.beginPath(); ctx.arc(cx, cy, cr, 0, Math.PI * 2); ctx.fillStyle = cg; ctx.fill();
  }
  ctx.restore();
}

// ══════════════════════════════════════════════
// v13 轻量级位置更新（掉帧跳过绘制时使用）
// 仅推进物理位置 + 碰撞检测，零 Canvas 绘制
// ══════════════════════════════════════════════

function updateRainPositionsOnly(
  state: RainState,
  dt: number,
  intensity: number,
  onHit: (x: number, y: number) => void
) {
  const { raindrops, splashes, obstacles, width, height } = state;

  // 推进雨滴位置 + 碰撞检测（不绘制）
  for (let i = raindrops.length - 1; i >= 0; i--) {
    const d = raindrops[i];
    d.x += (d.windOffset / 60) * d.speed * dt;
    d.y += d.speed * dt;

    let hit = false;
    for (const obs of obstacles) {
      if (d.x >= obs.x && d.x <= obs.x + obs.width &&
          d.y >= obs.y - 2 && d.y <= obs.y + obs.bandHeight) {
        if (Math.random() < obs.weight) {
          hit = true;
          const newSplashes = spawnSplash(d.x, obs.y, d.speed);
          if (splashes.length + newSplashes.length < MAX_SPLASH) {
            splashes.push(...newSplashes);
          }
          onHit(d.x, obs.y);
          break;
        }
      }
    }

    if (hit || d.y > height + 50 || d.x < -100 || d.x > width + 100) {
      if (i < raindrops.length - 1) raindrops[i] = raindrops[raindrops.length - 1];
      raindrops.pop();
    }
  }

  // 推进溅射粒子位置（不绘制）
  for (let i = splashes.length - 1; i >= 0; i--) {
    const p = splashes[i];
    p.vy += 400 * dt;
    p.vx *= 1 - dt * 2;
    p.vy *= 1 - dt * 0.8;
    p.x += p.vx * dt;
    p.y += p.vy * dt;
    p.life -= dt;
    if (p.life <= 0) {
      if (i < splashes.length - 1) splashes[i] = splashes[splashes.length - 1];
      splashes.pop();
    }
  }

  // 推进水痕位置（不绘制）
  for (let i = state.streaks.length - 1; i >= 0; i--) {
    const s = state.streaks[i];
    s.y += s.speed * dt;
    s.opacity *= 1 - dt * 0.15;
    if (s.y > height + s.length || s.opacity < 0.005) {
      if (i < state.streaks.length - 1) state.streaks[i] = state.streaks[state.streaks.length - 1];
      state.streaks.pop();
    }
  }
}
