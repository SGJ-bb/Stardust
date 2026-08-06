# BUG 日志

## BUG-08: 本地模型页面 null.length 崩溃 + 多页面空值安全问题

- **发现日期**: 2026-06-07
- **严重程度**: 严重（P0）
- **模块**: 多个页面组件 + Mock 数据层
- **文件**: `LocalModelPage.tsx`, `AlarmPage.tsx`, `BedtimeRadioPage.tsx`, `SkinShopPage.tsx`, `ModelManagerPage.tsx`, `ChatHistoryPage.tsx`, `GroupChatListPage.tsx`, `StickerPage.tsx`, `GroupChatPage.tsx`, `tauri.ts`

### 问题描述
点击"本地模型"等页面时报错 `Cannot read properties of null (reading 'length')`，页面白屏崩溃。

### 根因分析
1. `tauri.ts` 的 `getMockData` 函数缺少多个命令的 mock case，落入 default 返回 `null`
2. 页面组件中 API 回调未做 null 防御，`setModels(list)` 将 null 写入 state
3. JSX 中 `models.length` 在 null 上调用导致崩溃

### 修复方案（已实施）
1. **Mock 数据补全**：添加 `get_alarms`、`get_radio_list`、`get_skin_list`、`activate_code`、`purchase_skin` 等 case
2. **API 回调空值保护**：所有 `.then((list) => setXxx(list))` 改为 `.then((list) => setXxx(list ?? []))`
3. **组件防御性编程**：`pack.stickers.length` → `pack.stickers?.length ?? 0`，`member.name[0]` → `member.name?.[0] ?? "?"`

### 状态: 已修复

---

## BUG-09: TitleBar 文字与内容区标题视觉重叠

- **发现日期**: 2026-06-07
- **严重程度**: 高
- **模块**: 布局 (TitleBar + AppLayout)
- **文件**: `src/components/layout/TitleBar.tsx`

### 问题描述
TitleBar 横跨全屏，"星尘 Stradust" 文字位于 Sidebar 正上方，与内容区页面标题视觉重叠。

### 修复方案（已实施）
将 TitleBar 拆分为左右两部分：左侧与 Sidebar 等宽（显示 logo + 拖拽区域），右侧与内容区等宽（显示应用名称 + 窗口控制按钮）。

### 状态: 已修复

---

## BUG-07: persona.tags undefined 导致页面崩溃

- **发现日期**: 2026-06-07
- **严重程度**: 严重（P0）
- **模块**: 角色卡片渲染 + persist 数据恢复
- **文件**: `src/pages/Home/PersonaCard.tsx`, `src/pages/Home/HomePage.tsx`, `src/pages/Chat/ChatSidebar.tsx`, `src/pages/Persona/PersonaDetailPage.tsx`, `src/pages/Persona/PersonaEditorPage.tsx`, `src/stores/usePersonaStore.ts`

### 问题描述
刷新页面后首页报错 `Cannot read properties of undefined (reading 'slice')`，页面显示错误界面无法使用。

### 根因分析
1. `usePersonaStore` 使用 zustand persist 将 `personas` 持久化到 localStorage
2. persist 配置没有 `version` 和 `migrate` 函数，旧版本数据恢复时不会补全缺失字段
3. 通过 `addPersona()` 创建的角色对象中 `tags` 字段为 `undefined`（`PersonaCreateParams` 中 `tags` 是必填，但运行时可能未传入）
4. `PersonaCard.tsx:77` 调用 `persona.tags.slice(0, 3)` 时 `tags` 为 `undefined` 导致崩溃

### 受影响位置（5处）
1. `PersonaCard.tsx:77` — `persona.tags.slice(0, 3)` → `(persona.tags ?? []).slice(0, 3)`
2. `HomePage.tsx:27` — `p.tags.some(...)` → `(p.tags ?? []).some(...)`
3. `ChatSidebar.tsx:82` — `persona.tags.map(...)` → `(persona.tags ?? []).map(...)`
4. `PersonaDetailPage.tsx:100` — `persona.tags.map(...)` → `(persona.tags ?? []).map(...)`
5. `PersonaEditorPage.tsx:40` — `persona.tags.join(", ")` → `(persona.tags ?? []).join(", ")`

### 修复方案（已实施）
1. **组件防御性编程**：5 处 `persona.tags` 改为 `persona.tags ?? []`
2. **persist 数据迁移**：在 `usePersonaStore` 的 persist 配置中添加 `version: 1` 和 `migrate` 函数，为旧版本数据补全所有可能缺失的字段（tags, modelId, voiceId, favorability 等）

### 状态: 已修复

---

## BUG-06: 虚拟世界侧边栏导航后页面空白

- **发现日期**: 2026-06-07
- **严重程度**: 高
- **模块**: 侧边栏导航 (Sidebar)
- **文件**: `src/components/layout/Sidebar.tsx`

### 问题描述
点击侧边栏"虚拟世界"按钮后，页面空白无内容。路由定义为 `/world/:worldId`，但侧边栏导航到 `/world`（无 worldId 参数），导致路由不匹配。

### 根因分析
`getNavPath` 函数只为需要 `personaId` 的路由拼接参数，虚拟世界需要 `worldId` 但未被处理。

### 修复方案（已实施）
在 `getNavPath` 中添加虚拟世界特殊处理：`/world` → `/world/default`

### 状态: 已修复

---

## BUG-05: Tauri dev 重启后数据库重置

- **发现日期**: 2026-06-07
- **严重程度**: 高（P1）
- **模块**: 数据持久化
- **文件**: `src-tauri/src/` 数据库初始化逻辑

### 问题描述
每次 `pnpm tauri dev` 重启后，所有数据（角色、聊天记录、设置、API Key 配置等）全部丢失，数据库被重置为空状态。

### 复现步骤
1. 运行 `pnpm tauri dev`，创建角色、配置 API Key
2. 关闭应用
3. 再次运行 `pnpm tauri dev`
4. 所有数据丢失，回到初始空状态

### 预期行为
应用数据应持久化，重启后数据不丢失。

### 根因分析
**已确认根因：WAL 模式无 checkpoint + 进程被杀时数据丢失**

1. 数据库启用了 WAL (Write-Ahead Logging) 模式，所有写操作先写入 WAL 文件 (`stradust.db-wal`)，主数据库文件 (`stradust.db`) 不会立即更新
2. 整个项目中没有任何地方执行 WAL checkpoint，`Database` 结构体也没有实现 `Drop` trait
3. 当 `tauri dev` 被终止时（Ctrl+C、代码热重载），进程被强杀，Rust 的 `Drop` 析构器不会执行，`sqlite3_close()` 永远不会被调用
4. 结果：WAL 文件中的数据没有被合并回主数据库文件

**验证证据：**
- `stradust.db` 仅 4096 字节（只有 schema，无数据）
- `stradust.db-wal` 688,072 字节（672KB，包含所有用户数据）
- `stradust.db-shm` 32,768 字节
- 主数据库文件最后修改 2026/6/5，WAL 文件最后修改 2026/6/7

### 修复方案（已实施）
1. **启动时 checkpoint**：在 `Database::open()` 中启用 WAL 后立即执行 `PRAGMA wal_checkpoint(TRUNCATE)`，恢复上次未完成的数据
2. **Drop trait**：为 `Database` 实现 `Drop`，关闭时执行 checkpoint
3. **窗口关闭监听**：在 `lib.rs` 中监听 `CloseRequested` 事件
4. **公开 checkpoint 方法**：添加 `Database::checkpoint()` 供外部调用

### 状态: 已修复

---

## BUG-04: 前后端 Tauri 命令名大量不匹配（严重）

- **发现日期**: 2026-06-07
- **严重程度**: 严重（P0）
- **模块**: 前端桥接层 (src/lib/tauri.ts)
- **文件**: `src/lib/tauri.ts` vs `src-tauri/src/lib.rs`

### 问题描述
前端 `tauri.ts` 中调用的 Tauri 命令名与后端 `lib.rs` 中注册的命令名存在大量不匹配，导致所有 Tauri 命令调用失败（返回 "Command xxx not found" 错误）。

### 影响范围
- 前端共调用 66 个命令，仅 16 个与后端完全匹配
- 26 个命令名不一致（功能对应但命名风格不同）
- 24 个命令后端完全不存在（闹钟/电台/激活码/本地模型/皮肤商店等模块后端未实现）

### 已修复的命令名映射

| 前端旧命令名 | 后端实际命令名 | 状态 |
|-------------|--------------|------|
| `stream_chat` | `send_chat_stream` | ✅已修复 |
| `send_message` | `send_chat` | ✅已修复 |
| `toggle_favorite_message` | `toggle_favorite` | ✅已修复 |
| `get_persona_list` | `list_personas` | ✅已修复 |
| `get_memories` | `list_memories` | ✅已修复 |
| `create_memory` | `add_memory` | ✅已修复 |
| `text_to_speech` | `speak` | ✅已修复 |
| `start_recording` | `start_voice_record` | ✅已修复 |
| `stop_recording` | `stop_voice_record` | ✅已修复 |
| `save_settings` | `update_settings` | ✅已修复 |
| `get_group_chat_list` | `list_group_chats` | ✅已修复 |
| `get_achievements` | `list_achievements` | ✅已修复 |
| `get_diary_list` | `list_diaries` | ✅已修复 |
| `create_diary` | `generate_diary` | ✅已修复 |
| `get_moments` | `list_moments` | ✅已修复 |
| `publish_moment` | `create_moment` | ✅已修复 |
| `like_moment` | `toggle_like` | ✅已修复 |
| `comment_moment` | `add_comment` | ✅已修复 |
| `get_calendar_events` | `list_events` | ✅已修复 |
| `create_calendar_event` | `add_event` | ✅已修复 |
| `delete_calendar_event` | `delete_event` | ✅已修复 |
| `get_sticker_packs` | `list_stickers` | ✅已修复 |
| `import_sticker_pack` | `add_sticker` | ✅已修复 |
| `get_time_capsules` | `list_capsules` | ✅已修复 |
| `create_time_capsule` | `create_capsule` | ✅已修复 |
| `open_time_capsule` | `open_capsule` | ✅已修复 |
| `get_world_config` | `get_virtual_world` | ✅已修复 |
| `update_world_lore` | `update_world_config` | ✅已修复 |
| `get_model_list` | `list_models` | ✅已修复 |

### 仍需后端实现的命令（24个）

`get_chat_history`, `get_chat_sessions`, `delete_message`, `speech_to_text`, `control_live2d`, `get_group_chat`, `get_check_in_status`, `get_growth`, `get_world_state`, `create_world`, `trigger_world_evolution`, `upload_world_image`, `get_alarms`, `create_alarm`, `delete_alarm`, `get_radio_list`, `play_radio`, `activate_code`, `get_local_models`, `download_local_model`, `delete_local_model`, `get_skin_list`, `purchase_skin`

### 根因分析
前后端开发时采用了不同的命名风格：前端倾向 `get_xxx_list` / `create_xxx` 风格，后端倾向 `list_xxx` / `add_xxx` 风格。缺少统一的命名规范和接口文档。

### 状态: 已修复（26个命令名已更正，24个后端未实现的命令待后端开发）

---

## BUG-01: 主页搜索框清空后状态未重置

- **发现日期**: 2026-06-07
- **严重程度**: 中
- **模块**: 主页 (HomePage)
- **文件**: `src/pages/Home/HomePage.tsx`

### 问题描述
在搜索框输入关键词后，使用 `fill ""` 或 `Ctrl+A + Delete` 清空搜索框，页面仍显示"没有找到匹配的角色"，而非"还没有角色，点击上方按钮创建"。

### 复现步骤
1. 打开主页 `/`
2. 在搜索框输入"测试搜索"
3. 页面过滤后显示"没有找到匹配的角色"
4. 清空搜索框（Ctrl+A + Delete）
5. 页面仍显示"没有找到匹配的角色"

### 预期行为
搜索框清空后，searchQuery 应重置为空字符串，页面应显示全部角色列表或"还没有角色，点击上方按钮创建"。

### 根因分析
可能原因：React 的 `onChange` 事件在 agent-browser 的 `fill ""` 操作中未正确触发，导致 `searchQuery` 状态未更新。但使用键盘 Ctrl+A + Delete 也未正确重置，说明可能是 React 状态管理问题。

### 状态: 待修复

---

## BUG-02: 暗色模式开关切换后主题样式未生效

- **发现日期**: 2026-06-07
- **严重程度**: 高
- **模块**: 外观设置 (AppearanceSettings)
- **文件**: `src/pages/Settings/AppearanceSettings.tsx`, `src/components/common/ThemeProvider.tsx`

### 问题描述
在设置→外观中点击暗色模式开关，开关状态从 unchecked 变为 checked，但 CSS 变量 `--color-bg` 仍为 `#fef7f7`（浅粉色），页面视觉样式未切换为暗色。

### 复现步骤
1. 打开设置页面 `/settings`
2. 点击"外观"分组
3. 点击暗色模式开关
4. 开关变为 checked=true
5. 检查 `getComputedStyle(document.documentElement).getPropertyValue('--color-bg')` 仍为 `#fef7f7`

### 预期行为
暗色模式开关切换后，CSS 变量应更新为暗色值（如 `#1a1a2e`），页面背景变为深色。

### 根因分析
可能原因：
1. ThemeProvider 中的 `darkMode` 状态未正确更新
2. CSS 变量切换逻辑未绑定到 darkMode 状态
3. `useThemeStore` 的 `toggleDarkMode` 方法未被正确调用

### 状态: 待修复

---

## BUG-03: 聊天发送消息后输入框未清空

- **发现日期**: 2026-06-07
- **严重程度**: 中
- **模块**: 聊天页面 (ChatPage)
- **文件**: `src/pages/Chat/ChatPage.tsx`, `src/components/chat/ChatInput.tsx`

### 问题描述
在聊天页面输入消息并点击发送后，输入框文本未清空，消息也未出现在消息列表中。

### 复现步骤
1. 打开聊天页面 `/chat/{personaId}`
2. 在输入框输入"你好小星，今天天气怎么样？"
3. 点击发送按钮
4. 输入框文本未清空
5. 消息列表中无新消息气泡

### 预期行为
发送消息后：
1. 输入框文本应清空
2. 用户消息应出现在消息列表中
3. 如果 API 调用失败，应显示错误提示

### 根因分析
可能原因：
1. Tauri 后端未配置 API Key，`streamChat` 调用失败但错误被静默吞掉
2. `handleSend` 中的 `addUserMessage` 未被正确调用
3. `ChatInput` 的 `onSend` 回调未正确触发

### 状态: 待确认（需配置 API Key 后重新测试）
