import * as React from "react";
import { cn } from "@/lib/utils";
import { X } from "lucide-react";

/** Toast变体 */
export type ToastVariant = "default" | "success" | "error" | "warning";

interface ToastProps {
  id: string;
  title: string;
  description?: string;
  variant?: ToastVariant;
  duration?: number;
}

interface ToastState {
  toasts: ToastProps[];
}

const variantStyles: Record<ToastVariant, string> = {
  default: "border-[var(--color-border)] bg-[var(--color-card)] text-[var(--color-card-foreground)]",
  success: "border-green-500 bg-green-50 text-green-900",
  error: "border-red-500 bg-red-50 text-red-900",
  warning: "border-yellow-500 bg-yellow-50 text-yellow-900",
};

/** Toast容器组件 */
function ToastContainer({ toasts, onDismiss }: { toasts: ToastProps[]; onDismiss: (id: string) => void }) {
  return (
    <div className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 max-w-md">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={cn(
            "pointer-events-auto flex items-start gap-3 rounded-[var(--app-radius)] border p-4 shadow-lg animate-slide-in-right",
            variantStyles[toast.variant ?? "default"]
          )}
        >
          <div className="flex-1">
            <p className="text-sm font-semibold">{toast.title}</p>
            {toast.description && (
              <p className="mt-1 text-xs opacity-80">{toast.description}</p>
            )}
          </div>
          <button
            onClick={() => onDismiss(toast.id)}
            className="shrink-0 rounded-sm opacity-70 hover:opacity-100"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      ))}
    </div>
  );
}

export { ToastContainer };
export type { ToastProps };
