import { useEffect, useCallback } from "react";

/**
 * 快捷键钩子
 * @param keyCombination 快捷键组合，如 "Ctrl+Shift+S"
 * @param callback 回调函数
 * @param options 配置选项
 */
export function useKeyboard(
  keyCombination: string,
  callback: () => void,
  options: { preventDefault?: boolean } = {}
) {
  const { preventDefault = true } = options;

  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      const keys = keyCombination.toLowerCase().split("+");
      const ctrlRequired = keys.includes("ctrl");
      const shiftRequired = keys.includes("shift");
      const altRequired = keys.includes("alt");
      const metaRequired = keys.includes("meta");

      const mainKey = keys.find(
        (k) => !["ctrl", "shift", "alt", "meta"].includes(k)
      );

      // macOS上Ctrl键应匹配metaKey（Command键），同时支持Ctrl键
      const isMac = navigator.platform.toUpperCase().includes("MAC");
      const ctrlMatch = ctrlRequired
        ? isMac
          ? event.metaKey || event.ctrlKey
          : event.ctrlKey
        : true;
      const shiftMatch = shiftRequired ? event.shiftKey : true;
      const altMatch = altRequired ? event.altKey : true;
      const metaMatch = metaRequired ? event.metaKey : true;
      const keyMatch = mainKey ? event.key.toLowerCase() === mainKey : true;

      if (ctrlMatch && shiftMatch && altMatch && metaMatch && keyMatch) {
        if (preventDefault) {
          event.preventDefault();
        }
        callback();
      }
    },
    [keyCombination, callback, preventDefault]
  );

  useEffect(() => {
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [handleKeyDown]);
}
