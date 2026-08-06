import { useEffect } from "react";
import { isTauri } from "@/lib/tauri";
import type { TauriEventType } from "@/types/tauri";

/**
 * Tauri事件监听封装Hook
 * 非Tauri环境下安全跳过
 * @param event 事件名称
 * @param handler 事件处理函数
 */
export function useTauriEvent<T = unknown>(
  event: TauriEventType | string,
  handler: (payload: T) => void
) {
  useEffect(() => {
    if (isTauri()) {
      // Tauri 环境：使用原生事件监听
      let unlisten: (() => void) | undefined;

      import("@tauri-apps/api/event").then(({ listen }) => {
        listen<T>(event, (e) => {
          handler(e.payload);
        }).then((fn) => {
          unlisten = fn;
        });
      }).catch((error) => {
        console.error("Tauri事件监听初始化失败:", error);
      });

      return () => {
        unlisten?.();
      };
    } else {
      // Web 环境使用自定义事件作为回退（用于开发预览）
      const customEventType = `mock-${event}`;

      const handleCustomEvent = (e: Event) => {
        const detail = (e as CustomEvent).detail;
        handler(detail as T);
      };

      window.addEventListener(customEventType, handleCustomEvent);

      return () => {
        window.removeEventListener(customEventType, handleCustomEvent);
      };
    }
  }, [event, handler]);
}

/**
 * 一次性事件监听Hook
 * 非Tauri环境下安全跳过
 */
export function useTauriEventOnce<T = unknown>(
  event: TauriEventType | string,
  handler: (payload: T) => void
) {
  useEffect(() => {
    if (isTauri()) {
      // Tauri 环境：使用原生事件监听（一次性）
      let unlisten: (() => void) | undefined;

      import("@tauri-apps/api/event").then(({ listen }) => {
        listen<T>(event, (e) => {
          handler(e.payload);
          unlisten?.();
        }).then((fn) => {
          unlisten = fn;
        });
      }).catch((error) => {
        console.error("Tauri事件监听初始化失败:", error);
      });

      return () => {
        unlisten?.();
      };
    } else {
      // Web 环境使用自定义事件作为回退（用于开发预览）
      const customEventType = `mock-${event}`;

      const handleCustomEvent = (e: Event) => {
        const detail = (e as CustomEvent).detail;
        handler(detail as T);
        // 一次性：触发后自动移除
        window.removeEventListener(customEventType, handleCustomEvent);
      };

      window.addEventListener(customEventType, handleCustomEvent);

      return () => {
        window.removeEventListener(customEventType, handleCustomEvent);
      };
    }
  }, [event, handler]);
}
