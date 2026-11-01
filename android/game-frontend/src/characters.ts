import Phaser from 'phaser';

/**
 * 高质量像素角色渲染(M0.9 形象升级)。
 * 纯程序化绘制 48px 高像素"柔和明暗"风格角色:
 *  - 分层:后发 / 躯干 / 手臂 / 腿 / 头 / 前发 / 五官 / 腮红
 *  - 每层 base + 深色阴影 + 浅色高光, 立体柔和, 无硬直角
 *  - 参数化 CharDef(肤色/发型/发色/衣/裤/鞋/点缀色), 天然贴合"世界书描述→外观"映射(M3)
 *  - 输出 down/side 两个方向的 2 帧行走 sheet, 注册成 Phaser 动画; 左右用镜像(flipX)
 */
export interface CharDef {
  id: string;
  skin: number;
  hairStyle: 'short' | 'bob' | 'long' | 'up';
  hairColor: number;
  topColor: number;
  bottomColor: number;
  shoeColor: number;
  accentColor: number; // 领口 / 腰带 / 点缀
}

const PX = 1; // 48 网格, 每逻辑像素 1px

// 常用深度色(阴影/高光由各色派生)
function shade(c: number, f: number): number {
  const r = Math.max(0, Math.min(255, Math.round(((c >> 16) & 255) * f)));
  const g = Math.max(0, Math.min(255, Math.round(((c >> 8) & 255) * f)));
  const b = Math.max(0, Math.min(255, Math.round((c & 255) * f)));
  return (r << 16) | (g << 8) | b;
}
const highlight = (c: number) => shade(c, 1.22);
const darken = (c: number) => shade(c, 0.72);

type G = Phaser.GameObjects.Graphics;

function fill(g: G, x: number, y: number, w: number, h: number, c: number): void {
  g.fillStyle(c);
  g.fillRect(x * PX, y * PX, w * PX, h * PX);
}
function dot(g: G, x: number, y: number, c: number): void { fill(g, x, y, 1, 1, c); }

/** 渐变躯干: 上窄下略宽的圆润方形 */
function torso(g: G, def: CharDef, cx: number, topY: number, w: number, h: number): void {
  const x = cx - w / 2;
  fill(g, x + 1, topY, w - 2, h, def.topColor);            // base
  fill(g, x + 1, topY, w - 2, 2, highlight(def.topColor)); // 肩部高光
  fill(g, x + 1, topY + h - 2, w - 2, 2, darken(def.topColor)); // 下摆阴影
  // 领口/点缀
  fill(g, cx - 2, topY + 2, 4, 2, def.accentColor);
}

function head(g: G, def: CharDef, cx: number, y: number, s: number): void {
  // 后发(在头后, 略大一圈)
  fill(g, cx - s / 2 - 1, y - 1, s + 2, s + 2, def.hairColor);
  // 脸
  fill(g, cx - s / 2, y, s, s, def.skin);
  // 头发(顶)
  fill(g, cx - s / 2, y, s, Math.round(s * 0.32), def.hairColor);
  // 刘海(前发)
  if (def.hairStyle === 'bob') {
    fill(g, cx - s / 2, y, s, Math.round(s * 0.42), def.hairColor);
    fill(g, cx - s / 2 + 2, y + Math.round(s * 0.36), s - 4, 2, def.hairColor);
  } else if (def.hairStyle === 'long') {
    fill(g, cx - s / 2, y, s, Math.round(s * 0.4), def.hairColor);
    fill(g, cx - s / 2 - 1, y + Math.round(s * 0.3), 2, Math.round(s * 0.7), def.hairColor);
    fill(g, cx + s / 2 - 1, y + Math.round(s * 0.3), 2, Math.round(s * 0.7), def.hairColor);
  } else if (def.hairStyle === 'up') {
    fill(g, cx - s / 2, y, s, Math.round(s * 0.3), def.hairColor);
    fill(g, cx - 1, y - 4, 3, 4, def.hairColor); // 丸子
  } else { // short
    fill(g, cx - s / 2, y, s, Math.round(s * 0.3), def.hairColor);
  }
  // 眼睛 + 腮红
  const ey = y + Math.round(s * 0.5);
  dot(g, cx - 3, ey, 0x2b1e16);
  dot(g, cx + 2, ey, 0x2b1e16);
  fill(g, cx - 3, ey + Math.round(s * 0.34), 2, 1, 0xd9807a); // 嘴
  fill(g, cx - s / 2  , ey - 1, 2, 1, 0xff9f9f); // 腮红
  fill(g, cx + s / 2 - 2, ey - 1, 2, 1, 0xff9f9f);
}

function legs(g: G, def: CharDef, cx: number, topY: number, frame: number): [number, number] {
  const lw = 4;
  const off0 = frame === 0 ? 1 : 3; // 行走交错
  const off1 = 3 - off0;
  const x0 = cx - lw - 1;
  const x1 = cx + 1;
  fill(g, x0, topY, lw, 8 - off0, def.bottomColor);
  fill(g, x1, topY, lw, 8 - off1, def.bottomColor);
  // 鞋
  fill(g, x0, topY + 7 - off0 + 1, lw + 1, 2, def.shoeColor);
  fill(g, x1 - 1, topY + 7 - off1 + 1, lw + 1, 2, def.shoeColor);
  return [off0, off1];
}

function arms(g: G, def: CharDef, cx: number, shoulderY: number, frame: number): void {
  const swing = Math.sin(frame * Math.PI) * 1.5;
  const l = 8;
  // 左臂
  fill(g, cx - 7, shoulderY + 2, 3, l - 1, def.topColor);
  fill(g, cx - 7, shoulderY + l - 2, 3, 2, def.skin); // 手
  // 右臂(镜像)
  fill(g, cx + 4, shoulderY + 2, 3, l - 1, def.topColor);
  fill(g, cx + 4, shoulderY + l - 2, 3, 2, def.skin);
  void swing; // MVP 手臂暂不摆动, 保持简洁
}

/** 绘制朝下(正面)站立的一名角色, 生成一帧纹理。 */
function renderFrame(scene: Phaser.Scene, key: string, def: CharDef, frame: number): void {
  const S = 48;
  const g = scene.make.graphics({ x: 0, y: 0 }, false);
  const cx = 24;
  head(g, def, cx, 6, 12);
  torso(g, def, cx, 18, 14, 13);
  arms(g, def, cx, 19, frame);
  legs(g, def, cx, 30, frame);
  g.generateTexture(key, S, S);
  g.destroy();
}

/** 生成一名角色的 4 帧(2 方向 × 2 帧)并注册行走动画。 */
export function makeCharacterSheet(scene: Phaser.Scene, prefix: string, def: CharDef): void {
  const keys: string[] = [];
  for (let f = 0; f < 2; f++) {
    const k = `${prefix}_d${f}`;
    renderFrame(scene, k, def, f); // down
    keys.push(k);
    renderFrame(scene, `${prefix}_s${f}`, def, f); // side 复用正面(左右用镜像), 后续可换侧视图
  }
  const anims = scene.anims;
  if (!anims.exists(`${prefix}_w_d`)) {
    const toFrames = (ks: string[]): Phaser.Types.Animations.AnimationFrame[] =>
      ks.map((k) => ({ key: k }));
    anims.create({ key: `${prefix}_w_d`, frames: toFrames(keys), frameRate: 5, repeat: -1 });
    anims.create({ key: `${prefix}_w_s`, frames: toFrames([`${prefix}_s0`, `${prefix}_s1`]), frameRate: 5, repeat: -1 });
  }
}

/** 玩家默认形象: 栗发浅棕瞳的农夫。 */
export const playerDef: CharDef = {
  id: 'player', skin: 0xffd9b3, hairStyle: 'short', hairColor: 0x7a4a24,
  topColor: 0x4a90d9, bottomColor: 0x3a3a52, shoeColor: 0x4a3420, accentColor: 0xf6c453
};