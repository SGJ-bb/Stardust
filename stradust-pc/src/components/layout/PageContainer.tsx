import { cn } from "@/lib/utils";
import { motion } from "framer-motion";

interface PageContainerProps {
  children: React.ReactNode;
  className?: string;
  padded?: boolean;
  /** 页面标题（可选） */
  title?: string;
  /** 标题下方的说明文字（可选） */
  description?: string;
}

/**
 * 页面容器 - 带入场动画
 */
export function PageContainer({ children, className, padded = true, title, description }: PageContainerProps) {
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
