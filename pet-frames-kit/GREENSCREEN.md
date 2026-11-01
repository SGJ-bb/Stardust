# 绿幕素材 → 帧动画（AI 生成的最佳实践）

> 结论：**AI 出素材就走绿幕**。白底会把浅色衣服/发丝一起吃掉，透明通道 AI 视频模型又不给；
> 绿幕的色度差最大，抠像能保住发丝和半透明边缘——前提是必须做 **despill（去绿溢色）+ 软边 matte + 稳定化** 三件事。
> 工具：`F:/image2psd2live/tools/pet_frames_build.py`（已内置），自测：`pet_greenscreen_selftest.py`。

---

## 1. 为什么绿幕，而不是白底 / 蓝底 / 直接要透明

| 底色 | 抠像难度 | 典型翻车 |
|---|---|---|
| **纯绿 #00B140**（推荐） | 最低：绿通道远高于红蓝，差值稳定 | 白衣服/浅发被绿反光染色 → 必须 despill |
| 纯蓝 #0047BB | 次之 | 蓝色衣服、深色头发会糊；绿衣服角色改用蓝幕 |
| 白底 | 高 | 白衬衫、白丝、浅肤色全部和背景同色，一抠一个洞 |
| 透明 PNG 序列 | 没有（AI 视频不产出） | 图生图单帧可以，视频不行 |

**绿幕的物理优势**：`d = G - max(R,B)` 这个差值在幕布上能到 100+，在皮肤/衣服上通常 < 20，
中间那条过渡带就是真实的半透明边缘（发丝、裙摆薄纱）——正好是"软边 matte"要保留的东西。

---

## 2. 生成端规范（决定后面抠像上限）

### 2.1 提示词模板（图生视频，首帧用你的立绘）

```
[角色描述保持一致], full body, standing centered, facing camera,
solid chroma key green background (#00B140), flat even lighting,
no shadow on background, no floor, no props, no camera movement,
character stays fully inside frame, clean lineart, anime style,
<动作描述：waves hand / sits down / turns around slowly>
```

负向：`shadow, gradient background, green screen reflection on clothes, camera pan, zoom, cut, extra limbs, blur`

### 2.2 参数与素材要求

- **构图**：全身入画，四周留 8~10% 边距（AI 易抖，留余量裁）；机位固定（`no camera movement`）。
- **光**：均匀平光；不要有地面阴影（阴影会被抠成半透明洞，`--shrink` 也救不回来）。
- **时长/帧率**：2~4 秒，生成 24fps → 抽 **12fps**（桌宠够用，体积减半）。
- **循环动作**：首尾帧姿态要接近，否则 loop 播放会"跳一下"。
- **别穿绿衣服**：绿衣服 + 绿幕 = 必抠穿；换蓝幕（`--key blue`）。

---

## 3. 一条命令（视频直接进）

```bash
python tools/pet_frames_build.py \
  --video wave_green.mp4 --fps 12 --name wave \
  --key green --despill 0.85 --shrink 1 --stabilize bottom \
  --space zone --attach body --scale-k 1.0 \
  --out pet/assets/frames/wave.frames.json --preview
```

PNG 序列同理，把 `--video x.mp4` 换成 `--input frames/wave/`。
产物：`wave.sheet.png`（图集）+ `wave.frames.json`（契约）+ `wave.preview.gif`（先看效果）。
`--space model` 只能用于 Live2D 烘焙帧；AI 素材用 `zone`（贴命中区）。

---

## 4. 参数怎么调（按症状查）

| 症状 | 原因 | 处理 |
|---|---|---|
| 背景还剩一层淡绿 | 幕布不纯/带渐变 | 工具已按四角实测幕布色自适应收紧 `hi`；仍不行就 `--hi 45` |
| 人物边缘缺一块、发丝被吃 | `hi` 太大，把浅色前景判成背景 | `--hi 65`（放宽）+ `--lo 10` |
| 轮廓一圈绿 | 反光/压缩造成的绿溢色 | `--despill 0.9~1.0`，再加 `--shrink 1` |
| 白衣服发洋红 | despill 过头（把白压成中性） | `--despill 0.55~0.65` |
| 播放时人物左右漂 | AI 生成构图抖动 | `--stabilize bottom`（全身）/ `centroid`（半身、特效） |
| 整体偏暗/偏亮 | 生成时光照不一致 | 生成端解决；工具不做自动调色（会破坏与 Live2D 的色差） |

内部实现（`chroma_key()`）：
```
d      = key_channel - max(other_channels)
hi_eff = min(hi, max(lo+8, screen_d*0.75))     # 幕布一定归零，且随幕布纯度自适应
alpha  = clamp((hi_eff - d) / (hi_eff - lo), 0, 1)   # 软边，不是二值
green  = key - clamp(key-other,0) * despill * (0.4 + 0.6*edge)
shrink → MinFilter（去掉残边）
```

---

## 5. 实测（本机，2026-10-22）

用真实透明帧合成一版"带 AI 缺陷"的绿幕素材（每帧 ±4px 抖动、±1.2% 缩放、绿底亮度漂移、边缘绿溢、传感器噪声），
再 x264 `yuv420p` 压缩成 mp4，跑完整管线：

| 指标 | 结果 |
|---|---|
| 背景泄漏（四角 alpha 均值） | **0.0%** |
| 白衬衫区域绿偏 `G-max(R,B)` | **-8.6**（负值=无绿染；未 despill 时为正） |
| 稳定化修正量 | 最大 **7px** 位移被拉回 |
| 覆盖率 | 65% ±0.5%（与真值一致） |
| 帧尺寸 | 326×700（从 640×960 裁到内容） |

自测命令：`python tools/pet_greenscreen_selftest.py`，产物在 `pet/out/greenscreen_test/`（`compare.png` 三行对比、`report.json`）。

---

## 6. 抠完之后接到桌宠

产出的是标准 `*.frames.json` + 图集，和烘焙帧完全同构：

- **Android**：放进 `assets/js/petframes/`，把名字加进 `petframes-boot.js` 的 `INDEX`，Kotlin 调 `playFrameAnimation("wave")`。
- **PC/iOS（Tauri）**：放进 `public/frames/`，`fx.load('frames/wave.frames.json')` + `fx.play('wave', {rect})`。
- **Electron 参考实现**：写进 `pet.config.json` 的 `frameAnims`，或桥 `POST /cmd {"type":"frames","name":"wave"}`。

`space:"zone"` 的素材按 `attach + scaleK` 贴命中区；如果想让特效精确盖住脑袋，用 `--offset dx,dy`（单位=命中区宽高）。

---

## 7. 什么时候别用帧动画

- 需要跟鼠标/手指走的（视线跟随、头部朝向）→ 留在 Live2D。
- 需要和物理摆动联动的（头发、裙摆）→ 留在 Live2D。
- 超过 3 秒的连续动作 → 帧数暴涨（12fps×5s=60 帧，图集 20MB+），拆成几个短动作或回 Live2D 做。
