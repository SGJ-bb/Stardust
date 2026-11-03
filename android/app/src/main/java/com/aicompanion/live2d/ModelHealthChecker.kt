package com.aicompanion.live2d

import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

/**
 * Live2D 模型体检。
 *
 * 起因：接入第三方模型（尤其是 Cubism 5 导出的）时踩过不少坑 ——
 * 贴图超过 GPU 上限导致整体黑屏、正片叠底的阴影层渲染成黑方块、
 * 缺 HitAreas 直接报错、没有 Idle 动作所以一直僵着。这些在导入那一刻就能检查出来，
 * 不该等用户看到黑屏再去猜。
 *
 * 这里只做**静态检查**（读 moc3 头部、PNG 头、model3.json），不加载模型，
 * 所以几毫秒就能出结果，可以随列表一起展示。
 */
object ModelHealthChecker {

    enum class Level { BLOCKER, WARNING, INFO }

    data class Issue(
        val level: Level,
        val title: String,
        val detail: String
    )

    data class Report(
        val issues: List<Issue>,
        val mocVersion: Int,
        val maxTextureSide: Int,
        val textureCount: Int
    ) {
        val blockers: List<Issue> get() = issues.filter { it.level == Level.BLOCKER }
        val warnings: List<Issue> get() = issues.filter { it.level == Level.WARNING }

        /** 列表里那一行短标签 */
        val summary: String
            get() = when {
                blockers.isNotEmpty() -> "⚠ ${blockers.size} 项需要注意"
                warnings.isNotEmpty() -> "⚠ ${warnings.size} 项建议查看"
                issues.isNotEmpty() -> "✓ 可以使用（${issues.size} 条提示）"
                else -> "✓ 检查通过"
            }

        /** 详情弹窗的标题 */
        val headline: String
            get() = when {
                blockers.isNotEmpty() -> "这个模型可能有显示问题"
                warnings.isNotEmpty() -> "这个模型有几处建议留意"
                else -> "这个模型状态良好"
            }
    }

    /** GPU 纹理上限的安全值。超过它的贴图在部分机型上会整块加载失败。 */
    private const val SAFE_TEXTURE_SIDE = 4096
    private const val BIG_MODEL_MB = 120

    fun check(folder: File): Report {
        val issues = mutableListOf<Issue>()

        val moc3 = folder.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".moc3") }
        val modelJson = folder.walkTopDown().firstOrNull {
            it.isFile && (it.name.endsWith(".model3.json") || it.name.endsWith(".model.json"))
        }

        val mocVersion = moc3?.let { readMocVersion(it) } ?: 0
        val textures = collectTextures(folder)
        val maxSide = textures.maxOfOrNull { maxOf(it.first, it.second) } ?: 0
        val totalMb = (folder.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024.0 * 1024.0)).toInt()

        // ---- moc3 ----
        if (moc3 == null) {
            issues += Issue(
                Level.BLOCKER,
                "缺少 model.moc3",
                "这个文件夹里没找到 .moc3 模型文件，模型无法加载。请确认导入的是完整的模型文件夹。"
            )
        }
        if (modelJson == null) {
            issues += Issue(
                Level.BLOCKER,
                "缺少 model3.json",
                "没有找到 .model3.json 配置文件，App 不知道该怎么加载这个模型。"
            )
        }

        // ---- 贴图尺寸：这是最容易被忽略、后果又最严重的一条 ----
        if (maxSide > SAFE_TEXTURE_SIDE) {
            val bigOnes = textures.filter { maxOf(it.first, it.second) > SAFE_TEXTURE_SIDE }
                .joinToString("、") { "${it.first}×${it.second}" }
            issues += Issue(
                Level.WARNING,
                "贴图尺寸超过 4096",
                "有 ${bigOnes} 的贴图，超过了绝大多数手机 GPU 的纹理上限（4096）。\n\n" +
                    "不过不用太担心：App 加载前会自动把超大贴图降采样，通常还是能正常显示的，\n" +
                    "代价是清晰度掉一点、加载慢一些。\n\n" +
                    "想保持最佳画质，建议在 Cubism Editor 导出时把贴图控制在 4096 以内（勾选自动分割更保险）。"
            )
        }

        // ---- Cubism 5 ----
        if (mocVersion >= 5) {
            issues += Issue(
                Level.WARNING,
                "Cubism 5 模型",
                "这是 Cubism 5 导出的模型（moc3 v$mocVersion），而 App 当前的渲染器基于 Cubism 4。\n\n" +
                    "大部分模型能正常显示，但少数用到了「正片叠底」的效果层（典型的是脸上一块黑色阴影、\n" +
                    "眼睛上的高光遮罩）可能出现颜色不对。\n\n" +
                    "如果看到模型脸上有奇怪的黑色方块，基本就是这个原因。"
            )
        }

        // ---- model3.json 里的结构 ----
        if (modelJson != null) {
            val json = runCatching { JSONObject(modelJson.readText()) }.getOrNull()
            if (json != null) {
                val refs = json.optJSONObject("FileReferences")

                // Cubism 5 的运行库要求必须有 HitAreas，C4 不要求
                val hitAreas = json.optJSONArray("HitAreas")
                if ((hitAreas == null || hitAreas.length() == 0) && mocVersion >= 5) {
                    issues += Issue(
                        Level.WARNING,
                        "缺少点击区域（HitAreas）",
                        "model3.json 里没有 HitAreas，Cubism 5 的加载器会直接报错。\n\n" +
                            "App 会自动补一个空的，不影响使用，但模型的头部/身体点击互动会失效。"
                    )
                }

                // 待机动作
                val motions = refs?.optJSONObject("Motions") ?: refs?.optJSONObject("motions")
                val idle = motions?.optJSONArray("Idle")
                if (idle == null || idle.length() == 0) {
                    issues += Issue(
                        Level.WARNING,
                        "没有待机动作",
                        "模型里没有 Idle 动作组，加载后会一直保持同一个姿势（只有眨眼和呼吸）。\n\n" +
                            "如果模型包里其实带动画文件（比如 Animation 目录），App 会自动挑一个当待机。\n" +
                            "完全没有的话就只能一直不动了。"
                    )
                }

                // 眨眼 / 口型
                val groups = json.optJSONArray("Groups")
                var hasBlink = false
                var hasLipSync = false
                if (groups != null) {
                    for (i in 0 until groups.length()) {
                        val g = groups.optJSONObject(i) ?: continue
                        when (g.optString("Name")) {
                            "EyeBlink" -> hasBlink = true
                            "LipSync" -> hasLipSync = true
                        }
                    }
                }
                if (!hasBlink) {
                    issues += Issue(
                        Level.INFO,
                        "不会自动眨眼",
                        "没有配置 EyeBlink 参数组，模型不会自己眨眼，看起来会有点僵。"
                    )
                }
                if (!hasLipSync) {
                    issues += Issue(
                        Level.INFO,
                        "没有口型参数",
                        "没有配置 LipSync 参数组，说话时嘴不会动。"
                    )
                }

                // 物理
                val physics = refs?.optString("Physics").orEmpty()
                if (physics.isBlank()) {
                    issues += Issue(
                        Level.INFO,
                        "没有物理效果",
                        "没找到 physics3.json，头发/尾巴不会跟着晃。"
                    )
                }
            }
        }

        // ---- 体积 ----
        if (totalMb > BIG_MODEL_MB) {
            issues += Issue(
                Level.INFO,
                "体积偏大（${totalMb}MB）",
                "超过 ${BIG_MODEL_MB}MB 的模型在低端机上加载会比较慢，切换模型时可能要等几秒。"
            )
        }

        // ---- VTube Studio 残留 ----
        val vtsLeftovers = folder.listFiles()?.filter {
            it.name.endsWith(".vtube.json") || it.name == "items_pinned_to_model.json"
        }.orEmpty()
        if (vtsLeftovers.isNotEmpty()) {
            issues += Issue(
                Level.INFO,
                "来自 VTube Studio",
                "检测到 VTS 的配置文件（${vtsLeftovers.joinToString("、") { it.name }}）。\n\n" +
                    "这些文件 App 用不到，可以删掉；模型本身不受影响。"
            )
        }

        return Report(
            issues = issues.sortedBy { it.level.ordinal },
            mocVersion = mocVersion,
            maxTextureSide = maxSide,
            textureCount = textures.size
        )
    }

    /** moc3 第 5 个字节是版本号（3=Cubism3，4=Cubism4，5=Cubism5） */
    private fun readMocVersion(file: File): Int = runCatching {
        file.inputStream().use { ins ->
            val head = ByteArray(8)
            if (ins.read(head) < 8) return 0
            if (String(head, 0, 4, Charsets.US_ASCII) != "MOC3") return 0
            head[4].toInt() and 0xFF
        }
    }.getOrDefault(0)

    /** 只读 PNG 头拿尺寸，不把图整个解码进内存（贴图动辄 8192×8192） */
    private fun collectTextures(folder: File): List<Pair<Int, Int>> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return folder.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".png", ignoreCase = true) }
            .mapNotNull { f ->
                runCatching {
                    BitmapFactory.decodeFile(f.absolutePath, opts)
                    if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
                }.getOrNull()
            }
            .toList()
    }
}
