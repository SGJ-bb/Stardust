package com.aicompanion.live2d

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 模型语义分析：把「死模型」的表情/动作资源翻译成统一的情绪语义。
 *
 * ## 为什么需要这一层
 *
 * Live2D 模型的表情文件名和参数名是**作者随手起的**：
 * 有的叫 `happy.exp3.json`，有的叫 `1.exp3.json`，参数甚至是 `Param37` 这种编号。
 * 之前运行时硬编码 `EMOTION_MAP = {happy: ['happy','smile','f01'...]}`，
 * 换个模型就全对不上，而且匹配失败是**静默**的——用户只看到"表情永远不变"。
 *
 * 所以改成：**导入模型时读一遍文件，自己判断每个表情是什么情绪**，结果存成
 * `emotion_map.json` 放在模型目录里，跟模型走。运行时只读这份映射，不再猜名字。
 *
 * ## 三条推断依据（按可信度从高到低）
 *
 * 1. **参数语义**：表情改的是 Cubism 标准参数（`ParamEyeLSmile`、`ParamMouthForm`…）
 *    → 直接从参数名读出情绪。最准。
 * 2. **cdi3 显示名**：参数是 `Param43` 这种编号时，查 `*.cdi3.json` 里的中文名
 *    （实测小恶魔模型：Param43="流泪"→sad、Param40="嘟嘴"→tsundere、Param55="脸黑"→angry）。
 * 3. **文件名**：`happy`/`sad`/`f01` 之类的命名兜底。
 *
 * ## 还有一层：标准参数驱动
 *
 * 很多模型压根没有情绪表情文件（本模型 7 个表情是"头发/唱歌/恶魔/外套"这类功能开关）。
 * 但 Cubism 的标准参数是行业通用的，只要模型带 `ParamEyeLSmile` / `ParamMouthForm` /
 * `ParamBrow*` 这些，就能**不依赖任何表情文件**直接驱动表情。
 * 分析结果里会列出 `standardParams`，运行时据此决定走哪条路。
 */
object ModelSemanticAnalyzer {

    /** 输出文件名（落在模型目录里，跟模型一起走） */
    const val MAP_FILE = "emotion_map.json"

    /** 情绪关键词 → 情绪名。中英日都覆盖，因为模型作者来自各处。 */
    private val EMOTION_WORDS: List<Pair<String, List<String>>> = listOf(
        "happy" to listOf("happy", "smile", "joy", "laugh", "grin", "笑", "喜", "开心", "高兴", "微笑", "嬉", "楽", "笑顔"),
        "sad" to listOf("sad", "cry", "tear", "sorrow", "流泪", "流泪", "哭", "悲", "难过", "泪", "伤心", "泣"),
        "angry" to listOf("angry", "mad", "rage", "fury", "怒", "生气", "愤", "脸黑", "黑脸", "怒り"),
        "surprised" to listOf("surprise", "shock", "amazed", "wow", "惊", "惊讶", "呆", "瞪", "震", "びっくり"),
        "tsundere" to listOf("tsundere", "shy", "blush", "embarrass", "heart", "love", "嘟嘴", "嘟", "羞", "红", "脸红", "傲娇", "照れ", "爱", "心", "喜欢"),
        "neutral" to listOf("neutral", "default", "normal", "idle", "平静", "普通", "默认", "无表情")
    )

    /** Cubism 标准参数 → 用途。用来判断"这个模型能不能纯参数驱动" */
    private val STANDARD_PARAMS = listOf(
        "eyeSmile" to listOf("ParamEyeLSmile", "ParamEyeRSmile"),
        "eyeOpen" to listOf("ParamEyeLOpen", "ParamEyeROpen"),
        "browY" to listOf("ParamBrowLY", "ParamBrowRY"),
        "browAngle" to listOf("ParamBrowLAngle", "ParamBrowRAngle"),
        "mouthForm" to listOf("ParamMouthForm"),
        "mouthOpen" to listOf("ParamMouthOpenY", "ParamParamMouthOpenY"),
        "cheek" to listOf("ParamCheekPuff", "ParamCheek")
    )

    /** 每种情绪的标准参数配方（Cubism 标准参数范围 -1~1） */
    private val EMOTION_PRESETS: Map<String, List<Triple<String, String, Float>>> = mapOf(
        // 情绪 → (参数槽位, 参数ID, 目标值)
        "happy" to listOf(
            Triple("eyeSmile", "ParamEyeLSmile", 1f),
            Triple("eyeSmile", "ParamEyeRSmile", 1f),
            Triple("mouthForm", "ParamMouthForm", 1f),
            Triple("browY", "ParamBrowLY", 0.5f),
            Triple("browY", "ParamBrowRY", 0.5f)
        ),
        "sad" to listOf(
            Triple("eyeSmile", "ParamEyeLSmile", -0.8f),
            Triple("eyeSmile", "ParamEyeRSmile", -0.8f),
            Triple("browY", "ParamBrowLY", -1f),
            Triple("browY", "ParamBrowRY", -1f),
            Triple("browAngle", "ParamBrowLAngle", 0.8f),
            Triple("browAngle", "ParamBrowRAngle", 0.8f),
            Triple("mouthForm", "ParamMouthForm", -1f)
        ),
        "angry" to listOf(
            Triple("browAngle", "ParamBrowLAngle", -1f),
            Triple("browAngle", "ParamBrowRAngle", -1f),
            Triple("browY", "ParamBrowLY", -0.6f),
            Triple("browY", "ParamBrowRY", -0.6f),
            Triple("eyeSmile", "ParamEyeLSmile", -0.5f),
            Triple("eyeSmile", "ParamEyeRSmile", -0.5f),
            Triple("mouthForm", "ParamMouthForm", -0.7f)
        ),
        "surprised" to listOf(
            Triple("eyeOpen", "ParamEyeLOpen", 1f),
            Triple("eyeOpen", "ParamEyeROpen", 1f),
            Triple("mouthOpen", "ParamMouthOpenY", 0.8f),
            Triple("browY", "ParamBrowLY", 1f),
            Triple("browY", "ParamBrowRY", 1f)
        ),
        "tsundere" to listOf(
            Triple("cheek", "ParamCheekPuff", 1f),
            Triple("eyeSmile", "ParamEyeLSmile", 0.4f),
            Triple("browAngle", "ParamBrowLAngle", 0.5f),
            Triple("browAngle", "ParamBrowRAngle", 0.5f),
            Triple("mouthForm", "ParamMouthForm", -0.3f)
        )
    )

    /**
     * 分析一个模型目录，返回要写进 [MAP_FILE] 的 JSON。
     * 目录里找不到 model3.json 时返回 null。
     */
    fun analyze(modelDir: File): JSONObject? {
        val model3 = modelDir.listFiles()
            ?.firstOrNull { it.isFile && it.name.endsWith(".model3.json") }
            ?: return null
        val root = runCatching { JSONObject(model3.readText()) }.getOrNull() ?: return null
        val refs = root.optJSONObject("FileReferences") ?: JSONObject()

        // 1) 参数 ID → 人类可读名（cdi3 里的中文/日文名，是推断编号参数的关键）
        val paramNames = readParameterNames(modelDir)

        // 2) 所有表情文件（含未被 model3 引用的 —— 很多模型就是这样，文件在但没登记）
        val expressions = collectExpressions(modelDir, model3, refs)

        // 3) 逐个表情推断情绪
        val emotions = JSONObject()
        val detail = JSONArray()
        for (expr in expressions) {
            val hits = inferEmotions(expr, paramNames)
            detail.put(JSONObject().apply {
                put("file", expr.fileName)
                put("name", expr.name)
                put("params", JSONArray(expr.params.map { "${it.first}=${it.second}" }))
                put("candidates", JSONArray(hits.map { it.first }))
            })
            // 同一情绪取第一个命中的表情（后续可以由用户在设置页覆盖）
            for ((emo, score) in hits) {
                if (!emotions.has(emo)) {
                    emotions.put(emo, JSONObject().apply {
                        put("expression", expr.name)
                        put("score", score)
                    })
                }
            }
        }

        // 4) 标准参数可用性 + 每种情绪的参数配方
        val available = mutableMapOf<String, List<String>>()
        for ((slot, ids) in STANDARD_PARAMS) {
            val present = ids.filter { paramNames.containsKey(it) || paramNames.containsKey(it.replace("ParamParam", "Param")) }
            if (present.isNotEmpty()) available[slot] = present
        }
        val presets = JSONObject()
        for ((emo, rules) in EMOTION_PRESETS) {
            val arr = JSONArray()
            for ((slot, id, value) in rules) {
                val ids = available[slot] ?: continue
                val realId = ids.firstOrNull() ?: continue
                // 双眼参数要成对处理
                if (slot == "eyeSmile" || slot == "eyeOpen") {
                    ids.forEach { arr.put(JSONArray().put(it).put(value)) }
                } else if (arr.length() == 0 || arr.toString().indexOf("\"$realId\"") < 0) {
                    arr.put(JSONArray().put(realId).put(value))
                }
            }
            if (arr.length() > 0) presets.put(emo, arr)
        }

        // 5) 动作组
        val actions = JSONObject()
        val motions = refs.optJSONObject("Motions") ?: JSONObject()
        val groups = motions.keys().asSequence().toList()
        if (groups.isNotEmpty()) {
            for ((action, words) in ACTION_WORDS) {
                val hit = groups.firstOrNull { g -> words.any { g.lowercase().contains(it) } }
                if (hit != null) actions.put(action, hit)
            }
        }

        return JSONObject().apply {
            put("version", 1)
            put("model", model3.name)
            put("analyzedAt", System.currentTimeMillis())
            put("standardParams", JSONObject(available.mapValues { JSONArray(it.value) }))
            put("paramPresets", presets)
            put("emotions", emotions)
            put("actions", actions)
            put("motionGroups", JSONArray(groups))
            put("detail", detail)
            put("notes", JSONArray(buildNotes(emotions, presets, groups, expressions.size)))
        }
    }

    private val ACTION_WORDS = listOf(
        "idle" to listOf("idle", "stand", "wait"),
        "tap_head" to listOf("taphead", "tap_head", "head"),
        "tap_face" to listOf("tapface", "tap_face", "face"),
        "tap_body" to listOf("tapbody", "tap_body", "body"),
        "stretch" to listOf("stretch", "body"),
        "yawn" to listOf("yawn"),
        "tail_flick" to listOf("tail"),
        "ear_twitch" to listOf("ear")
    )

    private fun buildNotes(emotions: JSONObject, presets: JSONObject, groups: List<String>, exprCount: Int): List<String> {
        val notes = mutableListOf<String>()
        if (exprCount == 0) notes.add("没有表情文件，只能用参数驱动")
        if (emotions.length() == 0) notes.add("表情文件里没有能识别的情绪（可能是换装/特效开关）")
        if (presets.length() == 0) notes.add("缺少 Cubism 标准面部参数，参数驱动不可用")
        if (groups.isEmpty()) notes.add("model3.json 里没有登记任何动作组")
        if (emotions.length() > 0 && presets.length() > 0) {
            notes.add("表情文件和标准参数都有：优先用表情（作者调过），没有对应表情时用参数")
        }
        return notes
    }

    // ==================== 读取 ====================

    private data class Expr(val name: String, val fileName: String, val params: List<Pair<String, Float>>)

    /** 参数 ID → 显示名。优先 cdi3，其次 model3 的 Groups. */
    private fun readParameterNames(modelDir: File): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val cdi = modelDir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".cdi3.json") }
        if (cdi != null) {
            runCatching {
                val arr = JSONObject(cdi.readText()).optJSONArray("Parameters") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val id = p.optString("Id")
                    val name = p.optString("Name")
                    if (id.isNotBlank() && name.isNotBlank()) out[id] = name
                }
            }
        }
        // cdi3 没有的参数，用 ID 本身兜底（标准参数名本身就有语义）
        return out
    }

    private fun collectExpressions(modelDir: File, model3: File, refs: JSONObject): List<Expr> {
        val list = mutableListOf<Expr>()
        val referenced = mutableSetOf<String>()

        val arr = refs.optJSONArray("Expressions") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val file = o.optString("File")
            val name = o.optString("Name").ifBlank { file.substringAfterLast('/').substringBefore(".exp") }
            if (file.isBlank()) continue
            referenced.add(file.substringAfterLast('/'))
            val f = File(modelDir, file)
            if (!f.isFile) continue
            list.add(Expr(name, f.name, readParams(f)))
        }

        // 目录里存在但没登记的表情文件（本模型就是这种情况，7 个文件全没登记）
        modelDir.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".exp3.json") || it.name.endsWith(".exp.json")) }
            .filter { it.name !in referenced }
            .forEach { f ->
                val name = f.name.removeSuffix(".exp3.json").removeSuffix(".exp.json")
                list.add(Expr(name, f.name, readParams(f)))
            }
        return list
    }

    private fun readParams(f: File): List<Pair<String, Float>> {
        return runCatching {
            val arr = JSONObject(f.readText()).optJSONArray("Parameters") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val p = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = p.optString("Id")
                if (id.isBlank()) null else id to p.optDouble("Value", 0.0).toFloat()
            }
        }.getOrDefault(emptyList())
    }

    // ==================== 推断 ====================

    /** 给一个表情打分，返回按分数排序的 (情绪, 分) 列表，只保留最高分那一档 */
    private fun inferEmotions(expr: Expr, paramNames: Map<String, String>): List<Pair<String, Int>> {
        val scores = mutableMapOf<String, Int>()
        // 文件名本身也是线索（happy.exp3.json 之类）
        addScore(scores, expr.name, 3)
        addScore(scores, expr.fileName, 2)
        // 参数名 / cdi3 显示名
        for ((id, _) in expr.params) {
            addScore(scores, id, 2)
            paramNames[id]?.let { addScore(scores, it, 4) }   // 中文显示名最可信
        }
        if (scores.isEmpty()) {
            // 什么都没匹配上：值全为 0 视为 neutral
            if (expr.params.isNotEmpty() && expr.params.all { it.second == 0f }) {
                scores["neutral"] = 1
            }
        }
        val best = scores.values.maxOrNull() ?: return emptyList()
        return scores.filterValues { it == best }.map { it.key to it.value }
            .sortedByDescending { it.second }
    }

    private fun addScore(scores: MutableMap<String, Int>, raw: String, weight: Int) {
        if (raw.isBlank()) return
        val s = raw.lowercase()
        for ((emo, words) in EMOTION_WORDS) {
            if (words.any { s.contains(it.lowercase()) }) {
                scores[emo] = (scores[emo] ?: 0) + weight
            }
        }
    }
}
