package com.aicompanion.plugin

import android.content.Context
import com.aicompanion.file.AgentWorkspace
import com.aicompanion.models.ToolDefinition
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件工具：让 AI 像 agent 一样读写文件。
 *
 * 与 [ShellPlugin] 的分工：
 * - 本工具做「结构化文件操作」：列目录、读、写、追加、删除、移动、复制、搜索。
 *   它**不经过 shell**，所以不依赖任何命令执行能力，零权限即可用（工作区限应用自己的目录）。
 * - 需要跑命令/处理二进制/操作系统时用 run_shell。
 *
 * 安全边界见 [AgentWorkspace]：所有路径都会 canonical 化并校验前缀，`..` 与软链逃不出去。
 */
class FilePlugin(private val context: Context) : ToolPlugin {

    companion object {
        private const val TAG = "FilePlugin"
        private val DATE_FMT = "MM-dd HH:mm"
    }

    override val name = "file"
    override val description = "读写/整理文件（列目录、读、写、追加、删除、移动、复制、搜索）"

    override fun isEnabled(): Boolean = SettingsManager(context).fileOpsEnabled

    override fun getDefinition() = ToolDefinition(
        name = "file",
        description = """像操作本地文件一样读写文件。适合：记笔记、生成并保存文档、整理资料、批量重命名、查找内容。

【路径写法】（不写前缀时默认走 workspace/）
- workspace/notes/a.md → 应用私有工作区（零权限，始终可用）
- external/a.md        → App 专属外部目录（零权限，插电脑可见）
- public/a.md          → /sdcard/StardustAgent（需要「所有文件访问权限」，未授权会明确报错）
先用 action=roots 看当前哪些目录可用，不要假设 public 一定可用。

【action】
- roots：列出可用工作区（不知道能写哪时先调这个）
- list：列目录。支持 recursive=true 与 glob 过滤，输出大小/时间/类型
- read：读文本文件。大文件用 offset（起始行，从 1 开始）+ limit（行数）分段读
- write：写入文件（覆盖整个文件，自动创建父目录）
- append：追加内容到文件末尾
- mkdir：创建目录（含多级）
- copy / move：复制 / 移动（move 也可用于重命名）
- delete：删除文件或目录。删非空目录必须 recursive=true
- search：按文件名或内容搜索，返回命中行（带行号）
- info：文件/目录元信息

【注意】
- 只能操作工作区内的路径，越界会被拒绝
- 二进制文件（图片/音视频/压缩包）read 会失败，请改用 run_shell 或其他专门工具
- 写入前如果目标已存在且内容重要，先用 read 确认一下再覆盖
- 一次写入上限 4MB，单次读取上限 256KB（超出请分段读）""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf(
                        "roots", "list", "read", "write", "append",
                        "mkdir", "copy", "move", "delete", "search", "info"
                    ),
                    "description" to "要执行的操作"
                ),
                "path" to mapOf("type" to "string", "description" to "目标路径，如 workspace/notes/a.md"),
                "to" to mapOf("type" to "string", "description" to "copy/move 的目标路径"),
                "content" to mapOf("type" to "string", "description" to "write/append 要写入的文本内容"),
                "recursive" to mapOf("type" to "boolean", "description" to "list/delete 是否递归，默认 false"),
                "glob" to mapOf("type" to "string", "description" to "list 时的名称过滤，如 *.md 或 *.txt"),
                "offset" to mapOf("type" to "integer", "description" to "read 的起始行（从 1 开始）"),
                "limit" to mapOf("type" to "integer", "description" to "read 最多读多少行；search 最多返回多少条"),
                "query" to mapOf("type" to "string", "description" to "search 的关键词（匹配文件名或文件内容）")
            ),
            "required" to listOf("action")
        )
    )

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val action = args.optString("action", "").lowercase()
        if (!SettingsManager(context).fileOpsEnabled) {
            return "文件读写功能未开启。请让用户到「设置 → 无障碍 → 文件读写」打开开关后再试。"
        }
        return try {
            when (action) {
                "roots" -> doRoots()
                "list" -> doList(args)
                "read" -> doRead(args)
                "write" -> doWrite(args, append = false)
                "append" -> doWrite(args, append = true)
                "mkdir" -> doMkdir(args)
                "copy" -> doCopyMove(args, move = false)
                "move" -> doCopyMove(args, move = true)
                "delete" -> doDelete(args)
                "search" -> doSearch(args)
                "info" -> doInfo(args)
                else -> "未知 action：$action（可用：roots/list/read/write/append/mkdir/copy/move/delete/search/info）"
            }
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "file $action 被拒绝: ${e.message}")
            e.message ?: "路径不被允许"
        } catch (e: Exception) {
            AppLogger.e(TAG, "file $action 失败: ${e.javaClass.simpleName}: ${e.message}")
            "操作失败：${e.message}"
        }
    }

    // ==================== 各 action 实现 ====================

    private fun doRoots(): String = buildString {
        appendLine("可用工作区：")
        AgentWorkspace.roots(context).forEach { r ->
            append("- ").append(r.label).append("  → 前缀 ").append(r.prefix)
            append("  路径：").append(r.dir.absolutePath)
            if (r.note.isNotBlank()) append("  （").append(r.note).append("）")
            appendLine()
        }
        if (!AgentWorkspace.hasPublicStorageAccess(context)) {
            appendLine()
            appendLine(AgentWorkspace.publicUnavailableReason())
        }
        appendLine()
        append("写入时直接写相对路径即可，例如：write path=notes/todo.md")
    }

    private fun doList(args: JSONObject): String {
        val path = args.optString("path", "workspace")
        val recursive = args.optBoolean("recursive", false)
        val glob = args.optString("glob", "").trim()
        val dir = AgentWorkspace.resolve(context, path)
        if (!dir.exists()) return "目录不存在：${AgentWorkspace.displayPath(context, dir)}"
        if (!dir.isDirectory) return "${AgentWorkspace.displayPath(context, dir)} 不是目录（用 action=info 看文件信息）"

        val matches = if (glob.isBlank()) null else globToRegex(glob)
        // 提到循环外：之前写成 getter 会导致每个文件都 new 一个 SimpleDateFormat
        val dateFmt = SimpleDateFormat(DATE_FMT, Locale.getDefault())
        val out = StringBuilder()
        var fileCount = 0
        var dirCount = 0
        var truncated = false
        val maxEntries = 300

        fun walk(d: File, depth: Int, maxDepth: Int) {
            if (truncated) return
            val children = try { d.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) } catch (_: Exception) { null }
            if (children == null) {
                out.append(indent(depth)).append("(无法读取)\n")
                return
            }
            for (f in children) {
                if (fileCount + dirCount >= maxEntries) { truncated = true; return }
                val name = f.name
                if (f.isDirectory) {
                    if (matches == null || !recursive) {
                        dirCount++
                        out.append(indent(depth)).append("📁 ").append(name).append("/")
                        val cnt = try { f.list()?.size ?: 0 } catch (_: Exception) { 0 }
                        out.append("  (").append(cnt).append(" 项)\n")
                    }
                    if (recursive && depth < maxDepth) walk(f, depth + 1, maxDepth)
                } else {
                    if (matches != null && !matches.matches(name)) continue
                    fileCount++
                    out.append(indent(depth)).append("📄 ").append(name)
                    out.append("  ").append(AgentWorkspace.humanSize(f.length()))
                    out.append("  ").append(dateFmt.format(Date(f.lastModified())))
                    out.append('\n')
                }
            }
        }

        walk(dir, 0, if (recursive) 6 else 1)

        val header = buildString {
            append("目录：").append(AgentWorkspace.displayPath(context, dir))
            append("  共 ").append(dirCount).append(" 个子目录 / ").append(fileCount).append(" 个文件")
            if (glob.isNotBlank()) append("  （过滤：").append(glob).append("）")
            appendLine()
        }
        val body = if (fileCount == 0 && dirCount == 0) "(空目录)\n" else out.toString()
        return header + body + if (truncated) "…(条目过多已截断，可用 glob 过滤或缩小范围)" else ""
    }

    private fun doRead(args: JSONObject): String {
        val path = args.optString("path", "")
        if (path.isBlank()) return "请提供 path"
        val f = AgentWorkspace.resolve(context, path)
        if (!f.exists()) return "文件不存在：${AgentWorkspace.displayPath(context, f)}"
        if (f.isDirectory) return "${AgentWorkspace.displayPath(context, f)} 是目录，用 action=list"
        if (AgentWorkspace.looksBinary(f)) {
            return "这是二进制文件（${f.extension.ifBlank { "未知类型" }}，${AgentWorkspace.humanSize(f.length())}），无法按文本读取。"
        }
        if (f.length() > AgentWorkspace.MAX_READ_BYTES && args.optInt("offset", 0) <= 0) {
            return "文件较大（${AgentWorkspace.humanSize(f.length())}，上限 ${AgentWorkspace.humanSize(AgentWorkspace.MAX_READ_BYTES.toLong())}）。" +
                "请用 offset/limit 分段读取，例如 offset=1 limit=500。"
        }

        val offset = args.optInt("offset", 0).coerceAtLeast(0)
        val limit = args.optInt("limit", 0)
        val allLines = f.readLines()
        val start = if (offset > 0) (offset - 1).coerceAtMost(allLines.size) else 0
        val end = if (limit > 0) (start + limit).coerceAtMost(allLines.size) else allLines.size
        val slice = allLines.subList(start, end)

        val sb = StringBuilder()
        sb.append("文件：").append(AgentWorkspace.displayPath(context, f))
        sb.append("  共 ").append(allLines.size).append(" 行  ").append(AgentWorkspace.humanSize(f.length()))
        if (offset > 0 || limit > 0) {
            sb.append("  本段：第 ").append(start + 1).append("-").append(end).append(" 行")
        }
        sb.appendLine()
        sb.appendLine("--- 内容 ---")
        // 带行号，方便 AI 后续做精细编辑
        slice.forEachIndexed { i, line -> sb.append(start + i + 1).append("│ ").append(line).append('\n') }
        return sb.toString().trimEnd()
    }

    private fun doWrite(args: JSONObject, append: Boolean): String {
        val path = args.optString("path", "")
        if (path.isBlank()) return "请提供 path"
        val content = args.optString("content", "")
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > AgentWorkspace.MAX_WRITE_BYTES) {
            return "内容过大（${AgentWorkspace.humanSize(bytes.size.toLong())}），单次写入上限 ${AgentWorkspace.humanSize(AgentWorkspace.MAX_WRITE_BYTES.toLong())}。请分多次写。"
        }
        val f = AgentWorkspace.resolve(context, path)
        if (f.isDirectory) return "${AgentWorkspace.displayPath(context, f)} 是目录，不能写入"
        f.parentFile?.let { if (!it.exists()) it.mkdirs() }
        if (append) f.appendText(content, Charsets.UTF_8) else f.writeText(content, Charsets.UTF_8)
        val verb = if (append) "已追加" else "已写入"
        return "$verb：${AgentWorkspace.displayPath(context, f)}\n当前大小：${AgentWorkspace.humanSize(f.length())}" +
            if (append) "" else "\n（共 ${content.length} 字符）"
    }

    private fun doMkdir(args: JSONObject): String {
        val path = args.optString("path", "")
        if (path.isBlank()) return "请提供 path"
        val d = AgentWorkspace.resolve(context, path)
        if (d.exists()) return if (d.isDirectory) "目录已存在：${AgentWorkspace.displayPath(context, d)}" else "同名文件已存在，无法创建目录"
        val ok = d.mkdirs()
        return if (ok || d.exists()) "已创建目录：${AgentWorkspace.displayPath(context, d)}" else "创建目录失败"
    }

    private fun doCopyMove(args: JSONObject, move: Boolean): String {
        val srcPath = args.optString("path", "")
        val dstPath = args.optString("to", "")
        if (srcPath.isBlank() || dstPath.isBlank()) return "请提供 path 与 to"
        val verb = if (move) "移动" else "复制"
        val src = AgentWorkspace.resolve(context, srcPath)
        if (!src.exists()) return "源不存在：${AgentWorkspace.displayPath(context, src)}"
        var dst = AgentWorkspace.resolve(context, dstPath)
        // 目标是已存在目录 → 放进去
        if (dst.isDirectory) dst = File(dst, src.name)
        if (dst.exists()) return "目标已存在：${AgentWorkspace.displayPath(context, dst)}（请换一个目标名或先删除）"
        dst.parentFile?.let { if (!it.exists()) it.mkdirs() }

        if (src.isDirectory) {
            dst.mkdirs()
            copyDir(src, dst)
        } else {
            src.copyTo(dst, overwrite = false)
        }
        if (move) {
            val ok = deleteRecursive(src)
            if (!ok) return "已复制到 ${AgentWorkspace.displayPath(context, dst)}，但原路径删除失败（可能被占用）"
        }
        return "已${verb}：${AgentWorkspace.displayPath(context, src)} → ${AgentWorkspace.displayPath(context, dst)}"
    }

    private fun doDelete(args: JSONObject): String {
        val path = args.optString("path", "")
        if (path.isBlank()) return "请提供 path"
        val recursive = args.optBoolean("recursive", false)
        val target = AgentWorkspace.resolve(context, path)
        if (!target.exists()) return "目标不存在：${AgentWorkspace.displayPath(context, target)}"

        // 拒绝删除工作区根目录本身
        AgentWorkspace.roots(context).forEach { r ->
            if (target.canonicalPath == r.dir.canonicalPath) {
                return "拒绝删除工作区根目录（${r.prefix}/）。只能删它里面的内容。"
            }
        }

        val shown = AgentWorkspace.displayPath(context, target)
        if (target.isDirectory) {
            val children = target.list()?.size ?: 0
            if (children > 0 && !recursive) {
                return "目录 $shown 下还有 $children 项。确认要整目录删除请带 recursive=true。"
            }
            val ok = deleteRecursive(target)
            return if (ok) "已删除目录：$shown" else "删除失败（可能有文件被占用）"
        }
        return if (target.delete()) "已删除文件：$shown" else "删除失败：$shown"
    }

    private fun doSearch(args: JSONObject): String {
        val path = args.optString("path", "workspace")
        val query = args.optString("query", "").trim()
        if (query.isBlank()) return "请提供 query（搜索关键词）"
        val limit = args.optInt("limit", 50).coerceIn(1, AgentWorkspace.MAX_SEARCH_HITS)
        val root = AgentWorkspace.resolve(context, path)
        if (!root.exists()) return "路径不存在：${AgentWorkspace.displayPath(context, root)}"

        val lower = query.lowercase()
        val hits = StringBuilder()
        var count = 0
        var scanned = 0
        val maxScanBytes = 2L * 1024 * 1024

        fun searchFile(f: File) {
            if (count >= limit) return
            val nameHit = f.name.lowercase().contains(lower)
            val rel = AgentWorkspace.displayPath(context, f)
            if (nameHit) {
                count++
                hits.append("📄 ").append(rel).append("  ← 文件名匹配\n")
            }
            if (f.length() > maxScanBytes || AgentWorkspace.looksBinary(f)) return
            try {
                f.readLines().forEachIndexed { i, line ->
                    if (count >= limit) return@forEachIndexed
                    if (line.contains(query, ignoreCase = true)) {
                        count++
                        hits.append(rel).append(':').append(i + 1).append("  ").append(line.trim().take(160)).append('\n')
                    }
                }
            } catch (_: Exception) {
                // 忽略无法读取的文件
            }
        }

        fun walk(d: File, depth: Int) {
            if (count >= limit || depth > 6) return
            val children = try { d.listFiles() } catch (_: Exception) { null } ?: return
            for (f in children) {
                if (count >= limit || scanned > 2000) return
                if (f.isDirectory) walk(f, depth + 1) else { scanned++; searchFile(f) }
            }
        }

        if (root.isFile) searchFile(root) else walk(root, 0)
        return if (count == 0) "未找到匹配「$query」的内容（已扫描 $scanned 个文件）"
        else "匹配「$query」共 $count 条：\n" + hits.toString().trimEnd() +
            if (count >= limit) "\n…(已达上限 $limit 条，可缩小 path 范围)" else ""
    }

    private fun doInfo(args: JSONObject): String {
        val path = args.optString("path", "")
        if (path.isBlank()) return "请提供 path"
        val f = AgentWorkspace.resolve(context, path)
        if (!f.exists()) return "不存在：${AgentWorkspace.displayPath(context, f)}"
        val sb = StringBuilder()
        sb.appendLine("路径：${AgentWorkspace.displayPath(context, f)}")
        sb.appendLine("绝对路径：${f.absolutePath}")
        sb.appendLine("类型：${if (f.isDirectory) "目录" else "文件"}")
        sb.appendLine("所在工作区：${AgentWorkspace.describeRoot(context, f)}")
        sb.appendLine("可读：${f.canRead()}  可写：${f.canWrite()}")
        sb.appendLine("修改时间：${SimpleDateFormat(DATE_FMT, Locale.getDefault()).format(Date(f.lastModified()))}")
        if (f.isDirectory) {
            val children = try { f.listFiles() } catch (_: Exception) { null }
            sb.appendLine("子项：${children?.size ?: 0} 个")
            children?.sortedBy { it.name }?.take(30)?.forEach {
                sb.appendLine("  ${if (it.isDirectory) "📁" else "📄"} ${it.name}${if (it.isDirectory) "/" else ""}")
            }
        } else {
            sb.appendLine("大小：${AgentWorkspace.humanSize(f.length())}")
            sb.appendLine("文本文件：${if (AgentWorkspace.looksBinary(f)) "否" else "是"}")
        }
        return sb.toString().trimEnd()
    }

    // ==================== 辅助 ====================

    private fun indent(depth: Int): String = "  ".repeat(depth)

    /** 极简 glob：支持 * 与 ?，其余按字面量 */
    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder()
        glob.forEach { c ->
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                else -> sb.append(Regex.escape(c.toString()))
            }
        }
        return Regex("^$sb$", RegexOption.IGNORE_CASE)
    }

    private fun copyDir(src: File, dst: File) {
        val children = try { src.listFiles() } catch (_: Exception) { null } ?: return
        children.forEach { child ->
            val target = File(dst, child.name)
            if (child.isDirectory) {
                target.mkdirs()
                copyDir(child, target)
            } else {
                try { child.copyTo(target, overwrite = false) } catch (_: Exception) {}
            }
        }
    }

    /** 递归删除（不用 stdlib 的 deleteRecursively，避免与成员函数同名互相遮蔽） */
    private fun deleteRecursive(f: File): Boolean {
        if (f.isDirectory) {
            val children = try { f.listFiles() } catch (_: Exception) { null }
            children?.forEach { deleteRecursive(it) }
        }
        return try { f.delete() } catch (_: Exception) { false }
    }
}
