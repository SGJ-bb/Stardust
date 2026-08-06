/**
 * 像素精灵渲染器 (纯 TS，平台无关)
 *
 * 使用 Canvas 2D API 进行像素完美渲染:
 * - imageSmoothingEnabled = false (无滤波)
 * - 支持任意缩放倍数
 * - 自动居中绘制
 * - 双缓冲支持 (可选)
 */

import type { PixelRenderConfig } from './types';
import { DEFAULT_RENDER_CONFIG } from './types';

export class PixelRenderer {
  private canvas: HTMLCanvasElement | null = null;
  private ctx: CanvasRenderingContext2D | null = null;
  private config: PixelRenderConfig = { ...DEFAULT_RENDER_CONFIG };
  private currentImage: HTMLImageElement | null = null;
  private offscreenCanvas: OffscreenCanvas | null = null;
  private offscreenCtx: OffscreenCanvasRenderingContext2D | null = null;

  /** 绑定 Canvas 元素 */
  bindCanvas(canvas: HTMLCanvasElement): void {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d')!;
    this.ctx.imageSmoothingEnabled = false;
  }

  /** 解绑 Canvas */
  unbindCanvas(): void {
    this.canvas = null;
    this.ctx = null;
  }

  /** 更新渲染配置 */
  setConfig(config: Partial<PixelRenderConfig>): void {
    Object.assign(this.config, config);
  }

  getConfig(): Readonly<PixelRenderConfig> {
    return { ...this.config };
  }

  /** 设置当前要绘制的帧图像 */
  setImage(img: HTMLImageElement): void {
    this.currentImage = img;
  }

  /** 渲染一帧到 Canvas */
  render(): boolean {
    if (!this.ctx || !this.canvas || !this.currentImage) return false;

    const { ctx, canvas, config, currentImage } = this;
    const { spriteWidth, spriteHeight, scale } = config;

    // 清空画布
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // 计算居中位置
    const drawW = (currentImage.naturalWidth || spriteWidth) * scale;
    const drawH = (currentImage.naturalHeight || spriteHeight) * scale;
    const x = (canvas.width - drawW) / 2;
    const y = (canvas.height - drawH) / 2;

    // 像素完美缩放绘制
    ctx.drawImage(
      currentImage,
      0, 0,
      currentImage.naturalWidth || spriteWidth,
      currentImage.naturalHeight || spriteHeight,
      Math.round(x), Math.round(y),
      Math.round(drawW), Math.round(drawH),
    );

    return true;
  }

  /** 渲染指定图像 (不依赖内部 currentImage) */
  renderImage(img: HTMLImageElement): boolean {
    this.setImage(img);
    return this.render();
  }

  /** 启用离屏缓冲 (减少闪烁) */
  enableOffscreenBuffer(width: number, height: number): void {
    this.offscreenCanvas = new OffscreenCanvas(width, height);
    this.offscreenCtx = this.offscreenCanvas.getContext('2d')!;
    this.offscreenCtx!.imageSmoothingEnabled = false;
  }

  /** 禁用离屏缓冲 */
  disableOffscreenBuffer(): void {
    this.offscreenCanvas = null;
    this.offscreenCtx = null;
  }

  /** 调整画布尺寸 */
  resize(width: number, height: number): void {
    if (this.canvas) {
      this.canvas.width = width;
      this.canvas.height = height;
    }
  }

  /** 清空画布 */
  clear(): void {
    if (this.ctx && this.canvas) {
      this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    }
  }

  /** 销毁渲染器 */
  destroy(): void {
    this.unbindCanvas();
    this.currentImage = null;
    this.disableOffscreenBuffer();
  }
}
