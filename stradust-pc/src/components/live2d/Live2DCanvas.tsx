import { useRef, useEffect, useCallback, useState } from "react";
import { cn } from "@/lib/utils";
import { ImageOff } from "lucide-react";
import {
  loadZones,
  hitTestByScreen,
  DEFAULT_ZONE_REACTION,
  type PetZone,
  type PetZoneSet,
} from "@/lib/petzones";
import { startGazeFollow } from "@/lib/petgaze";
import { enableModelDrag } from "@/lib/petdrag";
import { PetDock, type MotionGroupInfo } from "@/components/live2d/PetDock";

interface Live2DCanvasProps {
  /** 模型路径 */
  modelPath?: string;
  /** 缩放比例 */
  scale?: number;
  /** 透明度 */
  opacity?: number;
  /** 命中区 JSON（pet_zones_gen.py 的产物），不传则关闭点击部位识别 */
  zonesPath?: string;
  /** 视线跟随指针（默认开） */
  gaze?: boolean;
  /** 可拖拽 + 松手回弹（默认关；桌宠场景开） */
  draggable?: boolean;
  /** 显示表情/动作/特效交互坞 */
  dock?: boolean;
  /** 命中回调（业务侧可覆盖默认反馈） */
  onHit?: (zone: PetZone) => void;
  className?: string;
}

/**
 * Live2D渲染画布组件
 * 使用PixiJS + pixi-live2d-display 渲染 Live2D 模型。
 *
 * 已整合原 Electron 桌宠（image2psd2live/pet）的能力：
 *   · 视线跟随指针（lib/petgaze）
 *   · 部位命中（lib/petzones：PSD 图层当交互区）
 *   · 画布内拖拽 + 惯性回弹（lib/petdrag）
 *   · 表情 / 动作 / 序列帧特效面板（PetDock + lib/petfx）
 *   · 呼吸/眨眼/口型/物理由 Cubism 运行时自带
 *
 * 浏览器环境安全降级，不会导致页面崩溃。
 */
export function Live2DCanvas({
  modelPath,
  scale = 0.25,
  opacity = 1.0,
  zonesPath,
  gaze = true,
  draggable = false,
  dock = false,
  onHit,
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
    setExpression: (name: string) => void;
    startMotion: (group: string, index?: number) => void;
    playFrames: (name: string, mode?: string) => void;
    getBounds: () => { x: number; y: number; width: number; height: number } | null;
    destroy: () => void;
  } | null>(null);
  const zonesRef = useRef<PetZoneSet | null>(null);
  const cleanupFxRef = useRef<Array<() => void>>([]);

  const [loadError, setLoadError] = useState(false);
  const [modelLoadError, setModelLoadError] = useState(false);
  const [expressions, setExpressions] = useState<string[]>([]);
  const [motions, setMotions] = useState<MotionGroupInfo[]>([]);
  const [frames, setFrames] = useState<string[]>([]);

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

              // 释放上一模型挂的跟随/拖拽
              cleanupFxRef.current.forEach((fn) => {
                try {
                  fn();
                } catch {
                  /* noop */
                }
              });
              cleanupFxRef.current = [];

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

              // ---- 模型自带的表情 / 动作清单（换模型自动更新）----
              try {
                const settings = (loadedModel as any).internalModel?.settings;
                const exprNames: string[] = (settings?.expressions || [])
                  .map((e: any) => e?.Name)
                  .filter(Boolean);
                const motionGroups: MotionGroupInfo[] = Object.entries(
                  settings?.motions || {},
                ).map(([g, arr]: [string, any]) => ({
                  group: g,
                  count: Array.isArray(arr) ? arr.length : 1,
                }));
                if (!destroyed) {
                  setExpressions(exprNames);
                  setMotions(motionGroups);
                }
              } catch {
                /* 老模型可能没有 settings */
              }

              // ---- 视线跟随 ----
              if (gaze) {
                cleanupFxRef.current.push(
                  startGazeFollow({
                    getModel: () => model,
                    getBounds: () => {
                      try {
                        const b = (model as any)?.getBounds?.();
                        return b
                          ? { x: b.x, y: b.y, width: b.width, height: b.height }
                          : null;
                      } catch {
                        return null;
                      }
                    },
                  }),
                );
              }

              // ---- 拖拽 + 惯性回弹 ----
              if (draggable && canvasRef.current) {
                cleanupFxRef.current.push(
                  enableModelDrag({
                    model,
                    canvas: canvasRef.current,
                    options: { home: { x: config.x, y: config.y } },
                  }),
                );
              }

              // 帧动画层：Live2D 管微动，序列帧管一次性动作/特效（见 lib/petfx.ts）
              try {
                const { initPetFx, petFramesInfo } = await import("@/lib/petfx");
                if (!destroyed) await initPetFx(app as any, loadedModel as any);
                // 已加载的序列帧清单（public/frames 里实际存在的，不写死）
                const info = petFramesInfo();
                if (!destroyed && info.list?.length) setFrames(info.list);
              } catch (e) {
                console.warn("petfx 初始化失败:", e);
              }
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
          setExpression(name: string) {
            try { (model as any)?.expression?.(name); } catch { /* 模型无该表情 */ }
          },
          startMotion(group: string, index = 0) {
            try { (model as any)?.motion?.(group, index); } catch { /* 模型无该动作 */ }
          },
          playFrames(name: string, mode = "overlay") {
            import("@/lib/petfx")
              .then((m) => m.playPetFrames(name, mode))
              .catch(() => {});
          },
          getBounds() {
            try {
              const b = (model as any)?.getBounds?.();
              return b ? { x: b.x, y: b.y, width: b.width, height: b.height } : null;
            } catch {
              return null;
            }
          },
          destroy() {
            cleanupFxRef.current.forEach((fn) => {
              try {
                fn();
              } catch {
                /* noop */
              }
            });
            cleanupFxRef.current = [];
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
  }, [draggable, gaze]);

  /** 加载命中区（与模型解耦，切换模型不用重载） */
  useEffect(() => {
    let alive = true;
    if (!zonesPath) {
      zonesRef.current = null;
      return;
    }
    loadZones(zonesPath).then((zs) => {
      if (alive) zonesRef.current = zs;
    });
    return () => {
      alive = false;
    };
  }, [zonesPath]);

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

  /** 响应引擎 Live2D 控制指令（表情/动作/切模型，经 lib/tauri.controlLive2D 总线） */
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent).detail as
        | { scope?: string; action?: string; expressionName?: string; motionGroup?: string; motionIndex?: number; modelPath?: string }
        | undefined;
      if (detail?.scope !== "live2d") return;
      if (detail.action === "expression" && detail.expressionName) {
        controllerRef.current?.setExpression(detail.expressionName);
      } else if (detail.action === "motion" && detail.motionGroup) {
        controllerRef.current?.startMotion(detail.motionGroup, detail.motionIndex ?? 0);
      } else if (detail.action === "load" && detail.modelPath) {
        controllerRef.current?.loadModel({ modelPath: detail.modelPath, scale, x: 0, y: 0 });
      }
    };
    window.addEventListener("sd:data-changed", handler);
    return () => window.removeEventListener("sd:data-changed", handler);
  }, [scale]);

  /** 点击交互：先判命中区，再走默认反馈（表情/动作/特效） */
  const handleCanvasPointerUp = useCallback(
    (e: React.PointerEvent<HTMLCanvasElement>) => {
      const zones = zonesRef.current;
      const ctrl = controllerRef.current;
      if (!zones || !ctrl) return;
      const zone = hitTestByScreen(zones, e.clientX, e.clientY, ctrl.getBounds());
      if (!zone) return;
      onHit?.(zone);
      const r =
        DEFAULT_ZONE_REACTION[zone.kind] || DEFAULT_ZONE_REACTION[zone.name];
      if (!r) return;
      if (r.frames) ctrl.playFrames(r.frames);
      if (r.expression) ctrl.setExpression(r.expression);
      if (r.motion) ctrl.startMotion(r.motion);
    },
    [onHit],
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
      <div className="relative">
        <canvas
          ref={canvasRef}
          width={300}
          height={400}
          onPointerUp={handleCanvasPointerUp}
          className={cn(
            "block",
            draggable ? "cursor-grab active:cursor-grabbing" : "cursor-pointer",
          )}
        />
        {dock && (expressions.length > 0 || motions.length > 0) && (
          <div className="absolute left-0 bottom-2 -translate-x-[calc(100%+10px)]">
            <PetDock
              expressions={expressions}
              motions={motions}
              frames={frames}
              onExpression={(n) => controllerRef.current?.setExpression(n)}
              onMotion={(g) => controllerRef.current?.startMotion(g)}
              onFrames={(n) => controllerRef.current?.playFrames(n)}
            />
          </div>
        )}
      </div>
    </div>
  );
}
