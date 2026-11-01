import Phaser from 'phaser';

/**
 * AI 预烘焙像素资产加载器(路线A)。
 * AI 生成的图片是纯白底 jpg, 没有透明通道。本模块加载后用代码逐像素抠白,
 * 生成真正的透明纹理, 供 Phaser 直接作角色/物品 sprite 使用。
 * 区别:
 *  - 程序化绘制(characters.ts): 运行时由 fillRect 现画 → 天花板低、外观粗糙(已证明)。
 *  - AI 预烘焙(本模块): 美术一次生成 PNG 级观感, 运行时仅加载+抠白 → 零 API、零成本、观感接近星露谷。
 * 既支持内置资产(预烘焙, 用户零 API), 也为后续"用户自定义世界观"用任意图片路径预留入口。
 */

/** 抠白阈值: RGB 均 >= 该值(0~255)视为背景。经端到端对比: 0.3 会残留抗锯齿红边, 0.5(127) 对像素画最干净。 */
const WHITE_THRESHOLD = 127;

// 保持对外接口兼容: 旧 WHITE_TOLERANCE 语义废弃, 收敛到阈值换算(127/255≈0.5)。
const WHITE_TOLERANCE = 0.5;

/**
 * 在指定 scene 的 preload 阶段加载一张 AI 像素图。
 * 纹理 key 即其 URL(与后续 keyImageToTransparent 的 srcKey 一致)。
 */
export function preloadPixelAsset(scene: Phaser.Scene, url: string): void {
  scene.load.image(url, url);
}

/**
 * 把一张已生成的像素纹理执行"抠白 + 收紧包围盒 + 居中", 生成新纹理。
 * 返回新纹理 key。
 */
export function keyImageToTransparent(
  scene: Phaser.Scene,
  srcKey: string,
  outKey: string,
  tolerance = WHITE_TOLERANCE
): string {
  const tex = scene.textures.get(srcKey);
  if (!tex || !tex.getSourceImage()) {
    console.warn(`[pixelAssets] texture not found: ${srcKey}`);
    return srcKey;
  }
  // 原图可能不是像素格式, 先归一化到 canvas
  const img = tex.getSourceImage() as HTMLImageElement;
  const c = document.createElement('canvas');
  c.width = img.naturalWidth || img.width;
  c.height = img.naturalHeight || img.height;
  const g = c.getContext('2d');
  if (!g) return srcKey;
  g.drawImage(img, 0, 0);

  const data = g.getImageData(0, 0, c.width, c.height);
  const d = data.data;
  const tol = tolerance * 255;
  let minX = c.width, minY = c.height, maxX = 0, maxY = 0;
  let hasPix = false;
  for (let y = 0; y < c.height; y++) {
    for (let x = 0; x < c.width; x++) {
      const i = (y * c.width + x) * 4;
      const r = d[i], gg = d[i + 1], b = d[i + 2];
      // 接近白色 → 透明
      if (r >= tol && gg >= tol && b >= tol) {
        d[i + 3] = 0;
      } else {
        hasPix = true;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  g.putImageData(data, 0, 0);

  if (!hasPix) {
    console.warn(`[pixelAssets] no opaque pixels in ${srcKey}`);
    return srcKey;
  }

  // 裁剪到内容包围盒
  const bw = maxX - minX + 1;
  const bh = maxY - minY + 1;
  const trim = document.createElement('canvas');
  trim.width = bw;
  trim.height = bh;
  const tg = trim.getContext('2d');
  if (!tg) return srcKey;
  tg.drawImage(c, minX, minY, bw, bh, 0, 0, bw, bh);

  // 生成 Phaser 纹理
  const key = outKey;
  scene.textures.addCanvas(key, trim);
  console.log(`[pixelAssets] keyImageToTransparent: ${srcKey} -> ${key} (frame ${bw}x${bh})`);
  return key;
}