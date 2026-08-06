# 日记超链接关系图谱架构设计文档

## 一、系统概览

### 1.1 设计目标

基于Obsidian关系图谱的核心优势,为Stradust日记功能设计一套**零维护、自动化、可视化**的关系图谱系统,实现:

- ✅ **双向链接自动生成** - 用户无需手动维护链接关系
- ✅ **本地图谱视图** - 避免信息过载,聚焦当前日记的关联网络
- ✅ **多维过滤系统** - 支持主题/时间/标签等多维度筛选
- ✅ **超链接点击跳转** - 直观的用户交互体验
- ✅ **零维护成本** - 链接关系自动构建和更新

### 1.2 核心架构

```
日记数据层(DiaryManager)
    ↓
链接提取层(LinkExtractor)
    ├─ 自动检测[[关键词]]语法
    ├─ 提取人名、地点、主题等实体
    └─ 构建双向链接索引
    ↓
关系图谱层(DiaryGraphManager)
    ├─ 维护节点和边数据
    ├─ 计算图谱布局
    └─ 提供多维过滤接口
    ↓
可视化层(DiaryGraphView)
    ├─ Canvas/WebGL渲染
    ├─ 物理引擎驱动布局
    └─ 交互响应(点击/悬停/拖拽)
    ↓
用户交互层(MainActivity)
    ├─ 本地图谱视图入口
    ├─ 超链接点击跳转
    └─ 过滤和搜索功能
```

---

## 二、核心功能设计

### 2.1 双向链接自动生成

#### **链接语法设计**

参考Obsidian的Wikilink语法,设计日记专用链接格式:

```markdown
# 基本链接
[[日记:2024-01-15]]         # 链接到特定日期日记
[[主题:健康]]               # 链接到主题笔记
[[人物:张三]]               # 链接到人物笔记
[[地点:北京]]               # 链接到地点笔记

# 带别名链接
[[日记:2024-01-15|昨天的日记]]  # 显示别名而非日期

# 嵌入式链接
![[日记:2024-01-15]]        # 直接嵌入日记内容(可选)
```

#### **自动链接提取算法**

**核心流程**:
```kotlin
fun extractLinks(diaryContent: String): List<LinkInfo> {
    val links = mutableListOf<LinkInfo>()
    
    // 1. 显式链接提取:[[xxx]]语法
    val explicitPattern = Regex("\\[\\[([^\\]]+)\\]\\]")
    explicitPattern.findAll(diaryContent).forEach { match ->
        val linkText = match.groupValues[1]
        links.add(LinkInfo(
            type = parseLinkType(linkText),  // diary/topic/person/location
            target = parseLinkTarget(linkText),
            position = match.range.first,
            isExplicit = true
        ))
    }
    
    // 2. 隐式链接提取:基于知识图谱自动识别
    val entities = extractEntities(diaryContent)  // 使用NLP或规则
    entities.forEach { entity ->
        links.add(LinkInfo(
            type = entity.type,
            target = entity.name,
            position = entity.position,
            isExplicit = false  // 提示用户可能遗漏的链接
        ))
    }
    
    return links
}
```

#### **双向链接索引构建**

**数据结构**:
```json
{
  "forwardLinks": {
    "日记:2024-01-15": [
      {"target": "主题:健康", "position": 120},
      {"target": "人物:张三", "position": 350}
    ]
  },
  "backLinks": {
    "主题:健康": [
      {"source": "日记:2024-01-15", "position": 120},
      {"source": "日记:2024-03-20", "position": 80}
    ]
  }
}
```

**自动更新机制**:
- 日记创建时:提取链接,更新双向索引
- 日记修改时:重新提取链接,增量更新索引
- 日记删除时:移除相关链接,清理索引

---

### 2.2 本地图谱视图

#### **设计理念**

- **以当前日记为中心** - 显示直接关联的笔记(深度可调)
- **避免信息过载** - 限制显示节点数量,聚焦核心关联
- **实时交互响应** - 悬停高亮、点击跳转、拖拽调整布局

#### **布局算法**

**力导向布局**(参考Obsidian):

```kotlin
class DiaryGraphLayout {
    // 中心节点(当前日记)
    val centerNode: GraphNode
    
    // 参数配置
    val centerForce: Float = 10.0f     // 趋中心引力
    val repelForce: Float = 60.0f      // 节点排斥力
    val linkForce: Float = 5.0f        // 连接吸引力
    val linkDistance: Float = 100.0f   // 连接线长度
    
    fun calculateLayout(nodes: List<GraphNode>, edges: List<GraphEdge>) {
        // Verlet积分方法(比Euler更稳定)
        for (node in nodes) {
            // 计算合力
            val force = Vector2D()
            
            // 中心引力(当前日记节点除外)
            if (node != centerNode) {
                force += centerForce * (centerNode.pos - node.pos).normalize()
            }
            
            // 节点排斥力
            for (other in nodes) {
                if (other != node) {
                    val dist = (node.pos - other.pos).length()
                    force -= repelForce / (dist * dist) * (node.pos - other.pos).normalize()
                }
            }
            
            // 连接吸引力
            for (edge in edges) {
                if (edge.source == node || edge.target == node) {
                    val neighbor = if (edge.source == node) edge.target else edge.source
                    val dist = (node.pos - neighbor.pos).length()
                    force += linkForce * (dist - linkDistance) * (neighbor.pos - node.pos).normalize()
                }
            }
            
            // 更新位置(使用Verlet积分)
            node.velocity += force * deltaTime
            node.pos += node.velocity * deltaTime
            node.velocity *= damping  // 阻尼系数0.85
        }
    }
}
```

#### **深度控制**

```kotlin
// 深度1:只显示直接连接的笔记(默认)
fun getLocalGraph(diaryId: String, depth: Int = 1): GraphData {
    val nodes = mutableListOf<GraphNode>()
    val edges = mutableListOf<GraphEdge>()
    
    // 添加中心节点
    nodes.add(GraphNode(diaryId, isCenter = true))
    
    // 添加第1层连接
    val directLinks = getDirectLinks(diaryId)
    for (link in directLinks) {
        nodes.add(GraphNode(link.target))
        edges.add(GraphEdge(diaryId, link.target))
        
        // 如果depth>=2,继续添加第2层
        if (depth >= 2) {
            val secondLayerLinks = getDirectLinks(link.target)
            for (link2 in secondLayerLinks) {
                if (!nodes.any { it.id == link2.target }) {
                    nodes.add(GraphNode(link2.target))
                }
                edges.add(GraphEdge(link.target, link2.target))
            }
        }
    }
    
    return GraphData(nodes, edges)
}
```

---

### 2.3 多维过滤系统

#### **过滤维度**

| 维度 | 实现方式 | 应用场景 |
|------|---------|---------|
| **时间范围** | 按日期过滤节点 | 查看特定时间段的关系 |
| **主题标签** | 按tag过滤节点 | 聚焦特定主题网络 |
| **节点类型** | 按类型过滤(diary/topic/person) | 只看人物关系图谱 |
| **连接强度** | 按连接数量过滤边 | 只显示强关联 |
| **搜索关键词** | 按内容匹配过滤 | 动态高亮相关节点 |

#### **过滤实现**

```kotlin
class DiaryGraphFilter {
    fun filterByTimeRange(graph: GraphData, startDate: Date, endDate: Date): GraphData {
        val filteredNodes = graph.nodes.filter { node ->
            val nodeDate = getNodeDate(node.id)
            nodeDate >= startDate && nodeDate <= endDate
        }
        
        val filteredEdges = graph.edges.filter { edge ->
            filteredNodes.any { it.id == edge.source } &&
            filteredNodes.any { it.id == edge.target }
        }
        
        return GraphData(filteredNodes, filteredEdges)
    }
    
    fun filterByNodeType(graph: GraphData, types: List<NodeType>): GraphData {
        val filteredNodes = graph.nodes.filter { node ->
            types.contains(getNodeType(node.id))
        }
        // 同上...
    }
    
    fun filterBySearch(graph: GraphData, query: String): GraphData {
        // 高亮匹配节点,其他节点淡化显示
        val matchedNodes = graph.nodes.map { node ->
            val content = getNodeContent(node.id)
            node.isHighlighted = content.contains(query, ignoreCase = true)
            node
        }
        return GraphData(matchedNodes, graph.edges)
    }
}
```

---

### 2.4 超链接点击跳转

#### **交互设计**

| 操作 | 行为 | 实现方式 |
|------|------|---------|
| **悬停节点** | 高亮节点及其连接 | 节点放大、连线加粗、显示tooltip |
| **单击节点** | 打开目标日记/笔记 | Intent跳转到日记详情页 |
| **双击节点** | 以该节点为中心重构图谱 | 重新计算本地图谱布局 |
| **右键节点** | 显示操作菜单 | 复制链接、编辑、删除等快捷操作 |
| **悬停连线** | 显示连接详情 | 显示连接次数、首次连接时间等 |
| **单击连线** | 打开两个日记对比视图 | 左右对比显示两个日记内容 |

#### **点击跳转实现**

```kotlin
class DiaryGraphInteraction {
    fun onNodeClick(node: GraphNode, context: Context) {
        val nodeType = getNodeType(node.id)
        
        when (nodeType) {
            NodeType.DIARY -> {
                // 跳转到日记详情页
                val intent = Intent(context, DiaryDetailActivity::class.java)
                intent.putExtra("diary_date", parseDiaryDate(node.id))
                context.startActivity(intent)
            }
            NodeType.TOPIC -> {
                // 跳转到主题笔记页(需新建)
                val intent = Intent(context, TopicNoteActivity::class.java)
                intent.putExtra("topic_name", parseTopicName(node.id))
                context.startActivity(intent)
            }
            NodeType.PERSON -> {
                // 跳转到人物卡片页(需新建)
                val intent = Intent(context, PersonCardActivity::class.java)
                intent.putExtra("person_name", parsePersonName(node.id))
                context.startActivity(intent)
            }
        }
    }
    
    fun onNodeDoubleClick(node: GraphNode) {
        // 以该节点为中心重构图谱
        val newCenterGraph = getLocalGraph(node.id, depth = 2)
        updateGraphView(newCenterGraph)
    }
    
    fun onEdgeClick(edge: GraphEdge, context: Context) {
        // 打开两个日记对比视图
        val intent = Intent(context, DiaryComparisonActivity::class.java)
        intent.putExtra("diary1", edge.source)
        intent.putExtra("diary2", edge.target)
        context.startActivity(intent)
    }
}
```

---

## 三、数据模型设计

### 3.1 核心数据结构

#### **链接信息**
```kotlin
data class LinkInfo(
    val type: LinkType,           // diary/topic/person/location
    val target: String,           // 目标ID
    val position: Int,            // 在原文中的位置
    val isExplicit: Boolean,      // 是否显式链接
    val alias: String? = null     // 显示别名
)

enum class LinkType {
    DIARY, TOPIC, PERSON, LOCATION, EVENT
}
```

#### **图谱节点**
```kotlin
data class GraphNode(
    val id: String,               // 节点ID(如"日记:2024-01-15")
    val label: String,            // 显示标签(如"2024-01-15"或"健康")
    val type: NodeType,           // 节点类型
    val pos: Vector2D,            // 位置坐标
    val velocity: Vector2D,       // 运动速度(用于物理引擎)
    val size: Float = 20.0f,      // 节点大小(可根据连接数量动态调整)
    val color: Int,               // 颜色(可根据类型染色)
    val isCenter: Boolean = false,// 是否中心节点
    val isHighlighted: Boolean = false // 是否高亮
)

enum class NodeType {
    DIARY, TOPIC, PERSON, LOCATION
}
```

#### **图谱连线**
```kotlin
data class GraphEdge(
    val source: String,           // 源节点ID
    val target: String,           // 目标节点ID
    val weight: Float = 1.0f,     // 连接权重(可表示连接次数)
    val color: Int = Color.GRAY,  // 连线颜色
    val thickness: Float = 2.0f   // 连线粗细(可根据权重动态调整)
)
```

#### **图谱数据**
```kotlin
data class GraphData(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val metadata: GraphMetadata? = null  // 可选的元数据(如时间范围)
)
```

---

### 3.2 持久化存储

#### **链接索引存储**

**方案A:JSON文件存储**(推荐)
```json
{
  "version": 1,
  "links": {
    "日记:2024-01-15": {
      "forward": [
        {"target": "主题:健康", "count": 3, "firstTime": "2024-01-15"},
        {"target": "人物:张三", "count": 1, "firstTime": "2024-01-15"}
      ],
      "backward": [
        {"source": "日记:2024-03-20", "target": "主题:健康", "count": 1}
      ]
    }
  }
}
```

**存储位置**: `context.filesDir/diary_graph/links_index.json`

**方案B:SQLite数据库存储**(大规模日记库优化)
```sql
CREATE TABLE diary_links (
    source_id TEXT,
    target_id TEXT,
    link_type TEXT,
    link_count INTEGER,
    first_time INTEGER,
    last_time INTEGER,
    PRIMARY KEY (source_id, target_id)
);
```

---

## 四、UI设计

### 4.1 本地图谱视图布局

```
┌─────────────────────────────────────────────────┐
│  [返回] [标题:日记关系图谱]    [过滤] [设置]     │
├─────────────────────────────────────────────────┤
│                                                 │
│         ┌─────┐                                 │
│         │主题 │──────┐                          │
│         │健康 │      │                          │
│         └─────┘      │                          │
│              │       │                          │
│              │   ┌───┴────┐                     │
│              │   │当前日记│                     │
│              └──→│2024-01 │←──┐                 │
│                  │  -15   │   │                 │
│              ┌──→└────────┘   │                 │
│              │               │                 │
│         ┌─────┴─┐         ┌─┴─────┐            │
│         │人物   │         │地点   │            │
│         │张三   │         │北京   │            │
│         └───────┘         └───────┘            │
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │ 深度: [1层] [2层] [3层]  类型: [全部] ▼  │  │
│  │ 搜索: [________________] [高亮匹配]     │  │
│  └──────────────────────────────────────────┘  │
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │ Tooltip: 张三 (人物)                     │  │
│  │ 出现日记: 5篇  首次: 2024-01-10          │  │
│  │ [打开日记] [查看所有提及]               │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 4.2 日记详情页中的超链接显示

```
┌─────────────────────────────────────────────────┐
│  日记详情 - 2024-01-15                          │
├─────────────────────────────────────────────────┤
│                                                 │
│  今天思考了很多关于[[主题:健康]]的事情,         │
│  和[[人物:张三]]讨论了项目的进展,                │
│  我们决定下周去[[地点:北京]]开会。              │
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │ 本地图谱预览(小窗口)                     │  │
│  │   [中心节点:当前日记]                    │  │
│  │   [一键打开完整图谱]                     │  │
│  └──────────────────────────────────────────┘  │
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │ 反向链接: 3篇日记提及本文                 │  │
│  │ - [[日记:2024-03-20]] 再次思考健康       │  │
│  │ - [[日记:2024-02-01]] 张三反馈           │  │
│  │ - [[日记:2024-01-20]] 北京会议记录       │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 4.3 超链接样式

**样式设计**:
- **颜色**: 根据类型区分(日记蓝色、主题绿色、人物橙色、地点紫色)
- **下划线**: 细虚线,悬停时变为实线
- **悬停效果**: 显示tooltip(目标名称、类型、出现次数)
- **点击效果**: 高亮瞬间,然后跳转到目标页面

**CSS样式示例**(Android等效实现):
```xml
<style name="DiaryLink">
    <item name="android:textColor">@color/link_diary</item>
    <item name="android:textStyle">normal</item>
    <item name="android:background">@drawable/link_underline</item>
</style>
```

---

## 五、技术实现路线图

### 5.1 Phase 1:基础功能(1-2周)

**任务清单**:
1. 创建`DiaryGraphManager.kt` - 核心管理类
2. 创建`LinkExtractor.kt` - 链接提取工具
3. 创建`DiaryGraphLayout.kt` - 图谱布局算法
4. 修改`DiaryManager.kt` - 集成链接索引构建
5. 修改`DiaryDetailActivity.kt` - 显示超链接和反向链接
6. 创建`DiaryGraphView.kt` - 自定义Canvas渲染视图

### 5.2 Phase 2:可视化增强(1-2周)

**任务清单**:
1. 创建`DiaryGraphActivity.kt` - 本地图谱专属页面
2. 实现物理引擎驱动的力导向布局
3. 实现节点交互(悬停、点击、拖拽)
4. 实现深度控制和过滤功能
5. 实现超链接点击跳转Intent
6. 添加颜色分组和搜索高亮

### 5.3 Phase 3:高级功能(1-2周)

**任务清单**:
1. 创建`TopicNoteActivity.kt` - 主题笔记页面
2. 创建`PersonCardActivity.kt` - 人物卡片页面
3. 实现日记对比视图`DiaryComparisonActivity.kt`
4. 实现隐式链接提取(NLP或规则)
5. 实现时序动画(可选)
6. 优化大规模图谱性能(Canvas裁剪)

---

## 六、性能优化策略

### 6.1 索引构建优化

**增量更新而非全量重建**:
```kotlin
fun updateLinksIndex(diaryId: String, oldContent: String?, newContent: String) {
    val oldLinks = oldContent?.let { extractLinks(it) } ?: emptyList()
    val newLinks = extractLinks(newContent)
    
    // 计算差异
    val removedLinks = oldLinks.filter { !newLinks.contains(it) }
    val addedLinks = newLinks.filter { !oldLinks.contains(it) }
    
    // 增量更新索引
    for (link in removedLinks) {
        removeLinkFromIndex(diaryId, link)
    }
    for (link in addedLinks) {
        addLinkToIndex(diaryId, link)
    }
}
```

### 6.2 图谱渲染优化

**Canvas视口裁剪**:
```kotlin
fun drawGraph(canvas: Canvas, viewport: RectF) {
    // 只渲染可见区域内的节点
    val visibleNodes = nodes.filter { node ->
        viewport.contains(node.pos.x, node.pos.y)
    }
    
    // 只渲染两端都在可见区域的连线
    val visibleEdges = edges.filter { edge ->
        val sourcePos = nodes.find { it.id == edge.source }?.pos
        val targetPos = nodes.find { it.id == edge.target }?.pos
        sourcePos != null && targetPos != null &&
        (viewport.contains(sourcePos.x, sourcePos.y) ||
         viewport.contains(targetPos.x, targetPos.y))
    }
    
    // 批量绘制连线
    for (edge in visibleEdges) {
        drawEdge(canvas, edge)
    }
    
    // 批量绘制节点
    for (node in visibleNodes) {
        drawNode(canvas, node)
    }
}
```

### 6.3 内存优化

**限制本地图谱深度**:
- 深度1:最多20个节点
- 深度2:最多50个节点
- 深度3:最多100个节点

**缓存策略**:
- 本地图谱数据缓存10分钟
- 链接索引常驻内存(定期持久化)
- 图谱布局参数缓存(避免重复计算)

---

## 七、与Obsidian对比

| 功能维度 | Obsidian | Stradust日记图谱 | 适配说明 |
|---------|---------|-----------------|---------|
| **双向链接** | ✅ 核心功能 | ✅ 核心功能 | 完全采纳,自动生成 |
| **本地图谱** | ✅ 核心功能 | ✅ 核心功能 | 完全采纳,避免信息过载 |
| **多维过滤** | ✅ 强大 | ✅ 中等 | 采纳核心维度(时间/类型/搜索) |
| **时序动画** | ✅ 支持 | ⚠️ 可选 | 下一版本实现 |
| **物理引擎** | ✅ 自研优化 | ✅ Verlet积分 | 采纳稳定算法 |
| **Canvas渲染** | ✅ WebGL | ✅ Android Canvas | 适配移动端性能 |
| **隐式链接** | ✅ Unlinked Mentions | ⚠️ 可选 | 使用NLP或规则提取 |

---

## 八、总结

**核心创新点**:
1. **零维护自动化** - 链接关系自动构建和更新,用户无需手动维护
2. **移动端优化** - 本地图谱避免信息过载,适合手机屏幕交互
3. **日记专属设计** - 强化时间维度,支持主题演化追踪
4. **双向价值** - 既是导航工具,又是成长可视化工具

**用户体验提升**:
- 从"孤立日记"到"关联网络"
- 从"手动维护"到"自动发现"
- 从"线性浏览"到"图谱探索"

**技术可行性**: 高 - 参考Obsidian成熟方案,适配Android移动端特性

**预计开发时间**: 4-6周(分3个Phase实施)

---

**文档生成时间**: 2026-07-08  
**适用项目**: Stradust Android APP  
**设计版本**: v1.0  