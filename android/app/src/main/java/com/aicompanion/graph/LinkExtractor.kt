package com.aicompanion.graph

import com.aicompanion.util.AppLogger

/**
 * 链接提取工具
 *
 * 从日记内容中自动提取[[xxx]]格式的显式链接,
 * 并基于规则提取隐式链接(人名、地点、主题等实体)。
 *
 * 设计参考: Obsidian Wikilink语法
 *
 * 支持的链接格式:
 * - [[日记:2024-01-15]] - 链接到特定日期日记
 * - [[主题:健康]] - 链接到主题笔记
 * - [[人物:张三]] - 链接到人物笔记
 * - [[地点:北京]] - 链接到地点笔记
 * - [[事件:生日]] - 链接到事件笔记
 * - [[日记:2024-01-15|昨天]] - 带别名的链接
 */
object LinkExtractor {

    private const val TAG = "LinkExtractor"

    // 显式链接正则: [[类型:目标]] 或 [[类型:目标|别名]]
    private val explicitLinkPattern = Regex(
        "\\[\\[(日记|主题|人物|地点|事件):([^\\]|\\]]+)(?:\\|([^\\]]+))?\\]\\]"
    )

    // 日期模式: 匹配 yyyy-MM-dd 格式
    private val datePattern = Regex("(\\d{4}-\\d{2}-\\d{2})")

    // 人名识别模式: 常见中文姓名(2-4字,以称呼结尾)
    private val personPattern = Regex(
        "(?:和|跟|与|向|给|告诉|问|见|遇到|约|找|打电话给|微信[聊天给])\\s*([\\u4e00-\\u9fa5]{2,4})(?:同学|老师|朋友|同事|经理|老板|主任|先生|女士|同学|小伙伴)?"
    )

    // 地点识别模式: 常见地点关键词
    private val locationKeywords = listOf(
        "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "西安", "南京",
        "重庆", "苏州", "天津", "长沙", "郑州", "青岛", "大连", "宁波", "厦门",
        "公司", "学校", "家", "医院", "公园", "咖啡厅", "餐厅", "图书馆",
        "机场", "火车站", "地铁站", "超市", "商场", "健身房"
    )

    // 主题关键词: 常见日记主题
    private val topicKeywords = listOf(
        "工作", "学习", "健康", "运动", "读书", "旅行", "美食",
        "感情", "家庭", "朋友", "梦想", "目标", "计划", "反思",
        "压力", "焦虑", "开心", "成长", "思考", "决策",
        "项目", "考试", "面试", "会议", "培训", "汇报"
    )

    /**
     * 提取日记中的所有链接
     *
     * @param content 日记内容
     * @param diaryDate 当前日记日期(用于生成日记链接)
     * @return 链接信息列表,按位置排序
     */
    fun extractLinks(content: String, diaryDate: String? = null): List<LinkInfo> {
        val links = mutableListOf<LinkInfo>()

        // 1. 提取显式链接 [[xxx:yyy]]
        links.addAll(extractExplicitLinks(content))

        // 2. 提取隐式链接(基于规则)
        links.addAll(extractImplicitLinks(content))

        // 3. 如果提供了日记日期,添加日记自身作为链接源
        if (diaryDate != null) {
            // 检查内容中是否提及其他日期
            links.addAll(extractDateLinks(content, diaryDate))
        }

        // 去重并按位置排序
        return links.distinctBy { it.toLinkId() + it.position }
            .sortedBy { it.position }
    }

    /**
     * 提取显式链接 [[xxx:yyy]] 或 [[xxx:yyy|别名]]
     */
    private fun extractExplicitLinks(content: String): List<LinkInfo> {
        val links = mutableListOf<LinkInfo>()

        explicitLinkPattern.findAll(content).forEach { match ->
            val prefix = match.groupValues[1]  // 日记/主题/人物/地点/事件
            val target = match.groupValues[2].trim()  // 目标名称
            val alias = match.groupValues.getOrNull(3)?.trim()  // 别名(可选)
            val position = match.range.first

            val type = LinkType.fromPrefix(prefix)
            links.add(LinkInfo(
                type = type,
                target = target,
                position = position,
                isExplicit = true,
                alias = alias
            ))
        }

        return links
    }

    /**
     * 提取隐式链接(基于规则匹配)
     *
     * 注意: 隐式链接只提取高置信度的实体,避免过度链接
     */
    private fun extractImplicitLinks(content: String): List<LinkInfo> {
        val links = mutableListOf<LinkInfo>()

        // 1. 提取人名(基于上下文关键词)
        personPattern.findAll(content).forEach { match ->
            val name = match.groupValues[1]
            // 过滤常见非人名词汇
            if (isValidPersonName(name)) {
                links.add(LinkInfo(
                    type = LinkType.PERSON,
                    target = name,
                    position = match.range.first,
                    isExplicit = false
                ))
            }
        }

        // 2. 提取地点(找出所有出现位置)
        for (location in locationKeywords) {
            var searchStart = 0
            while (true) {
                val pos = content.indexOf(location, searchStart)
                if (pos < 0) break
                links.add(LinkInfo(
                    type = LinkType.LOCATION,
                    target = location,
                    position = pos,
                    isExplicit = false
                ))
                searchStart = pos + location.length
            }
        }

        // 3. 提取主题关键词(找出所有出现位置)
        for (topic in topicKeywords) {
            var searchStart = 0
            while (true) {
                val pos = content.indexOf(topic, searchStart)
                if (pos < 0) break
                links.add(LinkInfo(
                    type = LinkType.TOPIC,
                    target = topic,
                    position = pos,
                    isExplicit = false
                ))
                searchStart = pos + topic.length
            }
        }

        return links
    }

    /**
     * 提取日期链接(内容中提及的其他日期)
     */
    private fun extractDateLinks(content: String, currentDate: String): List<LinkInfo> {
        val links = mutableListOf<LinkInfo>()

        datePattern.findAll(content).forEach { match ->
            val date = match.groupValues[1]
            // 排除当前日记日期
            if (date != currentDate) {
                links.add(LinkInfo(
                    type = LinkType.DIARY,
                    target = date,
                    position = match.range.first,
                    isExplicit = false
                ))
            }
        }

        return links
    }

    /**
     * 验证是否为有效人名
     * 过滤常见非人名词汇
     */
    private fun isValidPersonName(name: String): Boolean {
        // 排除常见非人名词汇
        val invalidNames = setOf(
            "今天", "昨天", "明天", "现在", "以后", "之前", "之后",
            "这里", "那里", "哪里", "什么", "怎么", "为什么",
            "这个", "那个", "这些", "那些", "我们", "你们", "他们",
            "自己", "别人", "大家", "所有", "一些", "有点",
            "觉得", "认为", "感觉", "想到", "发现", "决定",
            "公司", "学校", "医院", "家庭", "社会", "世界"
        )

        return name.length in 2..4 && name !in invalidNames
    }

    /**
     * 将文本中的链接转换为可点击的SpannableString段
     * (供TextView显示超链接样式)
     *
     * @param content 原始内容
     * @param links 链接列表
     * @return 链接段列表(文本+链接信息)
     */
    fun buildLinkSegments(content: String, links: List<LinkInfo>): List<LinkSegment> {
        if (links.isEmpty()) {
            return listOf(LinkSegment(content, null))
        }

        val segments = mutableListOf<LinkSegment>()
        var lastEnd = 0

        for (link in links.sortedBy { it.position }) {
            // 计算链接文本的范围
            val linkTextStart = link.position
            val linkTextEnd = if (link.isExplicit) {
                // 显式链接: [[xxx:yyy]] 的完整长度
                val bracketEnd = content.indexOf("]]", link.position)
                if (bracketEnd >= 0) bracketEnd + 2 else link.position + link.target.length
            } else {
                // 隐式链接: 目标名称的长度
                link.position + link.target.length
            }

            // 添加链接前的普通文本
            if (linkTextStart > lastEnd) {
                segments.add(LinkSegment(content.substring(lastEnd, linkTextStart), null))
            }

            // 添加链接文本
            val displayText = if (link.isExplicit) {
                link.getDisplayText()
            } else {
                link.target
            }
            segments.add(LinkSegment(displayText, link))

            lastEnd = linkTextEnd.coerceAtMost(content.length)
        }

        // 添加最后一段普通文本
        if (lastEnd < content.length) {
            segments.add(LinkSegment(content.substring(lastEnd), null))
        }

        return segments
    }

    /**
     * 在内容中插入显式链接语法
     *
     * @param content 原始内容
     * @param target 要链接的目标名称
     * @param type 链接类型
     * @param position 插入位置
     * @return 修改后的内容
     */
    fun insertLink(
        content: String,
        target: String,
        type: LinkType,
        position: Int
    ): String {
        val linkSyntax = "[[${type.prefix}:$target]]"
        val safePosition = position.coerceIn(0, content.length)
        return content.substring(0, safePosition) + linkSyntax + content.substring(safePosition)
    }

    /**
     * 将隐式链接转换为显式链接
     *
     * @param content 原始内容
     * @param link 要转换的隐式链接
     * @return 修改后的内容
     */
    fun convertToExplicitLink(content: String, link: LinkInfo): String {
        if (link.isExplicit) return content

        val originalText = content.substring(
            link.position,
            (link.position + link.target.length).coerceAtMost(content.length)
        )
        val linkSyntax = "[[${link.type.prefix}:${link.target}]]"

        return content.replaceRange(
            link.position,
            (link.position + link.target.length).coerceAtMost(content.length),
            linkSyntax
        )
    }

    /**
     * 批量提取所有日记的链接索引
     *
     * @param diaries 日记列表(日期→内容)
     * @return 链接索引(源ID→目标ID列表)
     */
    fun buildLinkIndex(diaries: List<Pair<String, String>>): Map<String, List<String>> {
        val index = mutableMapOf<String, MutableList<String>>()

        for ((date, content) in diaries) {
            val sourceId = "${LinkType.DIARY.prefix}:$date"
            val links = extractLinks(content, date)

            val targets = links.map { it.toLinkId() }.distinct()
            if (targets.isNotEmpty()) {
                index[sourceId] = targets.toMutableList()
            }
        }

        AppLogger.i(TAG, "链接索引构建完成: ${index.size}个源, ${index.values.sumOf { it.size }}条链接")
        return index
    }
}

/**
 * 链接段(用于UI显示)
 *
 * 表示文本中的一个段,可以是普通文本或可点击的链接
 */
data class LinkSegment(
    val text: String,          // 段文本
    val link: LinkInfo?        // 链接信息(null表示普通文本)
) {
    val isLink: Boolean get() = link != null
}