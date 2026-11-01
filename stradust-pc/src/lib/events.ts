/**
 * 统一事件总线：引擎 → UI 的流式/状态事件通道。
 * 双模式一致：浏览器与 Tauri WebView 都走 window CustomEvent，
 * 不再依赖 Rust "chat-stream" 事件（保留兼容：Tauri 下若收到后端事件也转发进总线）。
 */

export type BusEventType =
  | "chat-stream" // { personaId, sessionId, type: "token"|"done"|"error"|"tool_call"|"tool_result"|"emotion"|"action"|"proactive", content?, toolName?, toolCallId? }
  | "voice-state"
  | "proactive-message"
  | "achievement-unlocked"
  | "milestone-achieved"
  | "diary-generated"
  | "moment-generated"
  | "world-tick"
  | "ilink-message"
  | "alarm-fired"
  | "tool-confirm"
  | "wake-fired"
  | "data-changed";

export interface ChatStreamEvent {
  personaId?: string;
  sessionId?: string;
  type: "token" | "done" | "error" | "tool_call" | "tool_result" | "emotion" | "action" | "start" | "proactive";
  content?: string;
  toolName?: string;
  toolCallId?: string;
}

export function emit(type: BusEventType, detail?: unknown): void {
  window.dispatchEvent(new CustomEvent(`sd:${type}`, { detail }));
}

export function on<T = unknown>(type: BusEventType, handler: (detail: T) => void): () => void {
  const listener = (e: Event) => handler((e as CustomEvent<T>).detail);
  window.addEventListener(`sd:${type}`, listener);
  return () => window.removeEventListener(`sd:${type}`, listener);
}
