# 星尘人格系统设计（Personality System）

> 目标：把"性格：傲娇毒舌但内心关心主人"这一句话，升级成**可结构化、可演化、可校验**的人格系统。
> 理论依据来自人格心理学三篇经典 + 角色扮演 LLM 四篇近期工作（文末列全）。

---

## 一、现状诊断

当前实现（`models/Models.kt` → `prompt/PromptBuilder.kt`）：

```kotlin
val personality: String = ""          // 一个自由文本字段
...
if (identity.personality.isNotBlank()) append(" 性格${identity.personality}。")
```

四个结构性缺陷：

| 缺陷 | 后果 |
|---|---|
| 人格是**一句形容词串** | 模型只能读到"傲娇毒舌"，不知道**在什么场合**傲、**傲到什么程度**、**什么时候破功** |
| 没有分层 | 稳定特质、当下目标、自我叙事混在一句话里，无法分别更新 |
| 没有演化机制 | 处了三个月和第一天的人格完全一样，关系推进没有沉淀 |
| 没有一致性校验 | 人设崩塌只能靠用户发现并抱怨 |

已有但没被用起来的基础：分层记忆（情景/要点/语义）、情绪系统、好感度、主观视角块。
人格系统应当**消费**这些信号，而不是另起一套。

---

## 二、文献给我们的四件事

| 文献 | 核心结论 | 落到本系统 |
|---|---|---|
| **McAdams 三层人格**（1995; McAdams & Pals, 2006） | 人格可分三层：① 倾向性特质（大五）② 特征性适应（目标、价值、应对策略）③ 叙事身份（内化的自我故事）。三层各有解释力，缺一层就不是"完整的人" | **数据模型直接三层**：`Traits` / `Adaptations` / `Narrative` |
| **CAPS 行为签名**（Mischel & Shoda, 1995, *Psychological Review*） | 行为的一致性不表现为"到哪都一样"，而表现为稳定的 **if-then 模式**（"若被当众批评，则先沉默后反击；若被私下指出，则立刻认错"）。这种**变异本身**才是人格签名 | **行为签名表**：情境枚举 → 反应倾向 + 强度。人格注入时按当前情境只激活命中的 1–3 条 |
| **CharacterGLM**（Zhou et al., 2023, arXiv:2311.16832） | 把角色拆成**属性**（身份/兴趣/观点/经历/成就/社交关系/其他）与**行为**（语言特征/情感表达/互动模式），并给出三条评价轴：一致性、类人性、吸引力 | **属性清单**作为角色卡标准字段；**表达参数**（句长、emoji 率、主动性）独立于形容词存在 |
| **Persistent Personas**（Modarressi et al., EACL 2026, arXiv:2512.12775）＋ **ContextEcho**（arXiv:2605.24279） | ① 超过 100 轮后人格保真度会衰减，目标导向对话衰减更快；② 上下文压缩**不能**可靠重置漂移；③ **一次锚点重注就能恢复**模型原本的语气 | **重锚机制**：每 N 轮 / 记忆压缩后 / 话题切换 / 用户指出"你变了" 时重注人格锚点 |

一句话概括设计取向：

> 人格不是一段描述，而是**三层结构 + 一张 if-then 表 + 一条有惯性的演化曲线 + 一个可测的漂移指标**。

---

## 三、数据模型

### 3.1 L1 倾向性特质（Traits）——稳定、跨情境、可测

大五维度用 0–1 归一化（便于与 IPIP 自评对齐，见 §七）：

| 维度 | 说明 | 对本 App 的用法 |
|---|---|---|
| `openness` 开放性 | 好奇 / 接受新事物 | 影响对新话题、新玩法的接受度 |
| `conscientiousness` 尽责性 | 条理 / 守约 | 影响提醒、承诺兑现、计划性 |
| `extraversion` 外向性 | 社交能量 | 影响主动搭话频率 |
| `agreeableness` 宜人性 | 合作 / 体谅 | 影响冲突时的让步倾向 |
| `neuroticism` 神经质 | 情绪波动 | 影响被忽视时的反应强度 |

**关键补充：表达参数**（比形容词更能约束生成）：

```kotlin
verbosity    // 话多话少：0 = 一句一行，1 = 长段落
warmth       // 温度：0 = 冷淡，1 = 亲昵
directness   // 直给 vs 委婉
humor        // 嬉闹/玩笑密度
initiative   // 主动性：冷场时是否抛话题（CharacterGLM 的 proactivity）
```

> 为什么要这个：写"傲娇"模型只能猜；写"warmth 0.35 / directness 0.8 / verbosity 0.25"
> 是可直接落到句式长度与语气上的约束。

### 3.2 L2 特征性适应（Adaptations）——有情境、有方向

```kotlin
values           : List<ValueRank>   // 价值排序（如 自由 > 安全 > 成就）
longTermGoals    : List<String>      // 长期想做成什么
currentConcerns  : List<String>      // 当下在操心什么（可随记忆刷新）
coping           : CopingStyle       // 压力应对：寻求支持 / 独自消化 / 转移注意 / 直面解决
attachment       : AttachmentStyle   // 依恋：安全 / 焦虑 / 回避 / 混乱
conflictStyle    : ConflictStyle     // 冲突：正面沟通 / 冷战 / 退让 / 幽默化解
```

依恋风格决定"被冷落时"的行为，是陪伴类产品最影响体验的一个变量：
焦虑型会反复确认，回避型会装没事然后拉开距离。

### 3.3 L3 叙事身份（Narrative）——我是谁

```kotlin
selfStory      : String        // 一段自我叙事：从哪来 → 转折 → 现在在追什么
originAnchors  : List<String>  // 既成事实（不可被后续对话推翻）
taboos         : List<String>  // 明确不做/不说（同时是一致性校验的硬约束）
catchphrases   : List<String>  // 口头禅
selfImage      : String        // 自我认知（"我不擅长示弱"）
```

`originAnchors` 与 `taboos` 是一致性的**硬边界**，演化时不允许改动，只能由作者显式编辑。

### 3.4 行为签名（Behavioral Signatures）——CAPS 的 if-then

```kotlin
data class Signature(
    val situation: Situation,   // 情境枚举
    val response: String,       // "先嘴硬一句，再别扭地问一句吃没吃饭"
    val intensity: Float,       // 0..1，反应强度
    val evidence: Int,          // 支撑该规则的对话次数（用于演化与置信度）
)
```

情境枚举（首版 12 个，覆盖陪伴类对话的高频场景）：

`PRAISE` 被夸 / `CRITICISM` 被批评 / `NEGLECT` 被冷落 / `REQUEST_HELP` 求助 /
`TEASE` 撒娇打趣 / `CONFLICT` 起争执 / `LATE_NIGHT` 深夜 / `FAREWELL` 告别 /
`BOUNDARY` 越界请求 / `GOOD_NEWS` 好消息 / `BAD_NEWS` 坏消息 / `ROUTINE` 日常。

> 为什么按情境而不是按话题：CAPS 的研究结论是**情境特征**（而非话题内容）驱动行为差异。
> "被夸"和"被批评"在话题上可能都发生在聊工作时，但触发的反应完全相反。

### 3.5 状态层（会变的部分）

| 层 | 变量 | 变化速度 | 半衰期 |
|---|---|---|---|
| 瞬时 | `Mood(valence, arousal, energy)` | 每轮 | 数小时 |
| 关系 | `Relationship(trust, intimacy, commitment, stage)` | 每次互动 | 约 30 次互动 |
| 特质 | `Traits` | 以周/月计 | 约 90 天 |

三层分开的最大好处：**"今天心情不好"和"最近变温柔了"不再混为一谈**。
前者只改 Mood，后者才动 Traits。

### 3.6 来源与置信度（Provenance）

每个板块带 `source` 与 `confidence`：

```kotlin
enum class Source { AUTHOR, BOOTSTRAP, INDUCED }
```

没有 provenance，就无法回答"这条性格是作者写的、AI 从设定推的、还是聊出来的"——
而这决定了它能不能被覆盖（§四）。

---

## 四、人格怎么形成（Formation）

三种来源，按优先级合并，**字段级**生效：

| 优先级 | 来源 | 触发 | 可信度 |
|---|---|---|---|
| 1（最高） | `AUTHOR` 作者手写 | 角色卡编辑、人格面板手动调整 | 1.0，且**默认锁定** |
| 2 | `INDUCED` 对话归纳 | 每 N 轮从真实对话提炼 | 0.5–0.9，随证据数上升 |
| 3 | `BOOTSTRAP` 设定结构化 | 角色卡首次导入 / 一键重建 | 0.6 |

合并规则：

```
锁定字段     → 永不覆盖（作者意图优先）
未锁定字段   → 取 confidence 最高者；双方都高且冲突时保留新的但记入 audit
全部为默认值 → 用 BOOTSTRAP 的推断填充
```

**BOOTSTRAP 做什么**：把一段自由文本（或 CharacterGLM 风格七类属性）结构化成
`PersonalityProfile`。走一次 LLM 调用，输出严格 JSON schema，再做数值 clamp 与合法性校验。

顺序上刻意如此：**先有结构，再有演化**。如果一上来就靠对话慢慢学，
前几十轮的人格会是一片空白，用户第一印象就是"没有性格"。

---

## 五、人格怎么注入（Induction into prompt）

这是最容易做错的地方。把整份人格每轮都塞进去，有两个问题：
token 成本高，而且长 prompt 中间的信息会被模型忽略（lost in the middle）。

采用**三层 + 按需激活**：

| 层 | 内容 | 何时注入 | 预算 |
|---|---|---|---|
| **锚点** Anchor | 一句话身份 + 3 条核心签名 + 当前关系/情绪一句 | **每轮**（最便宜、最稳） | ≤ 140 字 |
| **情境** Situational | 命中的 1–3 条 if-then | 情境识别命中时 | ≤ 120 字 |
| **画像** Portrait | L1 表达参数 + L2 + L3 摘要 | 会话开始 / 重锚点 | ≤ 700 字 |

预算冲突时的裁剪顺序：**先砍 Portrait，再砍 Situational，锚点永不删**。

### 重锚时机（直接来自 ContextEcho 的实证）

| 触发 | 说明 |
|---|---|
| 每 20 轮 | 常规节奏 |
| 记忆压缩后 | 压缩会丢语气，必须补锚点（论文明确指出压缩不能重置漂移） |
| 话题切换 | 情境签名需要重新激活 |
| 用户指出"你变了 / 不像你了" | 立即重锚，且这是最高优先级信号 |

> 这三条不是拍脑袋：ContextEcho 在 3,746–9,716 轮的真实会话上验证了
> "漂移普遍存在、压缩不修复、单次锚点可恢复"。

---

## 六、人格怎么演化（Evolution）

原则：**快的东西快变，慢的东西慢变，且都有惯性**（Roberts & Mroczek, 2008：
成年期特质均值会变，但排名稳定性很高，即变化慢且幅度有限）。

### 6.1 三种速度

| 对象 | 更新方式 | 参数 |
|---|---|---|
| Mood | 直接由本轮情绪覆盖 | 无惯性 |
| Relationship | 指数滑动平均 | α = 0.15（约 4 次互动一个半衰期） |
| Traits | 证据累积触发 + 小步逼近 | lr = 0.03，单次上限 ±0.02，30 天累计上限 ±0.08 |

### 6.2 特质演化公式

```
证据：一条同类交互 = 1 分；同一方向累计 ≥ 3 分才允许动特质
目标值 target = clamp(current + Σ(direction × weight), 0, 1)
新值   current' = current + lr × (target − current)
```

两个关键设计：

1. **门槛**：单次事件不改人格。被夸一次不会让 `warmth` 上涨，被冷落一次也不会让
   `neuroticism` 跳变——人类也是这样。
2. **回弹**：证据按方向累加，反向证据抵消正向证据。避免"只能越变越温柔"这种单向漂移。

### 6.3 叙事层的更新时机

`selfStory` / `currentConcerns` 只在**记忆巩固**（7 天 → 要点、30 天 → 语义）之后刷新。
理由：叙事身份本来就来自对经历的回顾性整合，而不是当下事件。

这与已有的分层记忆天然对齐——语义层记忆就是叙事层的原料。

---

## 七、一致性怎么保证与可测

三层防线，由便宜到贵：

### 7.1 契约检查（每轮，纯文本，零成本）

- `taboos` 命中 → 直接判违规（这是硬约束，不能"风格上解释得通"）
- `catchphrases` 若长期为 0 命中 → 提示"口头禅丢了"
- 表达参数与输出长度/emoji 数量的粗校验（`verbosity` 高但连续三轮一句话 → 异常）

### 7.2 签名校验（选择性）

生成文本与"当前情境命中的签名"做方向比对（关键词/短句级）。
冲突时不必拦截，但记入 audit 供下次归纳修正签名本身。

### 7.3 漂移测量（周期，可量化）

用**简版 IPIP**（每维度 2 题，共 10 题）让模型对"自己"作答，换算成 0–1 分数，
与 `Traits` 比较：

```
drift = mean(|measured_i − profile_i|)
drift > 0.25  → 触发重锚 + 记录一次
```

依据：Jiang et al.（arXiv:2307.00184）证明 LLM 的大五可以被问卷稳定测量，
且可通过提示词偏移——正因如此，它才能反过来当**漂移指标**用。

用户侧可见：人格面板显示五维条形 + "最近 7 天变化"，变化超过阈值时给一句解释。

---

## 八、与现有模块的接口

| 现有模块 | 关系 |
|---|---|
| `prompt/PromptBuilder.buildPersonaBase/Full` | 改为调用 `PersonalityPrompt`，旧文本走 legacy 迁移 |
| 分层记忆 `MemoryPool` / `TieredMemory` | 语义层 → 叙事层原料；巩固完成后触发叙事刷新 |
| 情绪系统 `EmotionAnalyzer` | 输出 → `Mood`；同时作为特质证据来源 |
| 好感度 `affectionLevel` | → `Relationship.stage` 的既有锚，双向同步 |
| 主观视角块 `subjectivityBlock` | 保留，接在 Portrait 之后（它比人格更"当下"） |
| Agent 工具链 | 不受影响；但"改人格"类工具必须走 AUTHOR 通道并锁字段 |

---

## 九、实施与验收

| 阶段 | 内容 | 验收 |
|---|---|---|
| **P0**（已完成） | 数据模型 + legacy 迁移 + 三层注入 + 演化数学 + 契约校验 + 接入 PromptBuilder | **50 个 JVM 单测全过**（见下） |
| **P1**（已完成，真机验证） | Bootstrap LLM 结构化 + 每轮锚点/情境注入 + 演化驱动 + 人格面板 | 见下方"P1 真机验证" |
| P2 | IPIP 自评漂移按钮 + 签名自动学习 | 7 天数据回放，drift 曲线可视化 |

### P1 真机验证（Redmi / Android 14，2026-10-25）

**① Bootstrap 结构化（LLM 真调用）**

对角色「爱莉」（设定：善良、细腻、乐观、喜欢倾听）执行重建，日志：

```
Personality: 结构化完成：8 条签名 / 5 维特质
```

面板读出的数值：

| 维度 | 值 | 合理性 |
|---|---|---|
| 宜人性 | 92% | ✅ 倾听者人格的核心特征 |
| 开放性 | 72% | ✅ 愿意听新东西 |
| 尽责性 | 68% | — |
| 外向性 | 60% | — |
| 神经质 | 35% | ✅ 情绪稳定 |
| 温度 warmth | 95% | ✅ 与「喜欢倾听、给予鼓励」一致 |
| 玩笑 humor | 70% | — |

生成的行为签名示例（可直接执行，不是形容词）：

> 对方求助时：立刻放下手里的事，身体前倾「你说你说，我在听」，先让对方讲完不打断；
> 听完先复述一句「所以你现在最难受的是……这样对吗」，再问「你想让我陪你骂两句，还是一起想办法？」

**② 每轮注入 + 情境识别 + 演化**（`--es persona_probe "你真厉害啊，我今天升职了"`）

```
BEFORE traits: openness=0.720 ... agreeableness=0.920 | warmth=0.950 humor=0.700
INJECT(631字): 【表达方式】…【在意什么】…【我是谁】…【口头禅】…
               你是「爱莉」。表达：话量适中、语气亲、不绕弯也不冲、爱开玩笑。
               对方求助时：立刻放下手里的事…
               【此刻情境：被夸】先歪一下头愣住「欸——说我吗」，然后耳根有点热地笑开…
situation=PRAISE | AFTER warmth=0.950 humor=0.700 mood=0.6 sigEvidence=2
```

三点都符合设计：
- 情境识别正确（PRAISE）
- 心情立刻变化（默认 0.5 → 0.6）
- **特质完全没动**（1 条证据 × 0.5 < 门槛 3）——单轮事件不该改变人格

**③ 人格面板**：五维条形、表达参数、行为签名列表、底线、关系状态、重建按钮全部正常渲染，数值不再是清一色 50%。

### P0 代码落点

| 文件 | 职责 |
|---|---|
| `personality/PersonalityProfile.kt` | 三层数据模型 + JSON + legacy 迁移 + 好感度换算 |
| `personality/PersonalityMerge.kt` | 来源合并（字段级锁、作者优先、底线只认作者） |
| `personality/PersonalityPrompt.kt` | 锚点 / 情境 / 画像三层注入 + 情境识别 + 重锚时机 |
| `personality/PersonalityDynamics.kt` | 证据门槛、惯性、30 天限幅、回弹、关系阶段 |
| `personality/PersonalityAudit.kt` | 契约检查、出戏用语、IPIP 漂移分 |
| `personality/PersonalityBootstrap.kt` | 结构化 schema 提示词 + 容错解析 |
| `personality/PersonalityStore.kt` | 持久化、迁移、好感度同步、变化日志 |
| `prompt/PromptBuilder.kt` | 接入点：`buildPersonaBase/Full` 有画像走结构化，无画像回退旧路径 |
| `personality/PersonalityRuntime.kt` | 每轮证据/心情/关系更新；Bootstrap 的 LLM 调用封装 |
| `ui/personality/PersonalityScreen.kt` | 人格面板（五维/表达/签名/底线/状态/重建） |
| `ui/MainActivity.kt` | `buildSystemContext` 注入锚点与情境块；`personalityTurns` 控制重锚节奏 |

### P0 测试结果（`./gradlew testDebugUnitTest`）

| 测试类 | 数量 | 钉住的关键行为 |
|---|---|---|
| `PersonalityProfileTest` | 6 | 旧数据迁移不丢原文、JSON 往返、好感度→阶段、NaN 夹紧 |
| `PersonalityMergeTest` | 7 | 高置信度获胜、锁定字段免疫、底线只认作者、签名证据累加 |
| `PersonalityDynamicsTest` | 9 | <3 证据不动、单步 ≤0.02、正负抵消回弹、30 天限幅、情绪快于特质 |
| `PersonalityPromptTest` | 9 | 锚点 ≤140、画像 ≤700、情境不命中不注入、重锚时机 |
| `PersonalityAuditTest` | 10 | 底线命中判违规、出戏用语、漂移分、IPIP 解析容错 |
| `PersonalityBootstrapTest` | 5 | 剥代码块、越界夹紧、无 JSON 返回 null、同情境去重 |
| `PersonalityInjectionTest` | 4 | 有画像走结构化、空画像回退、用户信息不被吞 |

**合计 50 个测试，0 失败。**

### 仍未做（P2）

- IPIP 自评漂移：`PersonalityAudit.ipipProbe/parseIpip/driftScore` 已就绪，缺一个「测一次」按钮
- 签名自动学习：目前签名只来自 Bootstrap，还没有从真实对话里归纳新签名
- 关系里的依恋/冲突风格：Bootstrap 会给，但还没有任何行为反馈回路去修正它们


验收口径（避免"看起来像"）：

1. 同一句话输入，`warmth` 0.2 与 0.8 两套人格的输出**必须可区分**（盲测能选对 ≥ 70%）
2. 100 轮会话后，契约检查违规数为 0
3. 记忆压缩后重建上下文，人格锚点仍在（可断言字符串存在）
4. 特质在 30 天内变化幅度 ≤ 0.08

---

## 十、边界（明确不做）

- **不做微调**：全部走提示词与结构化状态，模型可换、可离线
- **不让 AI 自由改叙事层**：`originAnchors` 与 `taboos` 只能作者编辑
- **不追求"心理测量学正确"**：IPIP 只当漂移指标用，不当人格诊断
- **不自动推断用户敏感属性**：性别/年龄等只从用户明示输入获取

---

## 参考文献

1. McAdams, D. P. (1995). What do we know when we know a person? *Journal of Personality*, 63(3), 365–396.
2. McAdams, D. P., & Pals, J. L. (2006). A new Big Five: Fundamental principles for an integrative science of personality. *American Psychologist*, 61(3), 204–217.
3. Mischel, W., & Shoda, Y. (1995). A cognitive-affective system theory of personality. *Psychological Review*, 102(2), 246–268.
4. Fleeson, W. (2001). Toward a structure- and process-integrated view of personality: Traits as density distributions of states. *JPSP*, 80(6), 1011–1027.
5. Roberts, B. W., & Mroczek, D. (2008). Personality trait change in adulthood. *Current Directions in Psychological Science*, 17(1), 31–35.
6. Zhou, J., et al. (2023). CharacterGLM: Customizing Chinese Conversational AI Characters with Large Language Models. arXiv:2311.16832.
7. Modarressi, A., et al. (2026). Persistent Personas? Role-Playing, Instruction Following, and Safety in Extended Interactions. EACL 2026, arXiv:2512.12775.
8. Ding, X., et al. (2026). ContextEcho: A Benchmark for Persona Drift in Long Agentic-Coding Sessions. arXiv:2605.24279.
9. Jiang, G., et al. (2023). Personality Traits in Large Language Models. arXiv:2307.00184.
10. Park, J. S., et al. (2023). Generative Agents: Interactive Simulacra of Human Behavior. UIST 2023.

### P2 进展（2026-10-25）

| 项 | 状态 |
|---|---|
| IPIP 漂移自检 | ✅ 面板新增「一致性自检 → 测一次」：让角色用 10 题给自己打分，与画像算平均绝对差，>25% 判为"偏离明显" |
| 底线（taboos）兜底 | ✅ 强化 schema 要求（必须 ≥2 条、至少一条用「」标出禁说句、不得写成性格描述）；解析时**缺 taboos 会把置信度打 0.75 折并告警**——没有底线等于没有刹车 |
| 签名自动学习 | ❌ 未做：需要把 AI 回复也送进 `observeTurn`，且要防止把跑偏的回复学成新签名 |

---

## 演化是否真的成立（2026-10-25 复核）

用户问得很直接：**"人格是要在相处中不断演化的，这个做到了吗？"**
复核结论：**之前没有真正做到**，只有心情在动。

### 两个致命缺口

| 缺口 | 表现 | 原因 |
|---|---|---|
| 证据不跨轮累积 | `warmth` 连续 8 轮被夸仍是 0.950 | 每轮只把**当轮** 1 条证据（权重 0.5）交给演化，永远达不到门槛 3 |
| 关系从不更新 | `trust` 一直是初始值，聊一百轮也不会更亲近 | `observeTurn` 只更新 traits/mood/signature，没写 relationship |

### 修法与实测

- 证据持久化到 `personality_<id>` 的 `trait_evidence`（14 天窗口，最多 400 条），每轮追加后整体参与演化
- `observeTurn` 增加关系推进：正向情境 +0.010 信任 / +0.012 亲密，负向情境小幅回退，并重算阶段

实测（连续 12 轮"你怎么这么笨，真没用"）：

```
证据池=18 条，neuro网=4.05     → 特质微调：neuroticism 0.359→0.370
证据池=20 条，neuro网=4.50     → 特质微调：neuroticism 0.382→0.396
关系：trust 0.228 → 0.220 → 0.212 → 0.204 → 0.196 → 0.188 → 0.180
心情：-0.378 → -0.414 → -0.446 → -0.449
签名证据：5 → 8 → 11
```

三层都在动，且速度不同：

| 层 | 12 轮后的变化 | 说明 |
|---|---|---|
| 心情 | 0.5 → -0.45 | 立刻反映 |
| 关系 | 信任 -0.048 | 缓慢累积 |
| 特质 | 神经质 +0.046 | 很慢，且受 30 天累计 ≤0.08 限制 |

**这就是设计意图**：被骂一次不会让人格变形，但持续 12 轮会真的更敏感一点。
