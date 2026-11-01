# 技术任务清单：世界书驱动像素小镇（M0 / M1）

> 配套方案：`docs/llm-pixel-town-game-plan-2026-09-22.md`（世界书驱动版）
> 范围：**M0（骨架）+ M1（单 NPC 确定性规则层）**。本阶段零 LLM 调用、零 Schema、零世界差异，只把"游戏跑进 App + 一个据日程走动的 NPC"这条地基打通。
> 目标代码根：`android/app/src/main/java/com/aicompanion/game/`
> 前端根：`android/app/src/main/assets/game/`（Phaser 静态产物）

---

## 0. 工程接入点（已据此在真实代码中核实）

| 项 | 真实位置 / 模式 |
|----|----------------|
| 新 Activity 注册 | `android/app/src/main/AndroidManifest.xml`；仿 `.ui.AlarmActivity`（`NoTitleBar.Fullscreen`） |
| WebView 宿主先例 | `java/com/aicompanion/live2d/Live2DWebView.kt`（WebView 配置、JS 注入、截图等成熟用法） |
| 首页入口 | `java/com/aicompanion/ui/HomeActivity.kt`（加"进入小镇"入口） |
| DI 容器 | `java/com/aicompanion/AppContainer.kt`（全局单例，懒初始化各 Manager） |
| 像素渲染参考 | `java/com/aicompanion/pixelpet/`（`PixelAnimationEngine` Canvas 动画、`PixelPetView`、`PixelPetManager`、`ImageGenClient`）——MVP 像素资产与动画风格可复用其思路 |
| 存档 I/O 参考 | `java/com/aicompanion/storage/`、`ChatHistoryStorage.kt` |
| API 复用(后端阶段) | `network/ApiClient.sendChat()/sendChatWithToolLoop()`、`network/ProviderAdapter` |

> 编码正式开始前，**需将 `game/` 子系统补入 `android/PROJECT_INDEX.md`**（遵守项目索引维护规则），并同步 `README` 技术栈说明。

---

## 工具链决策（M0 前置）

- **前端：Vite + Phaser 3（TypeScript)**，源码放 `android/app/src/main/assets/game/frontend/`，构建产物输出到同目录 `dist/`（即 App 直接以 `assets/game/index.html` 加载）。
- **像素资产：MVP 用 CC0 像素包 + 复用 `pixelpet` 已验证的像素风格**，不依赖 AI 生图，避免拖慢 M0/M1。
- **Kotlin 侧 I/O：沿用现有 `storage/` SharedPreferences/文件 模式**，不引入新依赖。
- 桥接协议：统一为 JSON 事件串 `{type, payload}`，Kotlin 与 TS 各维护一份类型映射。

---

## M0 —— 骨架：Phaser 场景跑进 App + 桥打通 + 可移动角色

| # | 任务 | 产出文件（新） | 验收 |
|---|------|--------------|------|
| T0.1 | 建 `game/` 包骨架 + `assets/game` 前端目录 + Vite/Phaser 工程初始化 | `game/` 子目录、`assets/game/frontend/`（package.json / vite.config / index.html / src/main.ts） | `npm run build` 产出可被 `assets/game/index.html` 加载的静态资源 |
| T0.2 | 注册 `PhaserHostActivity`（全屏无标题栏，横向/纵向屏幕适配） | `game/engine/PhaserHostActivity.kt` + `res/layout/activity_phaser_host.xml`（仅一个 WebView）+ Manifest 注册 | Manifest 中可查、可从首页唤起 |
| T0.3 | WebView 配置与 `JsBridge`（JavaScriptInterface） | `game/engine/JsBridge.kt` | WebView 开启 JS、DOM storage、hardwareAccel；`loadUrl("file:///android_asset/game/index.html")` |
| T0.4 | 最小 Phaser 场景（纯色背景 + FPS 文本 + 空 Scene） | `frontend/src/scenes/BootScene.ts` | App 内能看到 Phaser 画面与 FPS，不白屏 |
| T0.5 | 双向桥验证：Kotlin↔JS 各发一个事件互相触发 | `JsBridge.kt` + `frontend/src/bridge.ts` | 一方 `evaluateJavascript` 注入触发 JS 回调；JS 经 `bridge` 触达 Kotlin 并打日志 |
| T0.6 | 玩家精灵与移动（tilemap 地板 + 玩家像素图 + 虚拟摇杆/按键移动 + 边界碰撞） | `frontend/src/scenes/GameScene.ts`、`frontend/src/tiles.ts` | 可移动、不穿地图边界 |
| T0.7 | 首页入口：从 `HomeActivity` 加"进入小镇"按钮 → `startActivity(PhaserHostActivity)` | 改 `ui/HomeActivity.kt` | 首页点击可进游戏、返回正常 |

**M0 出口验收**：安装 → 首页 → 进游戏 → 看到像素地图与角色 → 可行走移动 → 双向桥有日志 → 退出返回首页不崩溃。

---

## M1 —— 单 NPC 确定性规则层（零 LLM）

目标：地图上存在 **1 个按日程自主走动的 NPC**，行为、位置、作息全部本地确定性驱动，可存档还原。

| # | 任务 | 产出文件（新） | 验收 |
|---|------|--------------|------|
| T1.1 | 基础数据模型与时钟：`TimeSystem`（可变速游戏时钟、日/阶段枚举）、地图格子/cell 模型 | `game/logic/TimeSystem.kt`、`game/world/MapModels.kt` | 时钟推进、可按阶段判定（晨/午/夜） |
| T1.2 | Npc 状态与反射层 FSM：`NpcState`(位置/朝向/行为) + 状态机（Idle/Walk/Work/Sleep）+ Utility 选择 | `game/npc/NpcModels.kt`、`game/npc/FsmReflexLayer.kt` | 状态随阶段切换，无 LLM、纯本地 |
| T1.3 | 日程系统：一份 NPC 日程表（时间→目的地/行为），到点触发移动 | `game/npc/NpcScheduleSystem.kt` | 到点 NPC 切换目标并可演示 |
| T1.4 | 前端渲染 NPC：加载 NPC 精灵，按桥注入的位置/朝向更新 | `frontend/src/npc.ts` | 精灵跟随日程移动、朝向正确、可翻转 |
| T1.5 | 移动实现：MVP 用直线插值 + 预留寻路接口；地图复杂时接 `AstarService` 接口 | `game/logic/MovementSystem.kt`（+ `npc/AstarService.kt` 接口） | 直线可行走即满足；接口签名预留 A* |
| T1.6 | 存档骨架：`SaveManager` 本地 JSON 持久化（时间/NPC 位置/日程进度），I/O 与玩法解耦，预留后续 `WorldState` 扩展 | `game/logic/SaveManager.kt` | 重启后时钟与 NPC 位置正确还原 |
| T1.7 | 更新项目索引 | 改 `android/PROJECT_INDEX.md` | `game/` 子系统被索引收录 |

**M1 出口验收**：开启游戏后该 NPC 按日程在地图内自主走动、按作息"回家睡觉/外出工作"；杀掉进程重启后位置与时间还原；全程**无任何 LLM 网络调用**（可用日志/抓包确认）。

---

## 明确的非目标（本期刻意不做）

- WebView/桥安全加固、白名单命令集 → M3 之前再做（M0/M1 桥仅限 Hello/移动指令）。
- 后端 `backend/` 接口、`WorldCompiler`、Schema、样式套壳、LLM NPC、世界呼吸、好感度、种田循环 → 全部 M2+。
- `gameplay_bundle` 扩展位、L2 轻量层 → 本期仅预留设计空位，不实现。

---

## 风险与应对（M0/M1 阶段）

| 风险 | 应对 |
|------|------|
| Phaser 前端在低端机掉帧 | MVP 保持低精灵密度；`GameScene` 开启 Phaser 性能优化（`powerPreference`、关闭无用 effect） |
| `file:///android_asset` 加载 WebView 跨域/外域资源被拦 | 全部资源打包进 assets，不依赖 CDN；必要时 `network_security_config` 放行 |
| JS↔Kotlin 桥事件不同步 | 所有通信走统一 JSON 事件 + 单一线程派发；`evaluateJavascript` 主线程 |
| WebView 内存/泄漏 | 复用 `Live2DWebView` 的销毁与 `onPause/onResume` 处理经验 |

---

## 下一步

1. M0/M1 七个 + 七个任务已可并行为两条支线：前端（TS/Vite/Phaser）与 Kotlin（引擎/调度/存档）。
2. 确认后即可开始 **M0** 编码；M1 依据 M0 的桥与地图地基展开。
3. M2（编译器）将在 M1 闭环评审后再拆分，避免前置过度设计。