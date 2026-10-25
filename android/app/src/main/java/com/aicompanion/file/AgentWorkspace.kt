package com.aicompanion.file

import android.content.Context
import android.os.Build
import android.os.Environment
import com.aicompanion.util.AppLogger
import java.io.File

/**
 * AI 文件工作区：界定「AI 能读写哪些目录」，并做路径越界防护。
 *
 * 设计原则（与安卓权限模型对齐）：
 * 1. 默认只开放**本应用自己的目录** —— 私有目录与 App 专属外部目录，两者都**不需要任何权限**，
 *    装机即可用。这覆盖了「AI 帮我记笔记 / 生成文件 / 整理资料」的绝大多数需求。
 * 2. 公共存储（/sdcard 下的文档、下载）需要 MANAGE_EXTERNAL_STORAGE，
 *    没授权时**明确拒绝并说明原因**，而不是静默失败。
 * 3. 任何路径都会先 canonical 化再校验前缀，`..` 与符号链接都逃不出去。
 *
 * 路径写法（给 AI 用，简单且不会歧义）：
 * - `workspace/notes/a.md`（或直接 `notes/a.md`）→ 私有工作区
 * - `external/a.md`                              → App 专属外部目录
 * - `public/a.md`                                → /sdcard/StardustAgent（需授权）
 * - 绝对路径只要落在上述任一目录内也接受
 */
object AgentWorkspace {

    private const val TAG = "AgentWorkspace"

    /** 私有工作区子目录名（filesDir 下） */
    const val DIR_NAME = "agent_workspace"

    /** 公共目录名（/sdcard 下） */
    const val PUBLIC_DIR_NAME = "StardustAgent"

    /** 单次读取上限，避免把大文件灌进上下文 */
    const val MAX_READ_BYTES = 256 * 1024

    /** 单次写入上限 */
    const val MAX_WRITE_BYTES = 4 * 1024 * 1024

    /** 搜索结果上限 */
    const val MAX_SEARCH_HITS = 100

    enum class Kind { PRIVATE, EXTERNAL, PUBLIC }

    data class Root(
        val kind: Kind,
        val label: String,
        val prefix: String,
        val dir: File,
        val writable: Boolean,
        val note: String = ""
    )

    // ==================== 根目录 ====================

    /** 私有工作区：<app>/files/agent_workspace —— 零权限，始终可用 */
    fun privateRoot(context: Context): File =
        File(context.filesDir, DIR_NAME).also { ensureDir(it) }

    /** App 专属外部目录：/sdcard/Android/data/<pkg>/files/agent_workspace —— 零权限（API 19+） */
    fun externalRoot(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, DIR_NAME).also { d -> ensureDir(d) } }

    /** 公共目录：/sdcard/StardustAgent —— 需要 MANAGE_EXTERNAL_STORAGE（API 30+） */
    fun publicRoot(context: Context): File? {
        if (!hasPublicStorageAccess(context)) return null
        val dir = File(Environment.getExternalStorageDirectory(), PUBLIC_DIR_NAME)
        ensureDir(dir)
        return dir
    }

    /**
     * 是否有公共存储的完整读写权限。
     * API 30+ 只能靠 MANAGE_EXTERNAL_STORAGE；API 29 及以下看运行时 WRITE_EXTERNAL_STORAGE。
     * context 传 null 时只能判断 API 30+ 的情况（用在没有 Context 的场景）。
     */
    fun hasPublicStorageAccess(context: Context? = null): Boolean = try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Environment.isExternalStorageManager()
            context != null ->
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            else -> false
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "hasPublicStorageAccess 失败: ${e.message}")
        false
    }

    /** 当前可用的全部根目录 */
    fun roots(context: Context): List<Root> {
        val list = mutableListOf<Root>()
        val priv = privateRoot(context)
        list += Root(Kind.PRIVATE, "私有工作区", "workspace", priv, true, "零权限，应用私有")
        externalRoot(context)?.let {
            list += Root(Kind.EXTERNAL, "App 专属外部目录", "external", it, true, "零权限，插电脑可在 Android/data 下看到")
        }
        publicRoot(context)?.let {
            list += Root(Kind.PUBLIC, "公共目录", "public", it, true, "需要「所有文件访问权限」")
        }
        return list
    }

    /** 公共目录不可用时的说明（给 AI 解释给用户听） */
    fun publicUnavailableReason(): String =
        "公共目录（/sdcard/$PUBLIC_DIR_NAME）当前不可用：Android 11 起访问公共存储需要用户在「设置 → 应用 → 特殊权限 → 所有文件访问权限」中授权。\n" +
            "可以改用 workspace/（应用私有目录，零权限，随时可用）。"

    // ==================== 路径解析 ====================

    /**
     * 把 AI 给的路径解析成真实 File，并确保它落在允许的根目录内。
     * @throws SecurityException 路径越界或目标根目录不可用
     */
    fun resolve(context: Context, rawPath: String): File {
        val input = rawPath.trim()
        require(input.isNotEmpty()) { "路径为空" }

        val normalized = input.replace('\\', '/').removePrefix("./")

        // 1) 带前缀的写法
        val byPrefix = matchPrefix(context, normalized)
        if (byPrefix != null) return byPrefix

        // 2) 绝对路径：必须落在某个根目录内
        if (normalized.startsWith("/")) {
            val f = File(normalized)
            val canon = try { f.canonicalFile } catch (e: Exception) { throw SecurityException("路径无法解析：$input") }
            val hit = roots(context).firstOrNull { isInside(canon, it.dir) }
                ?: throw SecurityException(
                    "拒绝访问：$input 不在允许的工作区内。可用目录见 action=roots。 " +
                        "（想访问公共存储请先用 action=roots 确认是否已授权）"
                )
            return canon
        }

        // 3) 无前缀的相对路径 → 默认走私有工作区
        val priv = privateRoot(context)
        return safeChild(priv, normalized, input)
    }

    private fun matchPrefix(context: Context, path: String): File? {
        val idx = path.indexOf('/')
        val head = (if (idx >= 0) path.substring(0, idx) else path).lowercase()
        val rest = if (idx >= 0) path.substring(idx + 1) else ""
        return when (head) {
            "workspace", "work", "private" -> safeChild(privateRoot(context), rest, path)
            "external", "ext", "app" -> {
                val root = externalRoot(context)
                    ?: throw SecurityException("App 专属外部目录当前不可用（外部存储可能未挂载）")
                safeChild(root, rest, path)
            }
            "public", "sdcard", "storage" -> {
                val root = publicRoot(context) ?: throw SecurityException(publicUnavailableReason())
                safeChild(root, rest, path)
            }
            else -> null
        }
    }

    /** 拼接并校验子路径，防止 `..` 与符号链接逃逸 */
    private fun safeChild(root: File, relative: String, original: String): File {
        val child = File(root, relative)
        val canon = try { child.canonicalFile } catch (e: Exception) { throw SecurityException("路径无法解析：$original") }
        if (!isInside(canon, root)) {
            throw SecurityException("拒绝访问：$original 试图跳出工作区（$root）")
        }
        return canon
    }

    /** child 是否在 root 之内（含 root 自身） */
    fun isInside(child: File, root: File): Boolean {
        val c = try { child.canonicalPath } catch (_: Exception) { child.absolutePath }
        val r = try { root.canonicalPath } catch (_: Exception) { root.absolutePath }
        return c == r || c.startsWith(r.trimEnd('/') + "/")
    }

    /** 该路径属于哪个根目录（用于展示） */
    fun describeRoot(context: Context, file: File): String =
        roots(context).firstOrNull { isInside(file, it.dir) }?.let { "${it.label}(${it.prefix})" } ?: "工作区外"

    // ==================== 工具方法 ====================

    private fun ensureDir(dir: File) {
        if (!dir.exists()) {
            val ok = try { dir.mkdirs() } catch (_: Exception) { false }
            if (!ok && !dir.exists()) AppLogger.w(TAG, "创建目录失败: ${dir.absolutePath}")
        }
    }

    /** 人类可读的大小 */
    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format("%.1fMB", bytes / 1024.0 / 1024.0)
        else -> String.format("%.2fGB", bytes / 1024.0 / 1024.0 / 1024.0)
    }

    /** 相对路径展示（把绝对路径缩成 workspace/xxx 这种形式，省 token） */
    fun displayPath(context: Context, file: File): String {
        roots(context).forEach { r ->
            if (isInside(file, r.dir)) {
                val rel = try { r.dir.toURI().relativize(file.toURI()).path } catch (_: Exception) { "" }
                val clean = rel.trimEnd('/')
                return if (clean.isBlank()) r.prefix else "${r.prefix}/$clean"
            }
        }
        return file.absolutePath
    }

    /** 粗略判断是不是文本文件（用于 read 时给出友好提示） */
    fun looksBinary(file: File): Boolean {
        val ext = file.extension.lowercase()
        val binaryExts = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "heic",
            "mp3", "wav", "ogg", "m4a", "flac", "aac",
            "mp4", "mkv", "avi", "mov", "webm", "3gp",
            "zip", "rar", "7z", "tar", "gz", "apk", "dex", "jar", "so", "bin",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "ttf", "otf", "moc3"
        )
        if (ext in binaryExts) return true
        // 无扩展名或未知：探测前 8KB 是否含 NUL
        return try {
            file.inputStream().use { ins ->
                val buf = ByteArray(8192)
                val n = ins.read(buf)
                if (n <= 0) return false
                for (i in 0 until n) if (buf[i].toInt() == 0) return true
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
