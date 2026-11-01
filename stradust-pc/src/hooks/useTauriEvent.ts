import { useEffect } from "react";
import { isTauri } from "@/lib/tauri";
import type { TauriEventType } from "@/types/tauri";

/**
 * 事件监听 Hook（双通道）
 * 1. 统一引擎总线：sd:{event}（CustomEvent，浏览器与 Tauri 均生效——引擎跑在前端）
 * 2. Tauri 原生事件：仅桌面环境额外监听（兼容 Rust 侧旧链路）
 */
export function useTauriEvent<T = unknown>(
  event: TauriEventType | string,
  handler: (payload: T) => void
) {
  useEffect(() => {
    let unlistenTauri: (() => void) | undefined;
    let disposed = false;

    // 通道1：引擎总线（sd: 前缀 + 兼容旧 mock- 前缀）
    const busTypes = [`sd:${event}`, `mock-${event}`];
    const onBus = (e: Event) => handler((e as CustomEvent).detail as T);
    for (const t of busTypes) window.addEventListener(t, onBus);

    // 通道2：Tauri 原生事件
    if (isTauri()) {
      import("@tauri-apps/api/event").then(({ listen }) =>
        listen<T>(event, (e) => handler(e.payload)),
      ).then((fn) => {
        if (disposed) fn();
        else unlistenTauri = fn;
      }).catch((error) => {
        console.error("Tauri事件监听初始化失败:", error);
      });
    }

    return () => {
      disposed = true;
      for (const t of busTypes) window.removeEventListener(t, onBus);
      unlistenTauri?.();
    };
  }, [event, handler]);
}

/**
 * 一次性事件监听Hook
 */
export function useTauriEventOnce<T = unknown>(
  event: TauriEventType | string,
  handler: (payload: T) => void
) {
  useEffect(() => {
    let unlistenTauri: (() => void) | undefined;
    let fired = false;
    let disposed = false;

    const once = (detail: unknown) => {
      if (fired) return;
      fired = true;
      handler(detail as T);
    };
    const busTypes = [`sd:${event}`, `mock-${event}`];
    const onBus = (e: Event) => {
      once((e as CustomEvent).detail);
      for (const t of busTypes) window.removeEventListener(t, onBus);
    };
    for (const t of busTypes) window.addEventListener(t, onBus);

    if (isTauri()) {
      import("@tauri-apps/api/event").then(({ listen }) =>
        listen<T>(event, (e) => {
          once(e.payload);
          unlistenTauri?.();
        }),
      ).then((fn) => {
        if (disposed) fn();
        else unlistenTauri = fn;
      }).catch(() => undefined);
    }

    return () => {
      disposed = true;
      for (const t of busTypes) window.removeEventListener(t, onBus);
      unlistenTauri?.();
    };
  }, [event, handler]);
}
