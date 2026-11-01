# PetFrames 接入指南 —— Live2D + 帧动画（PC / iOS / Android）

> 目标：**保留你现有的 stradust 载体**（Tauri + React + PIXI 8 + `@jannchie/pixi-live2d-display`），
> 只往里加一层"序列帧播放器"。Live2D 继续管呼吸/眨眼/视线/口型/物理，帧动画管一次性节目。
> 这一层不依赖 Live2D、不依赖 Electron，两端（PC/iOS）和安卓 WebView 都能用。

本目录自带可运行样例：`demo.html`（纯 Canvas2D，无 PIXI 也能跑）。
启动方式：`python -m http.server 8899` → 浏览器打开 `http://127.0.0.1:8899/demo.html`。

> **安卓端已单独接好**：见 [`ANDROID.md`](./ANDROID.md)（assets 已部署 + Kotlin 两处小改 + 本地验证通过）。
> 本文档讲 PC/iOS（Tauri + PIXI 8）；安卓是 WebView + PIXI 7.3.2，用 `petframes-boot.js` 零侵入接入。

---

## 0. 我的参考实现 vs 你的载体（先对齐认知）

| | 我的参考实现（`F:/image2psd2live/pet/`） | 你的 stradust |
|---|---|---|
| 外壳 | Electron 40 | **Tauri 2**（PC/iOS） + Android WebView 壳 |
| 渲染 | PIXI 7.4.3 + `pixi-live2d-display` 0.4.0 | **PIXI 8.19** + `@jannchie/pixi-live2d-display` 1.4 |
| 透明窗口 | Electron 透明窗口 + 逐像素点击穿透 | Tauri 窗口透明（`transparent: true`） |
| 语言 | 纯 JS（无构建） | React + TS + Vite |

**结论：渲染范式相同（都是 PIXI + Live2D），所以只有"外壳层"不通，**"帧动画层"可以整块搬过去**。
`frameanim.js` 已经做了 PIXI 7/8 双兼容（v8 删了 `BaseTexture`，代码里自动切 `texture.source`），
并带一个 **Canvas2D 后端**（没有任何 PIXI 的环境也能跑）。

---

## 1. 三分钟接入（stradust-pc / stradust-ios）

### 1.1 放文件

```
stradust-pc/
├─ public/frames/                 ← 把本目录 frames/*.frames.json + *.sheet.png 放这里
└─ src/lib/pet-frames/frameanim.js   ← 本目录的 frameanim.js
```

> 走 `public/` 是因为 Tauri 里前端拿到的是 `tauri://localhost` 或 `http://tauri.localhost`，
> `fetch('./frames/xx.frames.json')` 相对路径最省事，不用过 `convertFileSrc`。

### 1.2 挂到现有 Live2DCanvas

你现在的 `src/components/live2d/Live2DCanvas.tsx` 里已经 `await pixiApp.init({...})`。
在模型加载成功后加几行：

```ts
import { PetFrames } from "@/lib/pet-frames/frameanim.js";

// 1) 初始化（放在 pixiApp.init 之后，stage 已有）
let fx: any = null;
async function initFx() {
  fx = new PetFrames({ app: pixiApp, PIXI });          // 自动识别 PIXI 8
  await fx.loadAll([
    "/frames/hearts.frames.json",
    "/frames/zzz.frames.json",
    "/frames/greet.frames.json",
  ]);
}

// 2) overlay 特效：挂在头部命中区（你是按 scale 0.25 摆模型，用模型的屏幕矩形算）
fx.play("hearts", {
  rect: PetFrames.rectFromAnchorBox(
    fx.animations.get("hearts").meta,
    { x: model.x, y: model.y, w: model.width * model.scale.x, h: model.height * model.scale.y },
    1.5
  ),
});

// 3) replace 替身：帧动画接管整屏
const meta = fx.animations.get("greet").meta;
fx.play("greet", { rect: PetFrames.rectFromModel(meta, { x: model.x, y: model.y, scale: model.scale.x }) });
model.alpha = 0;                                       // 模型淡出（配 CSS/过渡自己做）
fx.onEnd = () => { model.alpha = 1; };                 // 播完淡回
```

要点：
- **rect 必须是 stage 坐标系（CSS 像素）**，和 `model.x / model.y / model.scale` 同一套。
- `space:"model"` 的 manifest 用 `rectFromModel`（烘焙帧，像素级对齐）；
  `space:"zone"` 的用 `rectFromAnchorBox`（特效，按命中区缩放）。
- PIXI 8 下 `model.scale` 是 `ObservablePoint`，取 `.x`。

### 1.3 表情/动作按钮联动

你现在有 `setExpression` / `startMotion` 两个入口，把帧动画挂同一批按钮即可：

```ts
function onHeadPat() {
  controllerRef.current?.setExpression("Love");
  fx.play("hearts", { rect: headBoxRect() });
}
```

---

## 2. 安卓（WebView 壳）

你 `android/app/src/main/assets/js/` 里已经有 `pixi.min.js` + `pixi-live2d-display.min.js` +
`live2dcubismcore.min.js`，说明安卓端也是 WebView 渲染 —— **同一份 frameanim.js 直接能用**：

1. 把 `frames/` 拷进 `assets/frames/`，`frameanim.js` 拷进 `assets/js/`。
2. 页面里 `<script src="js/frameanim.js"></script>`，然后
   ```js
   const fx = new PetFrames({ PIXI: window.PIXI, app: pixiApp });   // 有 PIXI 走 PIXI
   // 或没有 PIXI 的页面：new PetFrames({ canvas })
   ```
3. 安卓注意：
   - `fetch` 走 `file:///android_asset/...` 时部分机型会被拦 → 建议用 `WebViewAssetLoader`
     映射成 `https://appassets.androidplatform.net/frames/...`，或把 JSON 内联成对象传进去。
   - WebView 的 `devicePixelRatio` 通常 2~3，图集**别超过 4096px 一边**（本 kit 的 greet 是 3948×2094，安全）。
   - 低端机把 `fps` 从 12 降到 8、图集降到 50% 分辨率，肉眼几乎无差。

---

## 3. iOS（Tauri 2 iOS）

- 前端代码与 PC 完全一致（WKWebView + WebGL2）。
- 注意：iOS 对**同时存活的纹理**更敏感。建议同一时间只保留 1 个 replace 图集 + 1~2 个 overlay 图集，
  用完 `fx.destroy()`（或只 `load` 需要的那几份）。
- 透明 WebView 叠 Live2D 时，`-webkit-backdrop-filter` 之类会拖性能，能不用就不用。

---

## 4. 契约：`*.frames.json`

```json
{
  "version": 1,
  "name": "greet",
  "sheet": "greet.sheet.png",
  "fps": 12, "loop": false,
  "frameW": 329, "frameH": 698, "cols": 12, "rows": 3, "count": 29,
  "space": "model",                    // model | zone
  "blend": "replace",                  // overlay | replace（播放器不强制，调用方决定）
  "anchor": [0, 0],                    // zone 空间一般 [0.5,0.5]
  "attach": "head", "scaleK": 1.5, "offset": [0, 0],
  "rectModel": [280.6, 21.8, 474.2, 1006],   // space=model：帧在模型画布里的矩形
  "pxToModel": 1.44147, "scale0": 0.46249
}
```

- `space=model`：帧是"从 Live2D 动作烘焙出来"的，跟模型同一坐标系 → 缩放窗口也不会错位。
- `space=zone`：帧是独立素材（特效/整身动作），按 `attach + scaleK + offset` 贴到命中区。
- `pxToModel = 1 / (downscale × dpr × scale0)`：帧像素 → 模型单位。

---

## 5. 怎么产出新的帧动画

工具在 `F:/image2psd2live/tools/`（Python，`F:/see-through/venv/Scripts/python.exe` 跑）：

| 素材来源 | 命令 |
|---|---|
| **自己画/AI 生成/视频抽帧** | `python pet_frames_build.py --input frames/wave --name wave --fps 12 --loop false --space zone --attach body --key auto --out out/wave.frames.json --preview` |
| **程序化特效** | `python pet_fx_gen.py --preset hearts --out src_hearts --frames 12` → 再走上一行打包 |
| **从 Live2D 动作烘焙**（对齐最好） | `electron pet/app --bake=bake_greet --bake-motion=PetGreet --bake-fps=12` → `pet_frames_build.py --space model --layout bake_greet/bake_layout.json` |

`--key auto` 会自动扣白底/绿幕；`--preview` 出 GIF 方便先看效果。

---

## 6. 分工建议（别把 Live2D 的活交给帧动画）

| 交给 Live2D | 交给帧动画 |
|---|---|
| 呼吸、眨眼、视线跟随、口型、头发/裙摆物理 | 坐下/躺下/起身、换装、变身、道具、大招 |
| 表情（exp3 单键） | 情绪特效（爱心/星星/ZZZ/汗滴/魔法阵） |
| 任何"要跟鼠标/手指走"的动作 | 任何"有明确起止"的表演 |

帧动画做"跟手"必穿帮（没有逐帧的参数插值），Live2D 硬调大动作成本爆炸 —— 这条线划清楚，两边都省事。

---

## 7. 已知坑（我这边踩过的）

1. **PIXI 8 移除了 `BaseTexture`**：`new PIXI.Texture({ source, frame })`；`frameanim.js` 已按版本分支处理。
2. **sheet 图集过大**：一张 3948×2094 的 PNG 解码后约 32MB 显存，同时开 4 张会吃满低端机 → 按需加载。
3. **`fps` 不要超过 15**：序列帧的节奏感本来就在 8~15fps，高了只涨体积。
4. **replace 切换要淡出**（110ms 左右）：第 0 帧姿势和模型当前姿势不一样，硬切会"跳一下"。
5. **loop 型特效要能停**：ZZZ/爱心是循环的，状态切走时记得 `fx.stop()`。
6. **Tauri 的 CSP**：如果 `tauri.conf.json` 限制了 `img-src`/`connect-src`，记得放行 `frames/*`。
7. **多实例**：同一页面只创建 1 个 `PetFrames`；多个会各自跑 rAF，白白翻倍开销（也可 `autoTick:false` 后自己 `tick(dt)`）。

---

## 8. 这张图是干什么的

`_shot_hearts.png` / `_shot_greet.png` 是无头浏览器对 `demo.html` 的实拍：
证明"没有 PIXI、没有 Live2D"的纯 Canvas2D 环境也能播 overlay 与 replace ——
即移动端最保守的 WebView 也有兜底路径。
