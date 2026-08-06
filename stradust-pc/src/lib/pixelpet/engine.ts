/**
 * 像素动画核心引擎 (平台无关)
 *
 * 负责:
 * - 帧调度 (requestAnimationFrame 循环)
 * - 动作状态机 (idle ↔ transient ↔ ambient)
 * - 帧推进逻辑 (loop / once / pingpong)
 * - 事件通知机制
 */

import type { PetAction, PixelFrame } from './types';

// ─── 回调类型 ───
type FrameCallback = (frame: PixelFrame | null, actionName: string) => void;
type ActionChangeCallback = (actionId: string, prevActionId: string) => void;

export class PixelAnimationEngine {
  private actions: Map<string, PetAction> = new Map();
  private currentActionId: string = '';
  private frameIndex: number = 0;
  private lastFrameTime: number = 0;
  private rafId: number | null = null;
  private isPlaying: boolean = false;

  private listeners: Set<FrameCallback> = new Set();
  private actionListeners: Set<ActionChangeCallback> = new Set();

  /** pingpong 方向 */
  private pingpongDir: 1 | -1 = 1;

  /** transient 动作完成后自动回退的 target */
  private fallbackActionId: string = '';

  /** ambient 定时器 */
  private ambientTimer: ReturnType<typeof setTimeout> | null = null;
  private ambientDelay: number = 10000; // 10秒无交互触发ambient

  /** idle 超时计时器 */
  private idleTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {}

  // ══════════════════════════════════
  // 注册/注销
  // ══════════════════════════════════

  /** 注册所有动作到引擎 */
  registerActions(actions: PetAction[]): void {
    this.actions.clear();
    for (const action of actions) {
      this.actions.set(action.id, action);
    }
    // 如果没有设置当前动作，默认选 idle
    if (!this.currentActionId || !this.actions.has(this.currentActionId)) {
      const idle = actions.find((a) => a.name === 'idle');
      if (idle) this.currentActionId = idle.id;
      else if (actions.length > 0) this.currentActionId = actions[0].id;
    }
  }

  /** 清除所有注册的动作 */
  clearActions(): void {
    this.stop();
    this.actions.clear();
    this.currentActionId = '';
    this.frameIndex = 0;
  }

  // ══════════════════════════════════
  // 监听器
  // ══════════════════════════════════

  onFrame(cb: FrameCallback): () => void {
    this.listeners.add(cb);
    return () => { this.listeners.delete(cb); };
  }

  onActionChange(cb: ActionChangeCallback): () => void {
    this.actionListeners.add(cb);
    return () => { this.actionListeners.delete(cb); };
  }

  private notifyFrame(): void {
    const action = this.getCurrentAction();
    const frame = action?.frames[this.frameIndex] || null;
    for (const cb of this.listeners) {
      cb(frame, action?.name || '');
    }
  }

  private notifyActionChange(prevId: string): void {
    for (const cb of this.actionListeners) {
      cb(this.currentActionId, prevId);
    }
  }

  // ══════════════════════════════════
  // 播放控制
  // ══════════════════════════════════

  /** 开始播放循环 */
  play(): void {
    if (this.isPlaying) return;
    this.isPlaying = true;
    this.lastFrameTime = performance.now();
    this.resetAmbientTimer();
    this.rafId = requestAnimationFrame(this.tick.bind(this));
  }

  /** 停止播放 */
  stop(): void {
    this.isPlaying = false;
    if (this.rafId) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    this.clearTimers();
  }

  /** 切换动作 */
  playAction(actionId: string): void {
    const prevId = this.currentActionId;
    const action = this.actions.get(actionId);
    if (!action) return;

    this.currentActionId = actionId;
    this.frameIndex = 0;
    this.pingpongDir = 1;

    // 如果是 transient/once 类型的动作，完成后回退到 fallback
    if (action.loopMode === 'once') {
      this.fallbackActionId = prevId || this.getIdleActionId();
    }

    this.notifyActionChange(prevId);
    this.resetAmbientTimer();

    // 立即渲染第一帧
    this.notifyFrame();
  }

  /** 触发互动动作 (一次性)，完成后自动回到之前的状态 */
  triggerInteraction(actionName: string): boolean {
    const action = Array.from(this.actions.values()).find(
      (a) => a.name === actionName && a.loopMode === 'once'
    );
    if (!action) return false;
    this.playAction(action.id);
    return true;
  }

  /** 触发随机环境动作 */
  triggerRandomAmbient(): boolean {
    const ambientActions = Array.from(this.actions.values()).filter(
      (a) => ['sleep', 'eat', 'think', 'dance'].includes(a.name) && a.frames.length > 0
    );
    if (ambientActions.length === 0) return false;
    const random = ambientActions[Math.floor(Math.random() * ambientActions.length)];
    this.fallbackActionId = this.getIdleActionId();
    this.playAction(random.id);
    return true;
  }

  // ══════════════════════════════════
  // 内部：主循环
  // ══════════════════════════════════

  private tick = (timestamp: number): void => {
    if (!this.isPlaying) return;

    const action = this.getCurrentAction();
    if (!action || action.frames.length === 0) {
      this.rafId = requestAnimationFrame(this.tick.bind(this));
      return;
    }

    const elapsed = timestamp - this.lastFrameTime;
    if (elapsed >= action.frameDuration) {
      this.advanceFrame();
      this.lastFrameTime = timestamp;
      this.notifyFrame();
    }

    this.rafId = requestAnimationFrame(this.tick.bind(this));
  };

  /** 帧推进 + 循环模式处理 */
  private advanceFrame(): void {
    const action = this.getCurrentAction();
    if (!action || action.frames.length <= 1) return;

    const totalFrames = action.frames.length;

    switch (action.loopMode) {
      case 'loop':
        this.frameIndex = (this.frameIndex + 1) % totalFrames;
        break;

      case 'pingpong':
        this.frameIndex += this.pingpongDir;
        if (this.frameIndex >= totalFrames - 1) {
          this.pingpongDir = -1;
          this.frameIndex = totalFrames - 1;
        } else if (this.frameIndex <= 0) {
          this.pingpongDir = 1;
          this.frameIndex = 0;
        }
        break;

      case 'once':
        this.frameIndex++;
        if (this.frameIndex >= totalFrames) {
          this.frameIndex = totalFrames - 1; // 停在最后一帧
          // 回退到 fallback 动作
          if (this.fallbackActionId && this.actions.has(this.fallbackActionId)) {
            // 延迟一帧后回退，让用户看到最后一帧
            setTimeout(() => {
              this.playAction(this.fallbackActionId);
            }, action.frameDuration * 2);
          }
        }
        break;
    }
  }

  // ══════════════════════════════════
  // 内部：定时器管理
  // ══════════════════════════════════

  private resetAmbientTimer(): void {
    this.clearTimers();
    // 当前是 idle 时，启动 ambient 触发定时器
    const action = this.getCurrentAction();
    if (action?.name === 'idle') {
      this.idleTimer = setTimeout(() => {
        this.triggerRandomAmbient();
      }, this.ambientDelay);
    }
  }

  private clearTimers(): void {
    if (this.ambientTimer) {
      clearTimeout(this.ambientTimer);
      this.ambientTimer = null;
    }
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
      this.idleTimer = null;
    }
  }

  // ══════════════════════════════════
  // 查询方法
  // ══════════════════════════════════

  getCurrentAction(): PetAction | undefined {
    return this.actions.get(this.currentActionId);
  }

  getCurrentFrame(): PixelFrame | undefined {
    const action = this.getCurrentAction();
    return action?.frames[this.frameIndex];
  }

  getIdleActionId(): string {
    const idle = Array.from(this.actions.values()).find((a) => a.name === 'idle');
    return idle?.id || this.currentActionId || '';
  }

  getIsPlaying(): boolean {
    return this.isPlaying;
  }

  /** 销毁引擎，释放资源 */
  destroy(): void {
    this.stop();
    this.listeners.clear();
    this.actionListeners.clear();
    this.actions.clear();
  }
}
