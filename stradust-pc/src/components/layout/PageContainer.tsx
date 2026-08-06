import { cn } from "@/lib/utils";
import { motion } from "framer-motion";

interface PageContainerProps {
  children: React.ReactNode;
  className?: string;
  padded?: boolean;
}

/**
 * 页面容器 - 带入场动画
 */
export function PageContainer({ children, className, padded = true }: PageContainerProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] as [number, number, number, number] }}
      className={cn(
        "flex h-full flex-col overflow-hidden",
        padded && "p-6",
        className
      )}
    >
      {children}
    </motion.div>
  );
}
