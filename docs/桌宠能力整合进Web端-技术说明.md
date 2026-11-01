# 桌宠能力整合进 Web 端 · 技术说明

> 日期：2026-10-26
> 目标：把 `F:/image2psd2live/pet`（Electron 桌宠）里验证过的模型交互能力，移植进
> `F:/stradust/stradust-pc`（Vite + React + Tauri + `@jannchie/pixi-live2d-display`）。
> 原则：**不重写渲染层**，只补交互层；现有 `petfx`（序列帧）与情绪驱动不受影响。

---

## 一、整合了什么

| 能力 | 桌宠（Electron）原实现 | Web 端落地 | 文件 |
|---|---|---|---|
| 视线跟随 | `renderer/pet.js` 里 pointermove → 阻尼 → `ParamEyeBallX/Y` + `ParamAngleX/Y` | 同名逻辑重写为独立模块，`requestAnimationFrame` 阻尼插值，空闲 2.4s 回中 | `src/lib/petgaze.ts` |
| 部位命中 | PSD 图层导出命中区（`pet_zones_gen.py`），点击判定部位并反馈 | 加载 zones JSON → 屏幕坐标→归一化→面积最小优先 | `src/lib/petzones.ts` |
| 拖拽 + 惯性回弹 | 拖窗口 + 落地物理 | 改为**画布内拖模型**：甩动带惯性 + 弹簧回位（可选夹取范围） | `src/lib/petdrag.ts` |
| 表情 / 动作 / 特效面板 | 托盘菜单（右键 → 动作子菜单） | 组件化交互坞，数据从模型 `settings` 读，换模型自动换内容 | `src/components/live2d/PetDock.tsx` |
| 序列帧特效 | `frameanim.js` + 烘焙帧 | **原本已有** `lib/petfx.ts`（未改动，接进面板触发） | `src/lib/petfx.ts` |
| 呼吸 / 眨眼 / 口型 / 物理 | Cubism 运行时 | 同（`pixi-live2d-display` 内建） | — |

`Live2DCanvas.tsx` 增量改造：新增 `zonesPath / gaze / draggable / dock / onHit` 五个 props，
控制器新增 `getBounds()` 与 `playFrames()`，模型加载后自动挂载跟随、拖拽并回填表情/动作清单。

---

## 二、怎么用

```tsx
<Live2DCanvas
  modelPath={toLive2dUrl(activePersona?.live2dModelPath)}
  zonesPath={zonesUrlFor(activePersona?.live2dModelPath)}  // /models/zones_<key>.json
  gaze        // 视线跟随（默认开）
  dock        // 表情/动作/特效面板
  draggable   // 画布内拖拽+回弹（桌宠场景开，普通悬浮层建议关）
  onHit={(zone) => console.log("点到", zone.kind, zone.name)}
/>
```

命中区文件放在 `public/models/`：

```
public/models/zones_whale2.json    # 蓝鲸娘（鲸鱼帽兜款）
public/models/zones_claudia.json   # claudia 鲸鱼娘
```

文件名规则（`AppLayout.zonesUrlFor`）：`whale2.model3.json` → `/models/zones_whale2.json`；
`claudia_expr.model3.json` → `/models/zones_claudia.json`（自动去掉 `_expr/_v2/_pkg` 后缀）。
**文件不存在时静默降级**：`loadZones` 返回 null，点击交互关闭，渲染不受影响。

新增命中区：

```bash
F:/see-through/venv/Scripts/python.exe F:/image2psd2live/tools/pet_zones_gen.py <模型.psd> -o F:/stradust/stradust-pc/public/models/zones_<key>.json
```

---

## 三、命中区数据格式

`pet_zones_gen.py` 产出，与桌宠共用同一份：

```json
{
  "canvas": [1024, 1024],
  "content_bbox": [78, 7, 947, 1022],
  "zones": [
    { "name": "face", "kind": "head", "group": "face", "rect": [303, 265, 645, 596] },
    { "name": "hand_l", "kind": "hand", "group": "handwear", "rect": [573, 656, 734, 834] }
  ]
}
```

判定规则：先过 `content_bbox` 粗筛，再**面积最小者优先**（脸 > 头 > 身体），
避免"点头部整块吃掉脸部点击"。

默认反馈（`DEFAULT_ZONE_REACTION`，业务可覆盖）：

| 部位 | 反馈 |
|---|---|
| head / face | 爱心特效 + Love 表情 |
| body | Nod 动作 |
| hand / hair | sparkle 特效 |

---

## 四、技术要点（踩过的坑）

1. **参数要防御性写入**：不同模型参数不全（如早期模型无 `ParamEyeBallForm`），
   `setParameterValueById` 用 `try/catch` + 存在性判断，缺参数静默跳过，不能抛错打断渲染。
2. **清理顺序**：切模型时先 `cleanup` 跟随/拖拽的 rAF 与事件监听，再 `destroy` 旧模型，
   否则旧 rAF 会持续写已销毁模型的参数（内存泄漏 + 控制台刷错）。
3. **命中判定用 `getBounds()` 而非固定画布尺寸**：模型缩放/位移后包围盒会变，
   用 `model.getBounds()` 换算归一化坐标才跟手。
4. **拖拽必须 `setPointerCapture`**：指针移出 canvas 后仍要收到 move/up，
   否则会"甩到一半失联"卡在中间位置。
5. **构建验证**：`npx tsc --noEmit` 无新增报错；`npx vite build` 通过（35.2s）。

---

## 五、边界与后续

- 未移植：**窗口级**能力（透明无边框、置顶、逐像素点击穿透、系统托盘、HTTP 桥 7871）。
  这些依赖 Electron/Tauri 窗口 API，Web 端由 Tauri 侧或页面布局承担。
- 口型仍是单段（`ParamMouthOpenY`），Web 端接 LLM/TTS 时可直接写该参数。
- 无手臂参数（本模型手与大衣连体），"挥手"类动作用身体倾斜 + 摆头替代。

---

## 六、文件清单（本次新增/修改）

```
stradust-pc/
├─ src/lib/petgaze.ts                 新增  视线跟随
├─ src/lib/petzones.ts                新增  命中区加载/判定
├─ src/lib/petdrag.ts                 新增  拖拽+惯性回弹
├─ src/components/live2d/PetDock.tsx   新增  表情/动作/特效面板
├─ src/components/live2d/Live2DCanvas.tsx  修改  接入上述能力（+5 props）
├─ src/components/layout/AppLayout.tsx     修改  zonesUrlFor + gaze/dock 开关
└─ public/models/zones_whale2.json     新增  命中区数据（来源 image2psd2live）
└─ public/models/zones_claudia.json    新增
```
