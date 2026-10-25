/**
 * 视觉定位（第三级兜底）：截图发给对话模型，让它直接**按坐标**指出可点目标。
 *
 * 为什么需要它：OCR 只能认出"字"，而微信这类界面里大量关键按钮是**纯图标**
 * （右上角「+」、语音、表情、相册…），OCR 完全看不见。此时唯一可行的办法是
 * 让视觉模型看图给坐标。
 *
 * 提准精度的手段：
 * 1. 图上叠**坐标网格**（[OcrView.withGrid]）——模型猜坐标常偏几十像素，网格能显著纠正；
 * 2. 明确告诉模型图片尺寸与坐标口径（左上角原点、单位像素）；
 * 3. 要求只返回 JSON，便于稳定解析；解析失败就当没结果，不瞎猜。
 */
package com.aicompanion.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import java.io.ByteArrayOutputStream

object VisionGrounding {

    private const val TAG = "VisionGrounding"

    data class Target(val text: String, val x: Int, val y: Int)

    fun ground(context: Context, src: Bitmap, maxDim: Int = 1280): List<Target> {
        val sm = runCatching { SettingsManager(context) }.getOrNull() ?: return emptyList()
        val url = runCatching { sm.chatApiUrl }.getOrDefault("")
        val model = runCatching { sm.chatModel }.getOrDefault("")
        if (url.isBlank() || model.isBlank()) {
            AppLogger.w(TAG, "未配置对话模型，跳过视觉定位")
            return emptyList()
        }

        var small: Bitmap? = null
        var grid: Bitmap? = null
        val payload: String
        val scale: Float
        try {
            scale = minOf(
                maxDim.toFloat() / src.width,
                maxDim.toFloat() / src.height,
                1f
            )
            small = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    src, (src.width * scale).toInt(), (src.height * scale).toInt(), true
                )
            } else {
                src
            }
            grid = OcrView.withGrid(small, step = 120)
            val baos = ByteArrayOutputStream()
            grid.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            payload = "data:image/jpeg;base64," +
                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLogger.e(TAG, "准备图像失败: ${e.message}")
            return emptyList()
        } finally {
            runCatching { if (grid != null && grid !== src) grid.recycle() }
            runCatching { if (small != null && small !== src) small.recycle() }
        }

        val w = (src.width * scale).toInt()
        val h = (src.height * scale).toInt()
        val prompt = """
            这是一张手机截图（尺寸 ${w}x$h 像素，图上已叠加坐标网格，左上角为原点）。
            请找出所有**用户可能想点击的目标**：按钮、列表项、输入框、图标、标签页。
            只输出 JSON 数组，不要任何解释。每个元素格式：
            {"text":"目标名称","x":中心点X,"y":中心点Y}
            坐标必须是这张图上的**像素值**。纯图标没有文字的，用你能想到的简短名称描述。
        """.trimIndent()

        val text = try {
            val client = com.aicompanion.network.ApiClient(
                sm.chatApiUrl, sm.chatApiKey, sm.chatModel,
                0.2f, 0.9f, 0f, 0f, 120, sm.apiProvider
            )
            client.sendChat(
                userId = "vision_grounding",
                message = prompt,
                personaName = "assistant",
                personaPrompt = "你是界面坐标标注助手，只输出 JSON 数组。",
                emotion = "NEUTRAL",
                action = "IDLE",
                memories = emptyList(),
                appCategory = "unknown",
                imageUrls = listOf(payload)
            )?.text?.trim().orEmpty()
        } catch (e: Exception) {
            AppLogger.e(TAG, "视觉定位请求失败: ${e.message}")
            ""
        }
        if (text.isEmpty()) return emptyList()

        return parseTargets(text, scale, src.width, src.height)
    }

    /**
     * 解析模型返回的 JSON 数组，并把「缩放图坐标」还原成「屏幕坐标」。
     * 抽成 internal 是为了能在 JVM 上单测（视觉链路端到端依赖真实模型，不便每次都跑）。
     */
    internal fun parseTargets(
        text: String,
        scale: Float,
        maxW: Int,
        maxH: Int,
    ): List<Target> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) {
            AppLogger.w(TAG, "模型未返回 JSON 数组：${text.take(80)}")
            return emptyList()
        }
        val out = ArrayList<Target>()
        runCatching {
            val arr = JSONArray(text.substring(start, end + 1))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val label = (o.optString("text").ifBlank { o.optString("label") }).trim()
                val x = o.optInt("x", -1)
                val y = o.optInt("y", -1)
                if (x < 0 || y < 0) continue
                // 模型给的是缩放后图片坐标，还原到屏幕坐标
                val sx = (x / scale).toInt().coerceIn(0, maxW - 1)
                val sy = (y / scale).toInt().coerceIn(0, maxH - 1)
                out.add(Target(label.ifBlank { "图标" }, sx, sy))
            }
        }.onFailure { AppLogger.w(TAG, "解析 JSON 失败: ${it.message}") }
        AppLogger.i(TAG, "视觉定位得到 ${out.size} 个目标")
        return out
    }

    fun boundsOf(t: Target, half: Int = 24): Rect =
        Rect(t.x - half, t.y - half, t.x + half, t.y + half)
}
