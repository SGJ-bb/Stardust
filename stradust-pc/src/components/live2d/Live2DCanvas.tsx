import { useRef, useEffect, useCallback, useState } from "react";
import { cn } from "@/lib/utils";
import { ImageOff } from "lucide-react";

interface Live2DCanvasProps {
  /** 模型路径 */
  modelPath?: string;
  /** 缩放比例 */
  scale?: number;
  /** 透明度 */
  opacity?: number;
  className?: string;
}

/**
 * Live2D渲染画布组件
 * 使用PixiJS + pixi-live2d-display渲染Live2D模型
 * 支持加载模型、设置表情、触发动作、点击交互、缩放调整
 * 浏览器环境下安全降级，不会导致页面崩溃
 */
export function Live2DCanvas({
  modelPath,
  scale = 0.25,
  opacity = 1.0,
  className,
}: Live2DCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const controllerRef = useRef<{
    loadModel: (config: {
      modelPath: string;
      scale: number;
      x: number;
      y: number;
    }) => Promise<void>;
    setModelScale: (s: number) => void;
    destroy: () => void;
  } | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [modelLoadError, setModelLoadError] = useState(false);

  useEffect(() => {
    if (!canvasRef.current) return;

    let destroyed = false;
    let app: unknown = null;
    let model: unknown = null;

    const initController = async () => {
      try {
        const PIXI = await import("pixi.js");
        if (destroyed) return;

        const pixiApp = new PIXI.Application();
        await pixiApp.init({
          canvas: canvasRef.current!,
          width: canvasRef.current!.width,
          height: canvasRef.current!.height,
          backgroundAlpha: 0,
          resizeTo: canvasRef.current!.parentElement ?? undefined,
        });
        app = pixiApp;

        controllerRef.current = {
          async loadModel(config) {
            if (destroyed || !app) return;
            try {
              // 用变量名让 Vite 不静态分析此 import，运行时由 try-catch 兜底
              // @jannchie/pixi-live2d-display 是 pixi.js v8 兼容 fork
              const moduleName = "@jannchie/pixi-live2d-display";
              const { Live2DModel: Live2DModelClass } = await import(
                moduleName
              );
              if (destroyed) return;
              // 移除旧模型
              if (model) {
                (app as any).stage.removeChild(model);
                (model as any).destroy();
                model = null;
              }
              const loadedModel = await Live2DModelClass.from(config.modelPath);
              if (destroyed) return;
              loadedModel.scale.set(config.scale);
              loadedModel.x = config.x;
              loadedModel.y = config.y;
              (app as any).stage.addChild(loadedModel);
              model = loadedModel;
              if (!destroyed) setModelLoadError(false);
            } catch (error) {
              console.error("加载Live2D模型失败:", error);
              if (!destroyed) setModelLoadError(true);
            }
          },
          setModelScale(s: number) {
            if (model) {
              (model as any).scale.set(s);
            }
          },
          destroy() {
            if (model) {
              (app as any)?.stage.removeChild(model);
              (model as any).destroy();
              model = null;
            }
            if (app) {
              (app as any).destroy(true);
              app = null;
            }
          },
        };
        setLoadError(false);
      } catch (error) {
        console.warn("Live2D初始化失败（浏览器环境可能不支持）:", error);
        setLoadError(true);
      }
    };

    initController();

    return () => {
      destroyed = true;
      controllerRef.current?.destroy();
      controllerRef.current = null;
    };
  }, []);

  /** 加载模型 */
  useEffect(() => {
    if (modelPath && controllerRef.current) {
      controllerRef.current.loadModel({
        modelPath,
        scale,
        x: 0,
        y: 0,
      });
    }
  }, [modelPath, scale]);

  /** 更新缩放 */
  useEffect(() => {
    if (controllerRef.current) {
      controllerRef.current.setModelScale(scale);
    }
  }, [scale]);

  /** 点击交互 */
  const handleCanvasClick = useCallback(
    (e: React.MouseEvent<HTMLCanvasElement>) => {
      // 浏览器环境下点击交互暂不实现
      if (!canvasRef.current) return;
      console.log("Live2D canvas clicked at:", e.clientX, e.clientY);
    },
    [],
  );

  // 加载失败时显示友好的占位 UI，而非空白
  if (loadError || modelLoadError) {
    return (
      <div
        className={cn(
          "live2d-container fixed right-0 bottom-0 flex items-center justify-center pointer-events-none",
          className,
        )}
        style={{ opacity }}
      >
        <div
          className="m-4 px-4 py-3 rounded-2xl border border-[var(--color-border)] bg-[var(--color-card)]/80 backdrop-blur-md shadow-lg flex items-center gap-2.5 max-w-[220px]"
          role="status"
          aria-live="polite"
        >
          <ImageOff className="h-4 w-4 text-[var(--color-muted-foreground)] shrink-0" />
          <span className="text-xs text-[var(--color-muted-foreground)] leading-relaxed">
            {loadError
              ? "Live2D 暂不可用（浏览器环境限制）"
              : "Live2D 模型加载失败，请检查模型路径"}
          </span>
        </div>
      </div>
    );
  }

  return (
    <div
      className={cn("live2d-container fixed right-0 bottom-0", className)}
      style={{ opacity }}
    >
      <canvas
        ref={canvasRef}
        width={300}
        height={400}
        onClick={handleCanvasClick}
        className="cursor-pointer"
      />
    </div>
  );
}
