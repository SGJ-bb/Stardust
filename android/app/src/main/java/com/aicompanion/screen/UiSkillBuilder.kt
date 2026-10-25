package com.aicompanion.screen

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 把一个界面的无障碍节点树转成「给 AI 看的页面说明」。
 *
 * ## 为什么要转，而不是把原始树塞给模型
 *
 * 原始 UI 树一个页面动辄 300+ 节点、上万字，其中绝大多数是布局容器
 * （FrameLayout/LinearLayout/View），对"我该点哪儿"毫无帮助，还挤占上下文。
 * 人看界面也不会去数容器，只看有字的东西和能点的地方。
 *
 * 所以这里只抽三类节点：
 * - **有文字/描述的**（按钮、标题、列表项）
 * - **能点的**（clickable / long-clickable）
 * - **能输入的**（EditText）
 *
 * 抽样后一个页面通常 10~40 行，模型能直接照着操作。
 *
 * ## 为什么同时记坐标和文本
 *
 * 文本优先（不随分辨率变），坐标兜底（图标按钮没文字）。
 * 只给坐标的话换个分辨率就全废；只给文本的话遇到无字图标就抓瞎。
 */
object UiSkillBuilder {

    /** 单页最多收录多少个元素 */
    private const val MAX_ELEMENTS = 60

    data class Element(
        val text: String,
        val desc: String,
        val className: String,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val centerX: Int,
        val centerY: Int,
        val bounds: Rect,
    ) {
        val label: String get() = text.ifBlank { desc }
        val kind: String
            get() = when {
                editable -> "输入框"
                scrollable -> "可滚动区"
                clickable -> "可点"
                else -> "文本"
            }
    }

    data class Page(
        val packageName: String,
        val pageKey: String,
        val title: String,
        val elements: List<Element>,
        val width: Int,
        val height: Int,
    )

    /** 从根节点抽一页 */
    fun collect(
        root: AccessibilityNodeInfo,
        pageClass: String = "",
        maxElements: Int = MAX_ELEMENTS,
    ): Page? {
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg.isBlank()) return null

        val out = mutableListOf<Element>()
        val seen = mutableSetOf<String>()
        val screen = Rect()
        runCatching { root.getBoundsInScreen(screen) }

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 40 || out.size >= maxElements) return
            try {
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val cls = node.className?.toString().orEmpty()
                val clickable = node.isClickable
                val editable = node.isEditable || cls.contains("EditText")
                val scrollable = node.isScrollable

                val interesting = (text.isNotBlank() || desc.isNotBlank() || clickable || editable || scrollable)
                if (interesting) {
                    val b = Rect()
                    node.getBoundsInScreen(b)
                    val w = b.width()
                    val h = b.height()
                    // 过滤：0 尺寸、整屏容器（它"可点"但点了没意义）
                    val isFullScreen = w >= screen.width() * 0.95f && h >= screen.height() * 0.95f
                    val label = text.ifBlank { desc }
                    // 空标签又不可编辑又不是滚动区 → 多半是装饰容器
                    val meaningful = label.isNotBlank() || editable || (scrollable && w > 200)
                    if (!isFullScreen && meaningful && w > 0 && h > 0) {
                        val dedupKey = "$label|${cls.substringAfterLast('.')}|${b.centerX() / 20}|${b.centerY() / 20}"
                        if (seen.add(dedupKey)) {
                            out.add(
                                Element(
                                    text = text,
                                    desc = desc,
                                    className = cls.substringAfterLast('.'),
                                    clickable = clickable,
                                    editable = editable,
                                    scrollable = scrollable,
                                    centerX = b.centerX(),
                                    centerY = b.centerY(),
                                    bounds = Rect(b),
                                )
                            )
                        }
                    }
                }
                for (i in 0 until node.childCount) {
                    walk(node.getChild(i), depth + 1)
                    if (out.size >= maxElements) break
                }
            } catch (_: Exception) {
                // 节点在遍历途中失效是常态（界面刷新），跳过即可
            }
        }
        walk(root, 0)

        if (out.isEmpty()) return null

        // 页面标识：优先 Activity 类名；其次找一个"像标题"的文本。
        // 不能直接取第一个文本——真机上第一个往往是搜索框的提示语（"搜索系统设置项"），
        // 拿它当页面名会导致同一 App 的不同页面互相覆盖。
        val hintWords = listOf("搜索", "请输入", "输入", "查找")
        val candidates = out.filter {
            it.label.length in 2..12 &&
                !it.editable &&
                !it.clickable &&
                it.centerY > 120 &&                       // 跳过状态栏
                hintWords.none { w -> it.label.contains(w) } &&
                it.label.any { c -> c.isLetter() }         // 排除纯数字/符号
        }
        val key = pageClass.substringAfterLast('.').ifBlank {
            candidates.firstOrNull()?.label?.replace(Regex("[\\s/：:]+"), "_") ?: "页面"
        }
        val title = candidates.firstOrNull()?.label.orEmpty()

        return Page(
            packageName = pkg,
            pageKey = key,
            title = title,
            elements = out,
            width = screen.width(),
            height = screen.height(),
        )
    }

    /** 生成 markdown 小节（同一 App 的多个页面会拼在同一个 skill 里） */
    fun renderPage(page: Page): String = buildString {
        appendLine("## 页面：${page.pageKey}${if (page.title.isNotBlank() && page.title != page.pageKey) "（${page.title}）" else ""}")
        appendLine()
        appendLine("屏幕：${page.width}×${page.height}（坐标会随滚动/换行失效，优先按文字定位）")
        appendLine()

        val editable = page.elements.filter { it.editable }
        val labelled = page.elements.filter { !it.editable && it.label.isNotBlank() }
        val iconOnly = page.elements.filter { !it.editable && it.label.isBlank() && (it.clickable || it.scrollable) }

        if (editable.isNotEmpty()) {
            appendLine("### 输入框（用 tap_text 点进去再输字）")
            appendLine("| 提示文字 | 操作 |")
            appendLine("|---|---|")
            editable.take(6).forEach {
                val label = it.label.ifBlank { "（无提示）" }
                appendLine("| $label | tap_text \"$label\" → device text \"...\" |")
            }
            appendLine()
        }

        if (labelled.isNotEmpty()) {
            // 一律给"按文字"的操作方式，不给坐标。
            // 原因：列表顺序会变（QQ 联系人、微信会话、搜索结果），
            // 今天"第 3 个"是张三，明天就是李四；坐标同样会随滚动/换行失效。
            appendLine("### 可操作元素（按文字定位，不要记坐标）")
            appendLine("| 文字 | 操作 |")
            appendLine("|---|---|")
            labelled.take(40).forEach {
                val label = it.label.take(20)
                appendLine("| $label | device tap_text \"$label\" |")
            }
            appendLine()
        }

        if (iconOnly.isNotEmpty()) {
            appendLine("### 无文字的图标按钮（只能用坐标，尽少量）")
            appendLine("| 类型 | 坐标 |")
            appendLine("|---|---|")
            iconOnly.take(8).forEach {
                appendLine("| ${it.className}${if (it.scrollable) " 可滚动" else ""} | (${it.centerX}, ${it.centerY}) |")
            }
            appendLine()
        }

        val pageTexts = labelled.map { it.label }.distinct()
        if (pageTexts.isNotEmpty()) {
            appendLine("### 页面上有哪些字（用来确认自己在不在这一页）")
            pageTexts.take(40).joinToString("、").let { appendLine(it) }
            appendLine()
        }

        appendLine("### 操作要点")
        appendLine("- **一律用 device tap_text \"文字\" 定位**。列表顺序会变（QQ 联系人、微信会话、搜索结果），")
        appendLine("  记「第几个」或记坐标早晚会点错人")
        appendLine("- 找不到目标文字就先 device swipe 滚动，再重新 tap_text，不要猜坐标")
        appendLine("- 点击后用 device screenshot 或界面快照确认页面变了，再继续下一步")
        appendLine("- 只有完全没有文字的图标按钮，才用 device tap(x,y)")
    }

    /**
     * 把新页面合并进已有 skill 正文。
     *
     * 同一页面重复学习时**替换**该节，不追加——否则越学越长，
     * 而且旧坐标会和新界面冲突。
     *
     * 匹配顺序：① 页名相同；② 页名不同但文字重合度高（>50%）。
     * 第二条是必需的：国产 App 常常拿不到 Activity 名，页名只能从标题猜，
     * 同一页两次学可能猜出两个名字，只按名字匹配就会堆出两个几乎一样的页面。
     */
    fun mergePage(existing: String, pageMarkdown: String, pageKey: String): String {
        val header = "## 页面：$pageKey"
        val lines = existing.lines()

        var start = lines.indexOfFirst { it.startsWith(header) }
        if (start < 0) {
            // 按内容相似度找同页
            val newTexts = extractTableTexts(pageMarkdown)
            for (i in lines.indices) {
                if (!lines[i].startsWith("## 页面：")) continue
                var endIdx = lines.size
                for (j in i + 1 until lines.size) {
                    if (lines[j].startsWith("## ")) { endIdx = j; break }
                }
                val oldTexts = extractTableTexts(lines.subList(i, endIdx).joinToString("\n"))
                if (jaccard(newTexts, oldTexts) >= 0.5f) { start = i; break }
            }
        }
        if (start < 0) {
            return (existing.trimEnd() + "\n\n" + pageMarkdown.trimEnd()).trim()
        }
        // 找到下一个 ## 之前的位置
        var end = lines.size
        for (i in start + 1 until lines.size) {
            if (lines[i].startsWith("## ")) { end = i; break }
        }
        val before = lines.subList(0, start).joinToString("\n")
        val after = lines.subList(end, lines.size).joinToString("\n")
        return listOf(before.trimEnd(), pageMarkdown.trimEnd(), after.trimStart())
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    /** 从 markdown 表格里抽出第一列文本，用于判断两页是不是同一页 */
    private fun extractTableTexts(md: String): Set<String> =
        md.lines()
            .filter { it.startsWith("|") && !it.contains("---") }
            .mapNotNull { it.split("|").getOrNull(1)?.trim() }
            .filter { it.isNotEmpty() && it != "文字" && it != "文字/描述" && it != "提示文字" }
            .toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val inter = a.count { it in b }
        val union = (a + b).size
        return if (union == 0) 0f else inter.toFloat() / union
    }
}
