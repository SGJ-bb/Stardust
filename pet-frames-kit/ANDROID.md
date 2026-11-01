# 安卓端接入：Live2D × 帧动画（stradust / WebView 壳）

> 已改好的范围：**只加文件 + 两处小改**（HTML 里两个 script 标签 + 3 个 Kotlin 方法）。没有动你的业务逻辑、模型加载、聊天、历史记录、多模型切换。
> 回滚 = 删掉 `assets/js/frameanim.js`、`assets/js/petframes-boot.js`、`assets/js/petframes/`，再撤掉那三段改动。

## 0. 为什么要单独做一层

你的安卓端是 `Live2DWebView` 里拼一段内联 HTML（`buildLive2DHtml()`），
`app` / `model` 都是内联脚本的局部变量，外面拿不到。
所以帧动画层用 **boot 脚本包一层 `PIXI.live2d.Live2DModel.from`** 来拿模型实例，
再等模型挂到 stage 上（有 `parent`）后建层 —— **完全不用改你的内联脚本逻辑**。

## 1. 已经放进 assets 的东西

```
app/src/main/assets/js/
├─ frameanim.js            # 帧动画播放器（PIXI 7/8 双兼容 + Canvas2D 兜底）
├─ petframes-boot.js       # Android 自动接入层（包 from、算落位、对外 API）
└─ petframes/
   ├─ hearts.frames.json + hearts.sheet.png      # 摸头爱心（overlay）
   ├─ zzz.frames.json    + zzz.sheet.png         # 睡着 ZZZ（overlay）
   ├─ sparkle.frames.json+ sparkle.sheet.png     # 星星（overlay）
   └─ greet.frames.json  + greet.sheet.png       # 打招呼（replace，340px 移动版 2.0MB）
```

> 服务器路由：`LocalModelServer` 里 `js/` 前缀直接从 `assets/` 读，
> 所以这些文件**不需要**改 NanoHTTPD 路由就能被 `http://127.0.0.1:端口/js/...` 取到。
> boot 脚本用 `document.currentScript.src` 推导自身目录，`http://` 与 `file://` 回退两种加载都能用。

## 2. 两处改动（已应用）

### 2.1 `Live2DWebView.kt` · 内联 HTML 里加两行

```kotlin
<script src="/js/pixi.min.js"></script>
<script src="/js/pixi-live2d-display.min.js"></script>
<script src="/js/frameanim.js"></script>      // ← 新增
<script src="/js/petframes-boot.js"></script> // ← 新增
<script>
```

顺序有要求：必须在 `pixi` / `pixi-live2d-display` 之后、你的内联脚本之前。

### 2.2 `Live2DWebView.kt` · 三个方法

```kotlin
live2dView.playFrameAnimation("hearts")                    // overlay，叠在模型上
live2dView.playFrameAnimation("greet", "replace")          // 替身：模型临时隐藏，播完恢复
live2dView.playFrameAnimationRect("hearts", x, y, w, h)    // 精确落位（CSS 像素，相对 WebView）
live2dView.stopFrameAnimation()                            // 停止 + 恢复模型显示
```

内部就是 `evaluateJavascript("window.playPetFrames(...)")`，和你现有 `setEmotion` / `setAction` 同款写法。
WebView 里对外还有完整 JS API：

| JS | 说明 |
|---|---|
| `window.playPetFrames(name, mode?, rect?)` | 播放；模型未加载完会自动排队，加载完补播 |
| `window.playPetFramesRect(name, {x,y,w,h})` | 指定矩形播放 |
| `window.stopPetFrames()` | 停止并恢复模型 |
| `window.petFramesInfo()` | `{ready, list, model, playing}` 便于调试 |

## 3. 什么时候该调

| 场景 | 调用 |
|---|---|
| 摸头 / 好感度上升 | `playFrameAnimation("hearts")` |
| 进入睡眠 / 长时间无交互 | `playFrameAnimation("zzz")`（loop，醒来记得 `stopFrameAnimation()`） |
| 收到礼物 / 升级 | `playFrameAnimation("sparkle")` |
| 「打个招呼」按钮 | `playFrameAnimation("greet", "replace")` |
| 切模型 / 退出页面 | `stopFrameAnimation()` |

建议挂在**已有的情绪/动作入口**旁边（`setEmotion`、`setAction`、`onActionTrigger` 那些），不要新加按钮。

## 4. 平台坑（都在代码里处理了，改代码时别踩回去）

1. **PIXI 7 的纹理 valid 坑**：`Texture.from(HTMLImageElement)` 后 `baseTexture.valid` 可能还是 false，
   子纹理会被跳过不画（容器位置、alpha 都对，就是看不见）。`frameanim.js` 里已 `update()` + 等 `loaded`。
2. **`Live2DModel` 不吃 PIXI 的 alpha**：`model.alpha = 0` 不会隐藏模型（它自己重写了 render）。
   replace 模式改用 `model.visible = false`，播完恢复。
3. **纹理上限 4096**：你 Kotlin 里已经按 4096 保守处理。本 kit 的 greet 移动版是 `4000×680`，安全；
   想更省可 `--max-frame 240` 重打包。
4. **显存**：`greet` 移动版解码后约 10MB，加上模型本身，中低端机建议**同一时间只开一个 replace 帧集**，
   其余用 2~50KB 的 overlay 特效集。
5. **后台节流**：`frameanim.js` 的 rAF 在 `document.hidden` 时跳过推进；`app.ticker.stop()` 不影响它。
6. **落位坐标**：manifest 里 `space:"model"` 的帧是按**烘焙它的那个模型**算的 `rectModel`。
   换模型（Haru/小恶魔…）时不要直接复用 greet，要么用 `space:"zone"` 的 overlay 特效，
   要么针对新模型重新 bake 一次（下面第 5 节）。

## 5. 给新模型/新动作做帧序列

> **AI 生成的素材走绿幕**，处理管线见 [`GREENSCREEN.md`](./GREENSCREEN.md)：
> `--video x.mp4 --key green --despill 0.85 --shrink 1 --stabilize bottom`，一步出图集。

```bash
# ① 用桌面端把 Live2D 动作烘成 PNG 序列（模型要对得上）
electron pet/app --bake=bake_wave --bake-motion=PetGreet --bake-fps=12
# ② 打包成安卓可用的尺寸（max-frame 控制单帧最长边，320~400 在手机上足够）
python tools/pet_frames_build.py --input pet/out/bake_wave --name wave \
    --fps 12 --space model --blend replace --max-frame 340 \
    --out <assets>/js/petframes/wave.frames.json
# ③ 把 wave.frames.json + wave.sheet.png 拷进 assets/js/petframes/，并把名字加进
#    petframes-boot.js 的 INDEX 数组
```

外部素材（AI 生成 / 手绘 / 视频抽帧）同理，用 `--space zone --attach head --key auto`。

## 6. 本地验证

`_test/android/`（不在 assets 内，不会打进 APK）里的 `petfx_test2.html` 用**和你安卓端完全相同的库**
（`assets/js/pixi.min.js` = PIXI 7.3.2 + `pixi-live2d-display.min.js` + `vtuber/Haru` 模型）跑通了：

```
PIXI 7.3.2 | model 912x1710
frames ready: hearts,zzz,sparkle,greet
play overlay -> true   alpha=1.00 frame=10
RESULT PASS
```

复现：把 `assets/` 当静态根目录起一个 http 服务，打开 `/_test/android/petfx_test2.html`。
真机验证建议：`adb logcat | grep -E "petfx|L2D"`，加载成功会打 `[petfx] ready: hearts,zzz,...`。

## 7. 构建与验证状态

- `gradlew :app:compileDebugKotlin --offline` → **BUILD SUCCESSFUL**（2026-10-22，改动已通过 Kotlin 编译）。
- 页面级验证：用安卓端同一套 `assets/js/pixi.min.js`(7.3.2) + `pixi-live2d-display.min.js` + 自带 `vtuber/Haru`
  跑 `_test/android/petfx_test2.html`，日志 `frames ready: hearts,zzz,sparkle,greet / RESULT PASS`。
- **改 Kotlin 时的坑**：Kotlin 的块注释**可以嵌套**，注释里写 `assets/js/petframes/*.frames.json`
  会因为 `/*` 又开一层注释，导致整文件被吞、报 "Unclosed comment" + 一堆 `Unresolved reference: addLog`。
  注释里别出现 `/*`。

## 8. 已知限制

- 帧动画不适合"跟手"（视线跟随、头部朝向仍归 Live2D）。
- replace 切换靠"帧淡入 130ms 后隐藏模型"，跨姿势仍可能有极短跳变；无缝需要帧序列首帧对齐当前姿态。
- 暂未做音频事件（`events` 字段在 manifest 里预留）。
