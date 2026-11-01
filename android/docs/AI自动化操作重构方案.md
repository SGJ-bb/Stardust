# AI 手机自动化操作：重构方案（"让 AI 真正看到 UI 树"）

> 范围：`android/app/src/main/java/com/aicompanion` 下 `screen/`、`plugin/PhoneOperationPlugin`、`network/ApiClient`(手机操作相关方法) 的无障碍自动化能力重构。
> 目标：让 AI 从一个"操作手机的执行器"，升级为一个"看得懂当前页面层级结构、握得住稳定元素身份、带着屏幕记忆操作"的 Agent。

---

## 一、现状盘点（v2 已经做到什么）

当前实现已具备相当完整的基础，不是从零开始：

| 能力 | 现有实现 | 位置 |
|------|---------|------|
| 无障碍屏幕采集（多窗口/容器标签继承/深度上限/同步刷新） | `ScreenRecognitionService.extractStructured/walk/refreshAndWait` | `screen/ScreenRecognitionService.kt` |
| 手势执行（点击/长按/滑动/输入/全局动作/滚动），多级兜底 | `perform*` 系列 + `clickData()` | `screen/ScreenRecognitionService.kt` |
| 反应式闭环：观察→决策→行动→再观察 | `PhoneOperationPlugin.runReactiveLoop` | `plugin/PhoneOperationPlugin.kt` |
| 步骤断言（expect：出现/消失/包名/变化） | `AutoOperator.evalExpect` | `screen/AutoOperator.kt` |
| 视觉兜底（无障碍读不到时截图 OCR/LMM） | `PhoneOperationPlugin.tryVisionFallback` + `LocalModelManager` | `plugin/PhoneOperationPlugin.kt` / `localmodel/` |
| 技能学习闭环（成功后沉淀 UI 知识/workflow） | `SkillBuilder`/`AppSkillManager`/`SkillCreator` | `screen/`、`skill/` |
| 敏感页面检测（支付/验证码/登录） | `PhoneOperationPlugin.detectAbnormalByText` | `plugin/PhoneOperationPlugin.kt` |

**结论：不必推翻重建，问题集中在「感知的信息形态」和「决策的上下文记忆」这两层。**

---

## 二、核心痛点（为什么说"AI 没能真正看到 UI 树"）

### P0：感知层丢掉了"树"结构
`formatScreenForLLM()` 最终输出的是**一块压扁的缩进文本**：
```
页面：主界面 (com.xxx)
  [3] 微信 {TextView #tab_title}
  [12] 消息 {TextView}
  文本内容 ×5
  (scroll) 可滚动区域
```
- 父子/包含/兄弟关系在文本里无法表达 → LLM 无法推理"这个按钮属于这张卡片""这个弹窗盖在主界面上"。
- `ClickableData.path`（如 `0-2-1`）只在内部点击时用，**从不进 prompt** → AI 不知道节点在哪棵树上。
- 命中上限后被截断，AI 只看到前 N 个节点，无法感知"还有更深处"的分层。

### P0：没有稳定的元素身份（identity）
- `index` 是**位置的序号**，列表重排/加载后即失效。
- `path`（`0-2-1`）是**子索引路径**，内容动态变化时也漂移。
- bounds 坐标随布局/滚动变化。
- 结果：AI 每次刷新都要"重新认"一遍所有元素，无法像人一样"记住这个控件、在滚动后还能找到它"。这是"多点、多页、长流程"做不稳定的根因。

### P1：交互原语太少，表达力不足
动作集只有 click/tap/back/home/scroll/swipe/input/long_press/notifications/recents/wait/read。
- 没有：`set_checked`(开关)、`select`(列表项/Choice 选择)、`seek`(滑杆)、`clipboard_paste`、`drag`、`pinch`、`双击`、`focus`、`clear_text`。
- `input` 只能 `ACTION_SET_TEXT` 整段设置，无法模拟逐字符输入，App 里带输入检测（如游戏/金融键盘）时失效。

### P1：无"屏幕状态记忆"（screen graph）
反应式循环是**短视**的：只喂最近几步历史，LLM 不知道"我从哪来、路过哪几个页面、怎么回退"。复杂任务里极易原地打转或越走越远。

### P1：视觉兜底与 UI 树脱节
视觉兜底是"独立的截图→分析→一段文字"拼在树下面，**没有和可点击元素/边界框(bbox)建立映射**，AI 看得出有什么，点不动。

### P2：安全是"事后文本关键词"
靠 `detectAbnormalByText` 事后扫屏文字。没有：操作前确认 UI、App 级能力白名单、敏感动作(发送/删除/支付)"先问用户"的回路。

### P2：技能库是自然语言，非结构化
`uiMap`/`workflow` 是自然语言文本，靠 LLM 每次重读。没有结构化的"节点模板 + 动作策略"，命中率不稳定。

---

## 三、重构目标与设计原则

1. **让 AI 看到"真树"**：感知层输出**结构化、带层级、带稳定身份**的 UI 树（等价于浏览器的 accessibility DOM）。
2. **看得懂 + 点得准**：树上的每个动作目标要么有稳定 handle，要么能解释它当前在哪（树路径 + 坐标 + 文本/资源的联合指纹）。
3. **带着记忆操作**：维护"屏幕状态图(screen graph)"，AI 能知道已访问页面、能回退、能去重。
4. **一层感知、多源融合**：无障碍树为主，视觉(截图 bbox OCR/LMM)为辅，二者映射进同一个"树视图"。
5. **安全内建于交互**：敏感操作在动作层拦截，走"执行前确认"而不是"事后扫文本"。
6. **渐进迁移**：保留 v2 的闭环骨架，逐层替换不整体推倒。可灰度开关。

---

## 四、目标架构

```
┌────────────────────────────────────────────┐
│  决策层   PhoneOperationAgent (对话循环)     │
│           + ScreenGraph 屏幕状态图/记忆       │
│           + SkillStore 结构化技能             │
├────────────────────────────────────────────┤
│  交互原语 UIABus  (统一元素寻址/动作执行/回执) │
├────────────────────────────────────────────┤
│  感知层   UITree 视图模型 (真正的层级树)       │
│          ├─ Accessibility 采集器(保留 v2)     │
│          ├─ 稳定身份分配器 (id alloc + rebind)│
│          └─ 视觉融合器  VisionOverlay(截图bbox)│
├────────────────────────────────────────────┤
│  运行时   无障碍服务（采集/手势，保留）         │
│          录屏 MediaProjection（视觉兜底，保留）│
└────────────────────────────────────────────┘
```

---

## 五、关键设计（含具体数据结构与接口）

### 5.1 感知层：`UITree` 视图模型（本方案的核心）

**新增 `screen/UITree.kt`**，把无障碍节点序列化为**显式层级 JSON**，喂给 LLM 的是树而非扁平文本：

```json
{
  "package": "com.tencent.mm",
  "screen": "主界面",
  "w": 1080, "h": 2400,
  "nodes": [
    {
      "id": "n14",                 // 稳定身份(见5.2)
      "pid": "n3",                 // 父节点 id → 真正的树
      "role": "button",            // clickable→button / editable→textbox / text→text / checkbox / slider ...
      "text": "微信",
      "desc": "",
      "bounds": [90, 2100, 390, 2290],
      "center": [240, 2195],
      "clickable": true,
      "checked": null,
      "enabled": true,
      "selected": false,
      "scrollable": false,
      "src": "a11y"                // a11y | vision
    },
    { "id": "n15", "pid": "n14", "role": "text", "text": "3条新消息", ... }
  ],
  "windows": [ ... ],              // 附加窗口(弹窗/输入法/悬浮窗)，用 window 前缀区分
  "truncated": false
}
```

**喂给 LLM 的两种形态（可配置，`phoneOpScreenFormat = tree|text`）：**
- `tree`: 上面的紧凑 JSON，补充一段"如何用 id 引用节点"的说明。默认开启（这就是"让 AI 看到 UI 树"）。
- `text`: 保留现有压扁文本作为降级（弱模型/token 敏感场景）。
- 树过大时**不是粗暴截断**，而是：先给"当前视口可见节点"的完整树，并给出"隐藏子树摘要+数量"（类似浏览器 devtools 的 subtree hints），引导 AI 展开而非盲翻。

**预留字段与 v2 的对应：**
- `id` ↔ 替代原 `path`（内部点击改走 id→重定位）。
- `role` ↔ 由 `isClickable/isEditable/isScrollable/className` 聚合。
- 保留 `bounds/center` 供坐标兜底；保留 `hint`（`{cls #viewId}`）。

### 5.2 稳定元素身份：`UINodeId` 分配与重绑定（rebind）

**新增 `screen/UINodeIdentity.kt`**：

- 每个快照给"可交互节点"分配树内稳定 `n{seq}`，并用一组**联合指纹(fingerprint)**绑定：
  `fingerprint = hash(viewId * className * text * desc * 相对父窗口)`。
- 提供两个函数：
  - `bind(node) -> id`：本快照内建立 id↔节点。
  - `rebind(oldSnapshotFingerprints, newSnapshot)`：滚动/刷新后把上一帧的 id 重新映射到新帧的同逻辑节点（同指纹）。
- **AI 交互协议升级**：LLM 可以"记住"一个 id 跨多次采样使用，比如：
  `{"action":"click","target":"n14","if_gone":"scroll_into_view"}` —— 节点不在屏幕上时自动 `scroll_into_view` 或重定位后点击，而不用 AI 每次手算新的 index/坐标。
- 这是解决"多点、多页、长流程不稳定"的关键：AI 第一次定位到一个控件，后续持有该 id，无论列表怎么滚，系统负责把它找出来。

### 5.3 交互原语：`UIABus`（统一元素寻址 + 动作执行 + 回执）

**新增 `screen/UIABus.kt`（或扩展现有 `AutoOperator`）**，动作目标寻址层与执行层分离：

- **目标寻址(tuan goals)**：`target` 支持 `id:n14 | text:微信 | viewId:tab_title | index:3 | coord:(540,2100)`，统一先重定位再执行。
- **动作原语扩展**（新增）：
  - `set_checked`（开关 Switch/CheckBox，`{"checked":true}`）
  - `select`（Choice/ListItem，发 `ACTION_CLICK` 或走弹窗选择）
  - `seek_to`（SeekBar，坐标滑动到目标比例）
  - `clear_text` / `focus`
  - `clipboard_paste`（配合系统剪贴板）
  - `drag`（起点→终点）
  - `double_click`、`pinch`、`scroll_into_view`（原语化向上滑到节点可见）
  - `input` 增加 `use_ime=true`：改走 IME action（对带输入检测的 App 更真实）
- **每一次动作返回结构化回执**（而非仅 true/false）：
  ```
  {ok:true, target_found:"a11y", rebound:true, page:"主界面", proof:"出现:发送成功", cost_ms:120}
  ```
  回执喂回决策循环，让 AI 知道"这次到底有没有点对"。

### 5.4 决策层：`ScreenGraph` 屏幕状态图（记忆）

**新增 `screen/ScreenGraph.kt`**：
- 以 `(package, pageClass, 视觉特征hash)` 为节点，动作/跳转为边。
- 维护：
  - 当前节点 / 已访问节点集合 / 回边（back 应回到哪）。
  - "死胡同"标记（反复进入又退出的页面）。
- 给 LLM 的上下文里加入**「导航记忆」块**：
  ```
  页面历史: 主界面 → 聊天列表 → 对话页(当前)
  可回退: 聊天列表
  已尝试且未果: [密码页(视为障碍)]
  ```
- 配合 `AutoOperator.back()` 可让 AI 主动"回退到记忆中的某页"，而不是靠运气。
- 顺带解决"around 死循环"：连续 N 次回到同一页面节点即判定打转，触发换策略或询问用户。

### 5.5 视觉融合：`VisionOverlay`（截图 bbox 并入树）

**新增 `screen/VisionOverlay.kt`**，与现有 `LocalModelManager.analyzeCurrentScreen` 配合：
- 视觉模块返回 `[{text, bbox, label}]`。
- 与无障碍树按"中心点落在可点节点 bounds"做**合并**：无障碍树有节点 → 用无障碍；树为空/贫瘠 → 用视觉 bbox 生成**临时树节点**（`src:"vision"`），每个 bbox 也分配临时 id，可直接 click/tap。
- 这样"看得出的东西"也能"点得到"，两者不再脱节。同一 `UITree` 输出，模型无需区分来源。

### 5.6 安全层：动作前置拦截（interceptor）

**新增 `action/OperationGuard.kt`**：
- **App 级能力白名单**：`AppCategoryClassifier` 已有分类，扩展为"某类 App 允许哪些动作"（如金融/支付类 App 默认禁自动输入、禁发送）。
- **敏感动作确认回路**：发送/删除/转账/购买/提交 等动作触发时，回传 UI 界面（复用悬浮窗/嵌入式卡片）"先问用户"，用户确认后才执行。用结构化回执把"需要确认"返回给闭环，闭环临时挂起等待。
- **升级异常检测**：由"事后扫文本关键词"升级为树级规则（`role==button&&text=~支付/确认` 出现在目标页面时拦截），并可在设置页单独开关（保留现有 `abnormalDetectionEnabled`）。

### 5.7 技能升级：结构化技能（Skill v3）

把"自然语言 uiMap/workflow"升级为**双轨**：
- `uiMap` 仍保留自然语言（给人看 / 弱模型兜底）。
- 新增结构段 `layoutSpec`：
  ```
  {"screens":[{"name":"对话页","key_nodes":[{"role":"textbox","desc":"输入框"},{"role":"button","text":"发送"}]}]}
  ```
- 加载技能时，`layoutSpec` 用于 **预绑定**：把技能里的关键节点映射到当前树的稳定 id 上 → AI 每一步可以直接 `target:id`，不再每次从零找。
- 沿用现有 `SkillBuilder.summarizeSkill` 的归纳流程，仅扩展返回 schema 与匹配逻辑。

---

## 六、分阶段实施路线

### Phase 1 — 感知重构（"真正看到树"）
产出：`UITree`、`UINodeIdentity`、format 双形态 + 树降级。
- 改造 `ScreenRecognitionService.extractStructured/walk`，新增 `UITreeBuilder`。
- 改造 `AutoOperator.formatScreenForLLM*` 增加 `tree` 形态。
- 改造 `ApiClient.analyzePhoneOpStep` 的屏幕板块说明，增加 id/role 引用说明、`set_checked/select/seek` 等新动作。
- `PhoneOperationPlugin.parseDecision` 支持新的 target 寻址。
- **验收**：微信/淘宝/设置等常见 App 中，AI 能正确说出"某按钮属于某卡片/弹窗"，且树形场景下不再报"未找到可点击元素"。

### Phase 2 — 身份与记忆（"点得准 + 记得住"）
产出：`UIABus` 目标寻址、`ScreenGraph`、`scroll_into_view`、结构化回执。
- **验收**：多页长流程（如"搜索→加购物车→结算前停下"），节点在滚动后能 rebind，AI 不再重复找同一元素；能主动退回到历史页面。

### Phase 3 — 视觉融合 + 安全（"看得见也能点 + 敢让它做"）
产出：`VisionOverlay`、`OperationGuard` 白名单 + 确认回路。
- **验收**：无障碍树为空的自绘 App，视觉树能完成点击闭环；敏感动作必定先经过用户确认 UI。

### Phase 4 — 结构化技能 + 长任务
产出：`layoutSpec`、后台排队执行、断点续跑。
- **验收**：首次失败的任务二次成功率显著上升；某个任务可后台排队、会话恢复后续跑。

> 每阶段都以开关灰度（`SettingsManager` 加 `phoneOpScreenFormat / operationGuardEnabled` 等），风险可控。

---

## 七、与现有代码的映射（改哪些文件）

| 现有文件/方法 | 本次改动 |
|--------------|---------|
| `screen/ScreenRecognitionService.kt`（`extractStructured/walk/ClickableData`） | 保留采集与手势，抽 `UITreeBuilder` 序列化真树；身份分配接入 |
| `screen/AutoOperator.kt`（`formatScreenForLLM*`、`executeAction`、`evalExpect`） | 增加 tree 形态输出；`executeAction` 改走 `UIABus` 寻址；回执结构化；新增原语 |
| `plugin/PhoneOperationPlugin.kt`（`runReactiveLoop`/`tryVisionFallback`） | 接入 `ScreenGraph` 记忆；视觉改走 `VisionOverlay` 融合；安全改走 `OperationGuard` |
| `network/ApiClient.kt`（`analyzePhoneOpStep`） | 屏幕格式版块改讲 id/role；动作清单/expect 说明同步扩展 |
| `screen/SkillBuilder.kt` / `AppSkillManager.kt` / `skill/SkillCreator.kt` | 扩展 `layoutSpec` 结构段 + 预绑定 |
| `screen/AppCategoryClassifier.kt` | 扩展为能力白名单来源 |
| `ui/settings/AccessibilitySettingsScreen.kt` + `SettingsManager.kt` | 增加 `phoneOpScreenFormat`、`operationGuardEnabled`、新动作开关、状态图开关 |
| 新增文件 | `screen/UITree.kt`、`screen/UINodeIdentity.kt`、`screen/UIABus.kt`、`screen/ScreenGraph.kt`、`screen/VisionOverlay.kt`、`action/OperationGuard.kt` |

---

## 八、风险与权衡

1. **token 成本**：全树 JSON 可能很大。→ 默认可见节点完整 + 隐藏子树只给摘要；弱模型可切回 text 形态。
2. **性能**：rebind 每次快照做指纹匹配，高频滚动时需限流（复用现有 `scheduleRefresh` 节流）。
3. **无障碍树对自绘/WebView 内容不完整**：依赖视觉融合兜底，但视觉本身有延迟 → 只做降级路径，不做主路径。
4. **稳定性风险点**：身份指纹碰撞（同布局多卡片）→ rebind 保留坐标邻近约束与"多才歧义时交还 AI 用 text 重新认"。
5. **安全责任边界**：确认回路是"最终保障"，白名单是"第一道闸"，两者独立可关，避免过度限制日常自动化。

---

## 九、验收标准（一句话）

> 任意一条指令（如"在网易云给这首歌唱的歌手点赞"），AI 能**从打开 App 到完成目标**全程自主完成：每一步都知道当前页面结构、持有稳定元素身份、失败能回退重试、敏感动作先问用户，且全程可被用户在界面上随时叫停。

---

## 十、迭代建议（下一步行动）

1. 确认本方案的范围与优先级（Phase 1~4 是否都要，还是先只做"真树"+身份）。
2. 若确认，我会按 Phase 1 先在源码落地 `UITree` + `formatScreenForLLM(tree)` 并把 `analyzePhoneOpStep` 提示词改成 id/role 语义，然后本地编译验证。
3. 落地后更新 `PROJECT_INDEX.md` 中 `screen/` 与 `plugin/` 的条目。