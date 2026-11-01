/**
 * 拖拽 + 落地回弹（画布内物理）
 * ---------------------------------------------------------------
 * 移植自桌宠 `pet/app/renderer/pet.js` 的拖拽逻辑，去掉"系统窗口移动"那层，
 * 改成在画布坐标系里甩动模型：松手带惯性，随后弹簧回位（轻微 overshoot）。
 *
 * 用法：
 *   const stop = enableModelDrag({ model, canvas, home: { x: 0, y: 0 } });
 */

type Any = any;

export interface DragOptions {
  /** 回位目标（画布坐标） */
  home?: { x: number; y: number };
  /** 惯性衰减系数（0.88~0.96，越大越"滑"） */
  friction?: number;
  /** 弹簧刚度（0.06~0.2，越大回位越急） */
  stiffness?: number;
  /** 松手后多久开始回位（ms） */
  settleDelay?: number;
  /** 拖动时是否限制在画布内 */
  clampToCanvas?: boolean;
  /** 命中区判定：返回 true 才允许起拖（避免点空白也能拖） */
  canStart?: (clientX: number, clientY: number) => boolean;
  /** 拖动开始/结束回调 */
  onDragStart?: () => void;
  onDragEnd?: () => void;
}

export function enableModelDrag(args: {
  model: Any;
  canvas: HTMLCanvasElement;
  options?: DragOptions;
}): () => void {
  const { model, canvas } = args;
  const o: Required<Omit<DragOptions, "canStart" | "onDragStart" | "onDragEnd">> = {
    home: { x: model?.x ?? 0, y: model?.y ?? 0 },
    friction: 0.92,
    stiffness: 0.12,
    settleDelay: 140,
    clampToCanvas: true,
    ...(args.options || {}),
  };
  const canStart = args.options?.canStart;
  const onDragStart = args.options?.onDragStart;
  const onDragEnd = args.options?.onDragEnd;

  let dragging = false;
  let pid: number | null = null;
  let start = { x: 0, y: 0, mx: 0, my: 0 };
  let v = { x: 0, y: 0 };
  let last = { x: 0, y: 0, t: 0 };
  let raf = 0;
  let settleAt = 0;
  let alive = true;

  const homeX = () => o.home?.x ?? 0;
  const homeY = () => o.home?.y ?? 0;

  const onDown = (e: PointerEvent) => {
    if (!model) return;
    if (canStart && !canStart(e.clientX, e.clientY)) return;
    dragging = true;
    pid = e.pointerId;
    try {
      canvas.setPointerCapture(e.pointerId);
    } catch {}
    start = { x: e.clientX, y: e.clientY, mx: model.x, my: model.y };
    last = { x: e.clientX, y: e.clientY, t: performance.now() };
    v = { x: 0, y: 0 };
    onDragStart?.();
  };

  const onMove = (e: PointerEvent) => {
    if (!dragging || !model || (pid !== null && e.pointerId !== pid)) return;
    const now = performance.now();
    const nx = start.mx + (e.clientX - start.x);
    const ny = start.my + (e.clientY - start.y);

    let tx = nx;
    let ty = ny;
    if (o.clampToCanvas) {
      const w = canvas.clientWidth || canvas.width;
      const h = canvas.clientHeight || canvas.height;
      tx = Math.max(-w * 0.35, Math.min(w * 0.35, nx));
      ty = Math.max(-h * 0.35, Math.min(h * 0.35, ny));
    }
    // 瞬时速度（像素/毫秒 → 归一化）
    const dt = Math.max(1, now - last.t);
    v.x = (e.clientX - last.x) / dt;
    v.y = (e.clientY - last.y) / dt;
    last = { x: e.clientX, y: e.clientY, t: now };

    model.x = tx;
    model.y = ty;
  };

  const endDrag = () => {
    if (!dragging) return;
    dragging = false;
    pid = null;
    // 甩出去：给一记初速度（ms → 帧）
    v.x *= 16;
    v.y *= 16;
    settleAt = performance.now() + o.settleDelay;
    onDragEnd?.();
  };

  const onUp = (e: PointerEvent) => {
    if (pid !== null && e.pointerId !== pid) return;
    endDrag();
    try {
      canvas.releasePointerCapture(e.pointerId);
    } catch {}
  };

  const tick = () => {
    if (!alive) return;
    if (model && !dragging) {
      const now = performance.now();
      if (now > settleAt) {
        // 惯性先衰减，再弹簧回位
        model.x += v.x;
        model.y += v.y;
        v.x *= o.friction;
        v.y *= o.friction;

        const dx = homeX() - model.x;
        const dy = homeY() - model.y;
        model.x += dx * o.stiffness;
        model.y += dy * o.stiffness;

        if (Math.abs(dx) < 0.4 && Math.abs(dy) < 0.4 && Math.hypot(v.x, v.y) < 0.4) {
          model.x = homeX();
          model.y = homeY();
          v = { x: 0, y: 0 };
        }
      }
    }
    raf = requestAnimationFrame(tick);
  };

  canvas.addEventListener("pointerdown", onDown);
  canvas.addEventListener("pointermove", onMove);
  canvas.addEventListener("pointerup", onUp);
  canvas.addEventListener("pointercancel", onUp);
  canvas.addEventListener("pointerleave", onUp);
  raf = requestAnimationFrame(tick);

  return () => {
    alive = false;
    cancelAnimationFrame(raf);
    canvas.removeEventListener("pointerdown", onDown);
    canvas.removeEventListener("pointermove", onMove);
    canvas.removeEventListener("pointerup", onUp);
    canvas.removeEventListener("pointercancel", onUp);
    canvas.removeEventListener("pointerleave", onUp);
  };
}
