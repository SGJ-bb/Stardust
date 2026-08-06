# 星尘 Android 端 UI 全面重构 — 设计规范

> 日期: 2026-06-22
> 状态: 待审核
> 范围: Android 端全面 Jetpack Compose 化 + 12套主题 Light/Dark 全覆盖

***

## 1. 项目背景

### 现状问题

| 问题                 | 详情                                               |
| ------------------ | ------------------------------------------------ |
| **God Activity**   | MainActivity.kt 2656 行，所有功能塞在一个 Activity         |
| **技术栈过时**          | 传统 XML Layout + 命令式 UI + findViewById，未用 Compose |
| **主题不完整**          | 9 套配色方案全部仅 Dark 模式，缺 Light 变体；与 PC 端 12 套不同步     |
| **无架构模式**          | ViewModel 是空壳，业务逻辑全在 Activity 层                  |
| **PC/Android 不一致** | React 端组件化良好，Android 端风格老旧                       |

### 目标

1. **全面迁移到 Jetpack Compose** — Material3 + Navigation Component
2. **同步 PC 端设计语言** — 与 stradust-pc 的 12 套主题完全一致
3. **每套主题 Light + Dark 双模式** — 共 22 组完整配色
4. **拆分 God Activity** — 按功能域分解为独立 Screen/ViewModel
5. **建立组件库** — 可复用的 Compose 组件集

***

## 2. 设计语言：同步 PC 端

### 设计原则

* **玻璃态质感** (Glassmorphism) — 半透明表面 + 微妙边框 + 柔和阴影

* **渐变主色** — 按钮、气泡、头像使用主题渐变色

* **圆角一致** — 统一 12px\~24px 圆角体系

* **动效流畅** — Compose Animation API 实现过渡动画

### 12 套主题总览

| #  | ID        | 名称   | Light | Dark | 主色调           |
| -- | --------- | ---- | ----- | ---- | ------------- |
| 1  | sakura    | 樱粉   | ✅     | ✅    | `#ec4899` 粉红  |
| 2  | peach     | 桃粉   | ✅     | ✅    | `#f97316` 橙色  |
| 3  | violet    | 紫罗兰  | ✅     | ✅    | `#8b5cf6` 紫色  |
| 4  | ocean     | 海蓝   | ✅     | ✅    | `#3b82f6` 蓝色  |
| 5  | emerald   | 翡翠   | ✅     | ✅    | `#10b981` 绿色  |
| 6  | sunset    | 日落   | ✅     | ✅    | `#f59e0b` 金黄  |
| 7  | rosegold  | 玫瑰金  | ✅     | ✅    | `#e11d48` 玫瑰红 |
| 8  | mint      | 薄荷   | ✅     | ✅    | `#14b8a6` 青色  |
| 9  | midnight  | 暗夜   | ❌     | ✅    | `#6366f1` 靛蓝  |
| 10 | tea       | 茶香   | ✅     | ✅    | `6b8e5a` 茶绿   |
| 11 | cyberpunk | 赛博朋克 | ❌     | ✅    | `#00f0ff` 霓虹青 |
| 12 | chinese   | 华夏风韵 | ✅     | ✅    | `#c53d43` 朱砂红 |

### 配色 Token 系统（每套主题通用）

```
┌─────────────────────────────────────────────┐
│  Design Tokens (Compose ColorScheme)        │
├─────────────────────────────────────────────┤
│  background     → 页面背景色                │
│  backgroundAlt  → 次级背景（列表/滚动区）    │
│  surface        → 卡片/面板背景             │
│  surfaceVariant→ 可交互表面（按钮背景）      │
│                                             │
│  primary        → 主操作色（CTA按钮）       │
│  onPrimary      → 主操作色上的文字           │
│  primaryContainer → 主色容器（填充区域）     │
│                                             │
│  secondary      → 次要操作色                │
│  onSecondary    → 次要色上的文字             │
│  secondaryContainer → 次要色容器            │
│                                             │
│  accent         → 强调色（链接/标签）        │
│  onAccent       → 强调色上的文字             │
│                                             │
│  outline        → 边框/分割线               │
│  outlineVariant→ 弱化边框                   │
│                                             │
│  textPrimary    → 主文字                     │
│  textSecondary  → 次级文字                   │
│  textMuted      → 弱化文字（placeholder）   │
│  textDisabled   → 禁用文字                   │
│                                             │
│  userBubble     → 用户气泡渐变               │
│  aiBubble       → AI气泡背景                 │
│  glow           → 发光叠加层                 │
│  themeGradient  → 主题品牌渐变              │
└─────────────────────────────────────────────┘
```

***

## 3. 架构方案

### 3.1 目录结构（重构后）

```
android/app/src/main/java/com/aicompanion/
├── StradustApp.kt                    # Application 入口
│
├── theme/                            # ★ 主题系统（新增）
│   ├── StradustTheme.kt             # Compose Theme + ColorScheme 定义
│   ├── ThemeTokens.kt               # 12×22 组配色数据类
│   ├── LightColorSchemes.kt         # 10 套浅色 Scheme
│   ├── DarkColorSchemes.kt          # 12 套暗色 Scheme
│   └── ThemeExtensions.kt           # Modifier 扩展（渐变、发光等）
│
├── ui/                               # ★ UI 层（重写）
│   ├── navigation/
│   │   └── StradustNavHost.kt       # Navigation Graph
│   │
│   ├── screens/                      # 各功能页面（从 MainActivity 拆出）
│   │   ├── chat/
│   │   │   ├── ChatScreen.kt        # 聊天主页
│   │   │   ├── ChatViewModel.kt
│   │   │   ├── components/
│   │   │   │   ├── MessageBubble.kt
│   │   │   │   ├── InputBar.kt
│   │   │   │   └── Toolbar.kt
│   │   │   └── VoiceButton.kt
│   │   │
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   └── SettingsViewModel.kt
│   │   │
│   │   ├── diary/
│   │   │   ├── DiaryScreen.kt
│   │   │   └── DiaryViewModel.kt
│   │   │
│   │   ├── world/
│   │   │   ├── VirtualWorldScreen.kt
│   │   │   └── WorldViewModel.kt
│   │   │
│   │   ├── album/
│   │   │   ├── AlbumScreen.kt
│   │   │   └── AlbumViewModel.kt
│   │   │
│   │   ├── checkin/
│   │   │   ├── CheckInScreen.kt
│   │   │   └── CheckInViewModel.kt
│   │   │
│   │   ├── achievement/
│   │   │   ├── AchievementScreen.kt
│   │   │   └── AchievementViewModel.kt
│   │   │
│   │   └── profile/
│   │       ├── ProfileScreen.kt
│   │       └── ProfileViewModel.kt
│   │
│   ├── components/                   # ★ 通用组件库
│   │   ├── StradustCard.kt          # 卡片容器
│   │   ├── StradustButton.kt        # 渐变按钮
│   │   ├── StradustInput.kt         # 输入框
│   │   ├── StradustTopBar.kt        # 顶栏
│   │   ├── StradustBottomBar.kt     # 底部导航
│   │   ├── StradustChip.kt          # 标签/筛选器
│   │   ├── StradustDialog.kt        # 对话框
│   │   ├── StradustSheet.kt         # 底部抽屉
│   │   ├── Live2DView.kt            # Live2D 容器
│   │   └── MarkdownText.kt          # Markdown 渲染
│   │
│   └── MainActivity.kt              # ★ 瘦身为 NavHost 容器 (<100 行)
│
├── data/                             # 数据层（不变）
├── domain/                           # 领域层（不变）
├── service/                          # 服务层（不变）
├── coordinator/                      # 协调器（保留，适配 Compose）
└── utils/                            # 工具类（不变）
```

### 3.2 导航结构

```
StradustNavHost (MainActivity)
├── ChatScreen (首页, 根路由)
│   ├── 设置面板 (bottom sheet)
│   ├── 功能菜单 (bottom sheet)
│   ├── 语音录制 (overlay)
│   └── 图片预览 (dialog)
│
├── SettingsScreen (设置)
│   ├── 主题选择页
│   ├── 外观模式页
│   └── TTS 设置页
│
├── DiaryScreen (日记)
├── VirtualWorldScreen (虚拟世界)
├── AlbumScreen (相册)
├── CheckInScreen (签到)
├── AchievementScreen (成就)
└── ProfileScreen (个人中心)
```

### 3.3 主题系统核心 API

```kotlin
// === 使用方式 ===
@Composable
fun StradustApp(themeId: String, darkMode: Boolean?) {
    val themeState = rememberThemeState(themeId, darkMode)

    StradustTheme(
        colorScheme = themeState.colorScheme,
        darkTheme = themeState.isDark
    ) {
        Surface {
            StradustNavHost()
        }
    }
}

// === 切换主题 ===
// 自动 recomposition，无需 recreate Activity
themeState.setTheme("ocean")       // 切换到海蓝主题
themeState.setDarkMode(true)       // 切换到暗色
themeState.setDarkMode(null)       // 跟随系统

// === 在任意 Composable 中使用颜色 ===
val colors = StradustTheme.colors   // 当前主题色彩
Box(modifier = Modifier.background(colors.userBubble)) { ... }
```

***

## 4. 组件规范

### 4.1 核心组件清单

| 组件                   | 说明           | 对应原代码位置                       |
| -------------------- | ------------ | ----------------------------- |
| `MessageBubble`      | 用户/AI 气泡     | MainActivity L400-L600        |
| `ChatInputBar`       | 输入栏+发送+语音    | MainActivity L700-L900        |
| `ChatToolbar`        | 顶栏(头像+名称+状态) | activity\_main.xml            |
| `FeaturePanel`       | 功能菜单网格       | showFeaturePanel() L965-L1094 |
| `SettingsPanel`      | 设置面板         | showSettingsPanel()           |
| `VoiceButton`        | 语音录制按钮       | 语音相关方法                        |
| `Live2DView`         | Live2D 模型渲染  | Live2D 相关                     |
| `DiaryEditor`        | 日记编辑器        | 日记相关方法                        |
| `VirtualWorldCanvas` | 虚拟世界画布       | 世界相关方法                        |
| `AlbumGrid`          | 相册网格         | 相册相关方法                        |
| `CheckInCard`        | 签到卡片         | 签到相关方法                        |
| `AchievementGrid`    | 成就网格         | 成就相关方法                        |

### 4.2 组件设计原则

1. **无状态 Composable** — 所有状态通过参数传入或由 ViewModel 管理
2. **主题感知** — 全部使用 `StradustTheme.colors` 获取颜色
3. **预览友好** — 每个 Screen 都有 `@Preview` 注解
4. **可组合性** — 小组件可自由组合成大界面

***

## 5. 迁移策略（分阶段）

### Phase 1: 基础设施（优先级最高）

1. **创建主题系统**

   * `StradustTheme.kt` — CompositionLocal Provider

   * `ThemeTokens.kt` — 12×22 组配色数据

   * `LightColorSchemes.kt` / `DarkColorSchemes.kt`

   * 替换现有 `ThemeManager.kt` 和 `colors.xml`

2. **搭建导航骨架**

   * 引入 `navigation-compose`

   * 创建 `StradustNavHost.kt`

   * `MainActivity` 瘦身

3. **基础组件库**

   * StradustCard, StradustButton, StradustInput, StradustTopBar, StradustBottomBar

### Phase 2: 核心聊天界面

1. **ChatScreen** — 从 MainActivity 拆出聊天逻辑
2. **ChatViewModel** — 聊天状态管理
3. **MessageBubble** — 气泡组件（支持渐变、Markdown、图片）
4. **ChatInputBar** — 输入栏（文本+语音+发送）
5. **ChatToolbar** — 顶栏（头像+在线状态+菜单）

### Phase 3: 功能页面逐个迁移

1. **SettingsScreen** — 含主题选择器、外观切换
2. **DiaryScreen** — 日记编辑+列表
3. **VirtualWorldScreen** — 虚拟世界
4. **AlbumScreen** — 相册浏览
5. **CheckInScreen** — 签到系统
6. **AchievementScreen** — 成就展示
7. **ProfileScreen** — 个人中心

### Phase 4: 打磨 & 清理

1. 动画过渡效果
2. 无障碍支持
3. 清理旧 XML 布局文件
4. 移除旧 ThemeManager / ViewBinding 兼容代码
5. 性能优化（LazyColumn、remember 等）

***

## 6. 技术依赖

```gradle
// build.gradle 新增依赖
dependencies {
    // Compose BOM (锁定版本)
    implementation platform('androidx.compose:compose-bom:2024.06.00')

    // Compose 核心
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.foundation:foundation'
    implementation 'androidx.compose.animation:animation'

    // 导航
    implementation 'androidx.navigation:navigation-compose:2.7.7'

    // ViewModel
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'

    // LiveData -> StateFlow 桥接
    implementation 'androidx.compose.runtime:runtime-livedata'

    // Activity Compose
    implementation 'androidx.activity:activity-compose:1.9.0'

    // ConstraintLayout Compose
    implementation 'androidx.constraintlayout:constraintlayout-compose:1.0.1'

    // Coil (图片加载)
    implementation 'io.coil-kt:coil-compose:2.6.0'

    // Markdown 渲染 (可选)
    implementation 'com.halilibo.richtext:richtext-commonmark:0.20.2'
}
```

***

## 7. 关键决策记录

| 决策      | 选择                          | 理由                          |
| ------- | --------------------------- | --------------------------- |
| UI 框架   | Jetpack Compose             | Google 官方推荐，声明式 UI，性能优于 XML |
| 设计规范    | 同步 PC 端                     | 三端统一体验，减少维护成本               |
| 主题数量    | 12 套                        | 与 PC 端 1:1 对齐               |
| 暗夜/赛博朋克 | 仅 Dark                      | 这两套主题本质是纯暗色美学               |
| 架构模式    | MVI (MVVM + Unidirectional) | 适合 Compose 的单向数据流           |
| 导航方案    | Navigation Component        | Google 推荐，类型安全路由            |
| 图片加载    | Coil Compose                | Compose 原生支持，轻量             |
| 状态管理    | ViewModel + StateFlow       | Android 官方推荐                |

***

## 8. 风险与缓解

| 风险                     | 影响 | 缓解措施                                    |
| ---------------------- | -- | --------------------------------------- |
| Live2D SDK 不兼容 Compose | 高  | 用 `AndroidView` 包装原生 View 作为过渡          |
| 迁移期间功能回归               | 中  | 逐页面迁移，每个 Phase 可编译运行                    |
| 旧 ThemeManager 兼容      | 低  | 保留旧接口作为 facade，内部委托给新系统                 |
| 性能回退（重组开销）             | 中  | 使用 `remember`/`derivedStateOf`/`key` 优化 |

