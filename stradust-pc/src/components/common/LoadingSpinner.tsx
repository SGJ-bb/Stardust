import { cn } from "@/lib/utils";

interface LoadingSpinnerProps {
  className?: string;
  size?: "sm" | "md" | "lg";
}

/**
 * 加载旋转器组件
 */
export function LoadingSpinner({ className, size = "md" }: LoadingSpinnerProps) {
  const sizeStyles = {
    sm: "h-4 w-4 border-2",
    md: "h-8 w-8 border-2",
    lg: "h-12 w-12 border-3",
  };

  return (
    <div
      className={cn(
        "animate-spin rounded-full border-[var(--color-border)] border-t-[var(--color-primary)]",
        sizeStyles[size],
        className
      )}
    />
  );
}
