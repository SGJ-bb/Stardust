/**
 * Live2D SDK封装
 * 使用PixiJS + pixi-live2d-display渲染Live2D模型
 */

/** Live2D模型配置 */
export interface Live2DModelConfig {
  modelPath: string;
  scale: number;
  x: number;
  y: number;
}

/** Live2D控制器接口 */
export interface ILive2DController {
  /** 加载模型 */
  loadModel(config: Live2DModelConfig): Promise<void>;
  /** 设置表情 */
  setExpression(name: string): void;
  /** 触发动作 */
  startMotion(group: string, index: number, priority: number): void;
  /** 点击交互 */
  tapModel(x: number, y: number): void;
  /** 设置缩放 */
  setModelScale(scale: number): void;
  /** 设置位置 */
  setModelPosition(x: number, y: number): void;
  /** 销毁模型 */
  destroy(): void;
}

/** 默认Live2D配置 */
export const DEFAULT_LIVE2D_CONFIG: Live2DModelConfig = {
  modelPath: "",
  scale: 0.25,
  x: 0,
  y: 0,
};

/**
 * 创建Live2D控制器
 * 使用PixiJS Application和pixi-live2d-display加载和渲染Live2D模型
 */
export function createLive2DController(
  canvas: HTMLCanvasElement,
): ILive2DController {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let app: any = null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let model: any = null;

  /** 初始化PixiJS Application */
  const initApp = async () => {
    if (app) return;
    try {
      const PIXI = await import("pixi.js");
      app = new PIXI.Application();
      await app.init({
        canvas,
        width: canvas.width,
        height: canvas.height,
        backgroundAlpha: 0,
        resizeTo: canvas.parentElement ?? undefined,
      });
    } catch (error) {
      console.error("初始化PixiJS失败:", error);
    }
  };

  return {
    async loadModel(config: Live2DModelConfig) {
      await initApp();
      if (!app) return;

      try {
        // 用变量名让 Vite 不静态分析此 import，运行时由 try-catch 兜底
        // @jannchie/pixi-live2d-display 是 pixi.js v8 兼容 fork
        const moduleName = "@jannchie/pixi-live2d-display";
        const { Live2DModel: Live2DModelClass } = await import(moduleName);

        // 移除旧模型
        if (model) {
          app.stage.removeChild(model);
          model.destroy();
          model = null;
        }

        // 加载新模型
        model = await Live2DModelClass.from(config.modelPath);
        model.scale.set(config.scale);
        model.x = config.x;
        model.y = config.y;
        app.stage.addChild(model);
      } catch (error) {
        console.error("加载Live2D模型失败:", error);
      }
    },

    setExpression(name: string) {
      if (model && typeof model.expression === "function") {
        model.expression(name);
      }
    },

    startMotion(group: string, index: number, priority: number) {
      if (model && typeof model.motion === "function") {
        model.motion(group, index, priority);
      }
    },

    tapModel(x: number, y: number) {
      if (model) {
        // 触发Live2D模型的点击区域交互
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const hitAreas = (model as any).internalModel?.hitAreas;
        if (hitAreas) {
          // 尝试触发点击交互
          if (typeof model.tap === "function") {
            model.tap(x, y);
          }
        }
      }
    },

    setModelScale(scale: number) {
      if (model) {
        model.scale.set(scale);
      }
    },

    setModelPosition(x: number, y: number) {
      if (model) {
        model.x = x;
        model.y = y;
      }
    },

    destroy() {
      if (model) {
        app?.stage.removeChild(model);
        model.destroy();
        model = null;
      }
      if (app) {
        app.destroy(true);
        app = null;
      }
    },
  };
}
