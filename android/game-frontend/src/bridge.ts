/**
 * JS ↔ Kotlin 双向桥(M0)。
 * 协议:统一 JSON 事件串 { type: string, payload?: any }。
 * - JS → Kotlin: window.AndroidGameBridge.postToNative(json)  (由 Kotlin JavaScriptInterface 提供)
 * - Kotlin → JS: Kotlin 侧 evaluateJavascript 调用 window.__onNativeEvent(json)
 */

export interface GameEvent {
  type: string;
  payload?: unknown;
}

// Kotlin 侧 JavaScriptInterface 挂在 window.AndroidGameBridge 上。
declare global {
  interface Window {
    AndroidGameBridge?: {
      postToNative: (msg: string) => void;
    };
    __onNativeEvent?: (msg: string) => void;
  }
}

const listeners = new Map<string, Set<(payload: unknown) => void>>();

export function bridgeSend(type: string, payload?: unknown): void {
  const evt: GameEvent = { type, payload };
  const raw = JSON.stringify(evt);
  const ios = (window as unknown as Record<string, unknown>)['webkit'];
  if (window.AndroidGameBridge?.postToNative) {
    window.AndroidGameBridge.postToNative(raw);
  } else if (ios && (ios as { messageHandlers?: unknown }).messageHandlers) {
    // 预留 iOS 桥(本阶段仅 Android 用)
    console.warn('[bridge] iOS bridge not wired yet'); // eslint-disable-line
  } else {
    console.log('[bridge] -> native (no bridge, local only):', raw); // eslint-disable-line
  }
}

export function bridgeOn(type: string, fn: (payload: unknown) => void): void {
  if (!listeners.has(type)) listeners.set(type, new Set());
  listeners.get(type)!.add(fn);
}

// 由 Kotlin evaluateJavascript 调用。default export 由 main.ts 注册。
export function __onNativeEvent(msg: string): void {
  try {
    const evt = JSON.parse(msg) as GameEvent;
    const set = listeners.get(evt.type);
    if (set) for (const fn of set) fn(evt.payload);
  } catch (e) {
    console.error('[bridge] bad native event:', msg, e); // eslint-disable-line
  }
}

window.__onNativeEvent = __onNativeEvent;

/** App 渲染就绪后通知 Kotlin。 */
export function notifyGameReady(): void {
  bridgeSend('game_ready', { ts: Date.now() });
}