import Phaser from 'phaser';

/**
 * 像素瓦片集(Tileset)生成 + 小镇 tilemap 组装(M0.10 美术基座)。
 * 目标:告别"单色平铺草皮",改用正宗 tilemap 承载多地貌(草地/水/田/路/石/花/树),
 *       为后续接入专业资产生成管线打底(资产热替换时仅换 texture/drawTile)。
 * 观感尽力接近星露谷式的"斑驳像素 + 明暗层次"。
 */

const TILE = 32;
type G = Phaser.GameObjects.Graphics;

function tFill(g: G, x: number, y: number, w: number, h: number, c: number): void {
  g.fillStyle(c);
  g.fillRect(x, y, w, h);
}
function tDot(g: G, x: number, y: number, c: number, s = 2): void {
  g.fillStyle(c);
  g.fillRect(x, y, s, s);
}
const sh = (c: number, f: number) => {
  const r = Math.max(0, Math.min(255, Math.round(((c >> 16) & 255) * f)));
  const gg = Math.max(0, Math.min(255, Math.round(((c >> 8) & 255) * f)));
  const b = Math.max(0, Math.min(255, Math.round((c & 255) * f)));
  return (r << 16) | (gg << 8) | b;
};

function drawGrass(g: G, x: number): void {
  tFill(g, x, 0, TILE, TILE, 0x6fae3a);
  // 斑驳颗粒: 深浅绿随机点, 做出"草皮颗粒"
  let seed = x * 31;
  const rand = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280; };
  for (let i = 0; i < 26; i++) {
    const px = Math.floor(rand() * TILE);
    const py = Math.floor(rand() * TILE);
    const c = rand() > 0.5 ? 0x5f9e31 : 0x7fbe47;
    tDot(g, x + px, py, c, 2);
  }
  // 顶部受光阴影带
  tFill(g, x, 0, TILE, 2, 0x5a9a2e);
  tFill(g, x, TILE - 2, TILE, 2, 0x7fbe45);
}

function drawWater(g: G, x: number): void {
  tFill(g, x, 0, TILE, TILE, 0x4a7fb5);
  tFill(g, x, 0, TILE, 2, 0x3a6da0);
  // 波纹高光(两条水平亮带 + 波动像素)
  tFill(g, x + 3, 6, 12, 2, 0x9fd0f5);
  tFill(g, x + 2, 14, 8, 2, 0x8fc4ee);
  tFill(g, x + 5, 22, 10, 2, 0x9fd0f5);
  tDot(g, x + 16, 10, 0xbfe2ff, 2);
  tDot(g, x + 8, 18, 0xbfe2ff, 1);
  tDot(g, x + 18, 26, 0x8fc4ee, 2);
  tFill(g, x, TILE - 2, TILE, 2, 0x35638f);
}

function drawSoil(g: G, x: number): void {
  tFill(g, x, 0, TILE, TILE, 0x9a6b3f);
  // 耕田条纹
  for (let r = 4; r < TILE; r += 7) tFill(g, x, r, TILE, 2, 0x8a5c34);
  for (let r = 8; r < TILE; r += 7) tFill(g, x, r, TILE, 1, 0xa87b4b);
  // 土粒
  tDot(g, x + 6, 12, 0x7c4f2c, 2); tDot(g, x + 20, 20, 0xb4875a, 2);
  tFill(g, x, TILE - 2, TILE, 2, 0x8a5c34);
}

function drawPath(g: G, x: number): void {
  tFill(g, x, 0, TILE, TILE, 0xcbb387);
  // 碎石点
  tDot(g, x + 6, 6, 0xb39a6a, 3); tDot(g, x + 18, 14, 0xd8c39a, 4);
  tDot(g, x + 10, 22, 0xa68d60, 3); tDot(g, x + 22, 26, 0xe0d0ac, 3);
  // 边界暗角
  tFill(g, x, 0, TILE, 2, 0xb39a6a);
  tFill(g, x, TILE - 2, TILE, 2, 0xbaa277);
}

function drawStone(g: G, x: number): void {
  tFill(g, x, 0, TILE, TILE, 0x9aa4ad);
  tDot(g, x + 8, 8, 0xb4bec7, 6); tDot(g, x + 18, 16, 0xc3ccd4, 8);
  tDot(g, x + 10, 18, 0x7f8a93, 5); tFill(g, x + 20, 22, 6, 6, 0x8b959e);
  tFill(g, x, 0, TILE, 2, 0x87929b); tFill(g, x, TILE - 2, TILE, 2, 0x858f98);
}

function drawFlower(g: G, x: number): void {
  drawGrass(g, x);
  // 小花: 中心黄 + 花瓣
  tDot(g, x + 6, 8, 0xe05555, 2); tDot(g, x + 8, 6, 0xe05555, 2);
  tDot(g, x + 4, 6, 0xe05555, 2); tDot(g, x + 6, 6, 0xffd94d, 1);
  tDot(g, x + 22, 18, 0xffd94d, 1); tDot(g, x + 21, 17, 0xf0b64d, 2); tDot(g, x + 23, 17, 0xf0b64d, 2);
  tDot(g, x + 24, 22, 0xffffff, 1);
}

function drawTree(g: G, x: number): void {
  drawGrass(g, x);
  const cx = x + 16;
  // 树影
  g.fillStyle(0x3f7a30, 0.5); g.fillEllipse(cx, TILE - 6, 20, 6);
  // 树干
  tFill(g, cx - 3, TILE - 12, 6, 10, 0x7a4a24);
  tFill(g, cx - 2, TILE - 12, 2, 10, 0x8a5a30);
  // 树冠(两大圆 + 明暗)
  g.fillStyle(0x3f9b44); g.fillCircle(cx, TILE - 18, 9);
  g.fillStyle(0x58b95c); g.fillCircle(cx - 2, TILE - 20, 6);
  g.fillStyle(0x2f7a35); g.fillCircle(cx + 3, TILE - 15, 6);
  // 高光叶
  g.fillStyle(0x8fd48a); g.fillCircle(cx - 5, TILE - 22, 3);
}

/** 河岸: 草地临水边的湿沙过渡带 */
function drawShore(g: G, x: number): void {
  drawGrass(g, x);
  // 靠"下"边是水的过渡: 湿沙 + 波纹
  g.fillStyle(0xcbb69a, 0.7); g.fillRect(x + 2, TILE - 12, TILE - 4, 12);
  g.fillStyle(0xb9a385); g.fillRect(x, TILE - 12, TILE, 2);
  tDot(g, x + 6, TILE - 6, 0x7c8fa3, 3); tDot(g, x + 18, TILE - 4, 0x93a9bd, 2);
  tDot(g, x + 12, TILE - 8, 0x6f829b, 2);
  tFill(g, x, TILE - 2, TILE, 2, 0x6a7d94);
}

/** 木栅栏 */
function drawFence(g: G, x: number): void {
  drawGrass(g, x);
  const cx = x + 16;
  // 顶部横木 + 竖柱 + 斜撑
  g.fillStyle(0x7a4a24); g.fillRect(cx - 14, 6, 28, 4);
  g.fillStyle(0x8a5a30); g.fillRect(cx - 14, 14, 28, 4);
  g.fillStyle(0x6b3d1e); g.fillRect(cx - 4, 6, 8, 22);
  g.fillStyle(0x5a2f14); g.fillRect(cx - 4, 6, 3, 22);
  // 地面阴影
  g.fillStyle(0x3f7a30, 0.5); g.fillRect(cx - 14, 26, 28, 4);
}

/** 作物(绿苗) */
function drawCrop(g: G, x: number): void {
  g.fillStyle(0x9a6b3f); g.fillRect(x, 0, TILE, TILE);
  for (let r = 4; r < TILE; r += 7) { g.fillStyle(0x8a5c34); g.fillRect(x, r, TILE, 2); }
  const cx = x + 16;
  g.fillStyle(0x3e8f43); g.fillRect(cx - 2, TILE - 16, 4, 12);   // 茎
  g.fillStyle(0x58b95c); g.fillRect(cx - 5, TILE - 22, 10, 4); // 叶两片
  g.fillStyle(0x4ca74f); g.fillRect(cx - 5, TILE - 16, 4, 2);
  g.fillStyle(0x58b95c); g.fillRect(cx + 1, TILE - 18, 4, 2);
  g.fillStyle(0x2f7a35); g.fillRect(cx - 2, TILE - 24, 4, 3);
}

/** tile 索引 → 绘制函数(与地图 data 值对应, data 用 1-based gid) */
const DRAWERS: Array<(g: G, x: number) => void> = [
  drawGrass, drawWater, drawSoil, drawPath, drawStone, drawFlower, drawTree, drawShore, drawFence, drawCrop
];

/** 一栋斜顶小木屋(实体建筑): 木墙+窗+门+烟囱+地面阴影。 */
export function buildHouse(scene: Phaser.Scene, x: number, y: number): Phaser.GameObjects.Image {
  const key = 'ts_house';
  if (!scene.textures.exists(key)) {
    const g = scene.make.graphics({ x: 0, y: 0 }, false);
    g.fillStyle(0x1f2a1a); g.fillCircle(64, 116, 40);           // 地面阴影
    g.fillStyle(0x4a3320); g.fillRect(8, 60, 112, 62);          // 墙底
    g.fillStyle(0x8a5a34); g.fillRect(14, 64, 100, 56);
    for (let yy = 64; yy < 120; yy += 10) { g.fillStyle(0x7d4e2d); g.fillRect(14, yy, 100, 3); } // 木纹
    // 窗
    g.fillStyle(0x2a1c10); g.fillRect(20, 76, 16, 16); g.fillStyle(0xffe9a8); g.fillRect(22, 78, 12, 12);
    g.fillStyle(0x2a1c10); g.fillRect(92, 76, 16, 16); g.fillStyle(0xffe9a8); g.fillRect(94, 78, 12, 12);
    g.fillStyle(0xd8b25a); g.fillRect(27, 82, 2, 8); g.fillRect(93, 82, 2, 8);
    // 门
    g.fillStyle(0x5a3a22); g.fillRect(48, 96, 20, 24); g.fillStyle(0x2c1c10); g.fillRect(50, 98, 16, 20);
    g.fillStyle(0xffd94d); g.fillRect(62, 108, 3, 3);
    // 屋檐遮沿
    g.fillStyle(0x9a5a2a); g.fillRect(6, 60, 116, 5);
    g.fillStyle(0x6b4030); g.fillRect(4, 52, 120, 10);
    // 斜屋顶
    g.fillStyle(0xc35b2a); g.fillTriangle(2, 56, 126, 56, 64, 14);
    g.fillStyle(0xd8733f); g.fillTriangle(34, 50, 94, 50, 64, 20);
    // 烟囱
    g.fillStyle(0x6b4030); g.fillRect(100, 30, 16, 26); g.fillStyle(0x57402c); g.fillRect(98, 26, 20, 7);
    g.generateTexture(key, 130, 126); g.destroy();
  }
  const img = scene.add.image(x, y, key);
  img.setDepth(1); // 立于地面之上, 玩家可走到"建筑前"被遮挡
  return img;
}
  /** 生成 tileset sheet 并据此铺出小镇 tilemap, 返回地面层。 */
export function buildTownMap(scene: Phaser.Scene): Phaser.Tilemaps.TilemapLayer {
  const COLS = 40;
  const ROWS = 40;

  // 1) tileset sheet(7 tile 横排, 每 32px)
  if (!scene.textures.exists('ts_tileset')) {
    const g = scene.make.graphics({ x: 0, y: 0 }, false);
    DRAWERS.forEach((draw, i) => draw(g, i * TILE));
    g.generateTexture('ts_tileset', DRAWERS.length * TILE, TILE);
    g.destroy();
  }

  // 2) 生成 data(1-based): 1草 2水 3田 4路 5石 6花 7树
  let seed = 7;
  const rand = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280; };
  const data: number[][] = Array.from({ length: ROWS }, () => new Array<number>(COLS).fill(1));
  const put = (r: number, c: number, v: number): void => {
    if (r >= 0 && r < ROWS && c >= 0 && c < COLS) data[r][c] = v;
  };

  // 小湖(左上) + 四周湿岸衔接
  for (let r = 5; r <= 10; r++) for (let c = 3; c <= 8; c++) put(r, c, 2);
  for (let c = 3; c <= 8; c++) { put(4, c, 8); put(11, c, 8); }
  for (let r = 5; r <= 10; r++) { put(r, 2, 8); put(r, 9, 8); }
  put(4, 2, 8); put(4, 9, 8); put(11, 2, 8); put(11, 9, 8);
  // 对角石板路(从左下到右上)
  let rr = ROWS - 2, cc = 1;
  while (rr >= 2 && cc <= COLS - 3) {
    put(rr, cc, 4); put(rr - 1, cc, 4);
    rr -= 1; cc += 1;
  }
  // 一块农田(中下): 土 + 作物 + 围栏
  for (let r = 28; r <= 33; r++) for (let c = 14; c <= 20; c++) put(r, c, 3);
  for (let r = 29; r <= 32; r++) for (let c = 15; c <= 19; c++) if ((r + c) % 3 === 0) put(r, c, 10);
  for (let r = 27; r <= 34; r++) put(r, 13, 9);
  for (let r = 27; r <= 34; r++) put(r, 21, 9);
  for (let c = 13; c <= 21; c++) put(27, c, 9);
  for (let c = 13; c <= 21; c++) put(34, c, 9);
  // 随机石与花
  for (let i = 0; i < 22; i++) put(Math.floor(rand() * ROWS), Math.floor(rand() * COLS), 5);
  for (let i = 0; i < 26; i++) put(Math.floor(rand() * ROWS), Math.floor(rand() * COLS), 6);
  // 几棵树(固定, 避免盖住路/湖/田)
  const trees: Array<[number, number]> = [[3, 24], [6, 30], [12, 12], [16, 26], [22, 6], [30, 32], [34, 10], [37, 26]];
  trees.forEach(([r, c]) => put(r, c, 7));
  // 湖边几丛花
  [[6, 2], [5, 9], [10, 2], [11, 8]].forEach(([r, c]) => put(r, c, 6));

  // 3) tilemap
  const map = scene.make.tilemap({ data, tileWidth: TILE, tileHeight: TILE });
  const ts = map.addTilesetImage('ts_tileset', 'ts_tileset', TILE, TILE);
  const layer = (ts ? map.createLayer(0, ts, 0, 0) : null)!;
  layer.setDepth(-1);
  return layer;
}