/**
 * 视线跟随（屏幕级）
 * ---------------------------------------------------------------
 * 从 Electron 桌宠 `pet/app/renderer/pet.js` 的 gaze 逻辑移植：
 *   - 指针在页面里移动 → 归一化成 (-1..1)
 *   - 带阻尼插值，避免抖动
 *   - 同时驱动眼球（ParamEyeBallX/Y）与头部（ParamAngleX/Y，权重更小）
 *   - 指针离开窗口后缓慢回中（而不是突然跳回）
 *
 * 用法：
 *   const stop = startGazeFollow({ getModel: () => model, getBounds: () => model.getBounds() });
 *   // 卸载时
 *   stop();
 */

type Any = any;

export interface GazeOptions {
  /** 眼球最大位移（参数值，1.0 = 参数上限） */
  eyeAmount?: number;
  /** 头部跟随权重（0 = 不带动头部） */
  headWeight?: number;
  /** 阻尼系数，越小越"沉"（0.05~0.35 之间） */
  damping?: number;
  /** 死区：指针离模型中心很近时不动（0~1） */
  deadZone?: number;
  /** 空闲多少毫秒后回中 */
  idleResetMs?: number;
}

interface Ctx {
  getModel: () => Any;
  getBounds: () => { x: number; y: number; width: number; height: number } | null;
  opts: Required<GazeOptions>;
  target: { x: number; y: number };
  current: { x: number; y: number };
  raf: number;
  lastMove: number;
  alive: boolean;
}

function clamp(v: number, a: number, b: number) {
  return v < a ? a : v > b ? b : v;
}

/** 参数安全写（模型缺参数时静默跳过，不同版本模型共用同一套代码） */
function setParam(model: Any, id: string, value: number) {
  try {
    const core = model?.internalModel?.coreModel;
    if (!core) return;
    if (typeof core.setParameterValueById === "function") {
      core.setParameterValueById(id, value);
    } else if (typeof core.setParameterValue === "function") {
      // 老版本按参数索引写
      const idx = core.getParameterIndex?.(id);
      if (typeof idx === "number" && idx >= 0) core.setParameterValue(idx, value);
    }
  } catch {
    /* 忽略：该模型没有这根参数 */
  }
}

export function startGazeFollow(args: {
  getModel: () => Any;
  getBounds: () => { x: number; y: number; width: number; height: number } | null;
  options?: GazeOptions;
}): () => void {
  const opts: Required<GazeOptions> = {
    eyeAmount: 1.0,
    headWeight: 0.28,
    damping: 0.16,
    deadZone: 0.06,
    idleResetMs: 2400,
    ...(args.options || {}),
  };

  const ctx: Ctx = {
    getModel: args.getModel,
    getBounds: args.getBounds,
    opts,
    target: { x: 0, y: 0 },
    current: { x: 0, y: 0 },
    raf: 0,
    lastMove: 0,
    alive: true,
  };

  const onMove = (e: PointerEvent) => {
    const b = ctx.getBounds();
    if (!b || b.width <= 0 || b.height <= 0) return;
    // 以模型包围盒中心为原点归一化；超出裁到 ±1
    const cx = b.x + b.width / 2;
    const cy = b.y + b.height * 0.38; // 视觉中心略偏上（脸的位置）
    const nx = clamp((e.clientX - cx) / (b.width * 0.9), -1, 1);
    const ny = clamp((e.clientY - cy) / (b.height * 0.55), -1, 1);

    const r = Math.hypot(nx, ny);
    if (r < opts.deadZone) {
      ctx.target.x = 0;
      ctx.target.y = 0;
    } else {
      ctx.target.x = nx;
      ctx.target.y = ny;
    }
    ctx.lastMove = performance.now();
  };

  const onLeave = () => {
    ctx.target.x = 0;
    ctx.target.y = 0;
  };

  const tick = () => {
    if (!ctx.alive) return;
    const o = ctx.opts;
    const model = ctx.getModel();
    if (model && model.internalModel) {
      // 空闲回中
      if (performance.now() - ctx.lastMove > o.idleResetMs) {
        ctx.target.x = 0;
        ctx.target.y = 0;
      }
      ctx.current.x += (ctx.target.x - ctx.current.x) * o.damping;
      ctx.current.y += (ctx.target.y - ctx.current.y) * o.damping;

      // 眼白在 PSD 左/右分件：X 轴左右相反，用同一值即可（Cubism 约定）
      setParam(model, "ParamEyeBallX", ctx.current.x * o.eyeAmount);
      setParam(model, "ParamEyeBallY", -ctx.current.y * o.eyeAmount);

      if (o.headWeight > 0) {
        // 头部跟随：幅度 30° 级参数 → 最多 ±6° 左右
        setParam(model, "ParamAngleX", ctx.current.x * 30 * o.headWeight);
        setParam(model, "ParamAngleY", -ctx.current.y * 30 * o.headWeight);
        setParam(model, "ParamBodyAngleX", ctx.current.x * 10 * o.headWeight);
      }
    }
    ctx.raf = requestAnimationFrame(tick);
  };

  window.addEventListener("pointermove", onMove, { passive: true });
  window.addEventListener("pointerleave", onLeave, { passive: true });
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) onLeave();
  });
  ctx.raf = requestAnimationFrame(tick);

  return () => {
    ctx.alive = false;
    cancelAnimationFrame(ctx.raf);
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerleave", onLeave);
    // 回中，避免停在上一个姿势
    const model = ctx.getModel();
    if (model) {
      setParam(model, "ParamEyeBallX", 0);
      setParam(model, "ParamEyeBallY", 0);
      setParam(model, "ParamAngleX", 0);
      setParam(model, "ParamAngleY", 0);
      setParam(model, "ParamBodyAngleX", 0);
    }
  };
}
