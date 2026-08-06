import {
  useRef,
  useEffect,
  useCallback,
  forwardRef,
  useImperativeHandle,
} from "react";
import { PixelAnimationEngine } from "@/lib/pixelpet/engine";
import { PixelRenderer } from "@/lib/pixelpet/renderer";
import type { PetAction, PetMode } from "@/lib/pixelpet/types";

interface PixelPetCanvasProps {
  /** 宠物的动作列表 */
  actions: PetAction[];
  /** 当前宠物模式 */
  petMode: PetMode;
  /** 渲染缩放倍数 */
  scale?: number;
  /** 自定义className */
  className?: string;
  /** 动作切换回调 */
  onActionChange?: (actionName: string) => void;
  /** 单击回调 */
  onClick?: () => void;
  /** 双击回调 */
  onDoubleClick?: () => void;
}

export interface PixelPetCanvasHandle {
  /** 切换动作 */
  playAction: (actionId: string) => void;
  /** 触发互动动作 */
  triggerInteraction: (actionName: string) => boolean;
  /** 获取当前引擎实例 */
  getEngine: () => PixelAnimationEngine | null;
}

/**
 * 像素宠物渲染画布组件
 *
 * - Canvas 2D 像素完美渲染
 * - 集成 AnimationEngine 驱动动画循环
 * - 支持单击/双击交互
 * - 自动加载帧图并缓存
 */
const PixelPetCanvas = forwardRef<PixelPetCanvasHandle, PixelPetCanvasProps>(
  (
    {
      actions,
      petMode,
      scale = 3,
      className,
      onActionChange,
      onClick,
      onDoubleClick,
    },
    ref,
  ) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const engineRef = useRef<PixelAnimationEngine | null>(null);
    const rendererRef = useRef<PixelRenderer | null>(null);
    // 帧图片缓存 Map<imagePath, HTMLImageElement>
    const imageCacheRef = useRef<Map<string, HTMLImageElement>>(new Map());
    const lastClickTimeRef = useRef<number>(0);

    // ═══ 初始化 Engine + Renderer ═══
    useEffect(() => {
      if (!canvasRef.current) return;

      const engine = new PixelAnimationEngine();
      const renderer = new PixelRenderer();
      renderer.bindCanvas(canvasRef.current);

      engineRef.current = engine;
      rendererRef.current = renderer;

      // 监听引擎的帧回调 → 触发渲染
      engine.onFrame((frame) => {
        if (!frame || !renderer) return;
        const cached = imageCacheRef.current.get(frame.imagePath);
        if (cached) {
          renderer.render();
        } else {
          // 加载并缓存图片
          const img = new Image();
          img.onload = () => {
            imageCacheRef.current.set(frame.imagePath, img);
            renderer.setImage(img);
            renderer.render();
          };
          img.onerror = () => {
            console.warn(`[PixelPet] Failed to load frame: ${frame.imagePath}`);
          };
          img.src = frame.imagePath;
        }
      });

      // 监听动作变化
      engine.onActionChange((_, prevId) => {
        const action = engine.getCurrentAction();
        if (action) {
          onActionChange?.(action.name);
        }
      });

      return () => {
        engine.destroy();
        renderer.destroy();
        engineRef.current = null;
        rendererRef.current = null;
      };
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // ═══ 当 actions 变化时，重新注册到引擎 ═══
    useEffect(() => {
      const engine = engineRef.current;
      if (!engine || petMode !== "pixel") return;

      // 清除旧缓存（仅清除不再使用的）
      const newPaths = new Set<string>();
      for (const action of actions) {
        for (const frame of action.frames) {
          newPaths.add(frame.imagePath);
        }
      }
      for (const [path] of imageCacheRef.current) {
        if (!newPaths.has(path)) {
          imageCacheRef.current.delete(path);
        }
      }

      engine.registerActions(actions);

      // 如果正在播放则重启
      if (engine.getIsPlaying()) {
        engine.stop();
        engine.play();
      } else {
        engine.play();
      }

      // 更新渲染器配置
      if (actions.length > 0 && rendererRef.current) {
        const firstAction = actions[0];
        rendererRef.current.setConfig({
          scale,
          spriteWidth: 64, // 默认值，实际以图片为准
          spriteHeight: 64,
        });
      }
    }, [actions, petMode, scale]);

    // ═══ 暴露方法给父组件 ═══
    useImperativeHandle(
      ref,
      () => ({
        playAction: (actionId: string) => {
          engineRef.current?.playAction(actionId);
        },
        triggerInteraction: (actionName: string) => {
          return engineRef.current?.triggerInteraction(actionName) ?? false;
        },
        getEngine: () => engineRef.current,
      }),
      [],
    );

    // ═══ 点击处理：区分单击/双击 ═══
    const handleCanvasClick = useCallback(() => {
      const now = Date.now();
      if (now - lastClickTimeRef.current < 300) {
        // 双击
        onDoubleClick?.();
      } else {
        // 单击（延迟判断是否为双击）
        setTimeout(() => {
          if (Date.now() - lastClickTimeRef.current >= 280) {
            onClick?.();
          }
        }, 300);
      }
      lastClickTimeRef.current = now;
    }, [onClick, onDoubleClick]);

    // 非 pixel 模式时不渲染
    if (petMode !== "pixel") {
      return null;
    }

    return (
      <canvas
        ref={canvasRef}
        width={192}
        height={192}
        className={className}
        style={{
          width: "100%",
          height: "100%",
          cursor: "pointer",
        }}
        onClick={handleCanvasClick}
      />
    );
  },
);

PixelPetCanvas.displayName = "PixelPetCanvas";
export { PixelPetCanvas };
export default PixelPetCanvas;
