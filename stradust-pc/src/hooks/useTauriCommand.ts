import { useCallback } from "react";
import { isTauri } from "@/lib/tauri";
import * as tauriCommands from "@/lib/tauri";

/**
 * Tauri IPC调用封装Hook
 * 提供类型安全的invoke调用，统一错误处理
 * 非Tauri环境下安全降级
 */
export function useTauriCommand() {
  /** 通用命令调用 */
  const call = useCallback(async <T>(command: string, args?: Record<string, unknown>): Promise<T> => {
    if (!isTauri()) {
      console.warn(`[Mock] useTauriCommand.call "${command}" 在浏览器环境中被调用`);
      return undefined as T;
    }
    try {
      const { invoke } = await import("@tauri-apps/api/core");
      return await invoke<T>(command, args);
    } catch (error) {
      console.error(`Tauri命令调用失败 [${command}]:`, error);
      throw error;
    }
  }, []);

  return {
    call,
    ...tauriCommands,
  };
}
