/**
 * OCR 兜底视图：当某个 App **不上报无障碍树**时（实测微信 8.0.76 的 LauncherUI 只暴露
 * 1 个空节点，原生 View 却是完整的），唯一可靠的办法是「截图 → 识别文字 → 按坐标点击」。
 *
 * 为什么这条路能成：手势注入（dispatchGesture / input tap）由**系统**执行，
 * 应用即使屏蔽无障碍树也拦不住；只有「读它的界面」需要换手段。
 *
 * 输出带坐标，可以直接喂给模型，让它按 (x,y) 点。
 */
package com.aicompanion.screen

import android.graphics.Bitmap
import android.graphics.Rect
import com.aicompanion.util.AppLogger
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.TimeUnit

object OcrView {

    private const val TAG = "OcrView"

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    data class Item(val text: String, val bounds: Rect) {
        val cx: Int get() = bounds.centerX()
        val cy: Int get() = bounds.centerY()
    }

    data class Result(
        val items: List<Item>,
        val width: Int,
        val height: Int,
        /** 整张图几乎全黑：锁屏 / FLAG_SECURE 页面截不到内容 */
        val blank: Boolean = false,
    ) {
        /** 给模型看的紧凑文本：编号 + 文字 + 可点坐标 */
        fun toUiText(): String {
            if (blank) {
                return "(截图是黑屏：锁屏状态或该页面设置了 FLAG_SECURE，无法读取内容)"
            }
            if (items.isEmpty()) {
                return "(OCR 未识别到任何文字，可能是纯图片/视频页面)"
            }
            val sb = StringBuilder()
            sb.append("屏幕 ").append(width).append('x').append(height)
            sb.append("（该页面不上报无障碍树，以下为 OCR 结果，坐标可直接点击）\n")
            items.forEachIndexed { i, it ->
                sb.append(i + 1).append(". ").append(it.text.replace('\n', ' '))
                    .append("  @(").append(it.cx).append(',').append(it.cy).append(")\n")
            }
            sb.append("提示：点击用 tap(x,y)；纯图标按钮 OCR 认不出，请结合截图判断。")
            return sb.toString().trim()
        }

        /** 按文字找条目：完全相等优先，其次包含；多个候选取最短（最精确） */
        fun find(target: String): Item? {
            val t = target.trim()
            if (t.isEmpty()) return null
            val exact = items.filter { it.text.trim() == t }
            if (exact.isNotEmpty()) return exact.minByOrNull { it.text.length }
            val partial = items.filter { it.text.contains(t) }
            return partial.minByOrNull { it.text.length }
        }
    }

    /**
     * 识别一张截图。ML Kit 是异步 Task，这里用 Tasks.await 转成同步调用 ——
     * 调用方本来就在后台线程（无障碍服务的 executor / 探针线程），不会堵主线程。
     */
    /** 采样判断是否几乎全黑（每 40 像素取一点，够快也够准） */
    private fun isNearlyBlack(bitmap: Bitmap): Boolean {
        return try {
            var sum = 0L
            var n = 0
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val c = bitmap.getPixel(x, y)
                    sum += ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
                    n++
                    x += 40
                }
                y += 40
            }
            n > 0 && sum / n < 8
        } catch (_: Exception) {
            false
        }
    }

    /** 过滤状态栏噪声（时间 / 电量这类纯数字），避免模型把"1:02"当成可点目标 */
    private fun isNoise(item: Item): Boolean {
        if (item.bounds.top > 70) return false
        val t = item.text.trim()
        if (t.matches(Regex("^\\d{1,2}:\\d{2}$"))) return true
        if (t.matches(Regex("^\\d{1,3}%?$"))) return true
        return false
    }

    /**
     * 给视觉模型用的"网格图"：每 [step] 像素画一条线并标坐标。
     * 模型看图猜坐标常常偏几十像素，叠一层网格能显著提高 grounding 精度。
     */
    fun withGrid(src: Bitmap, step: Int = 120): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(out)
        val line = android.graphics.Paint().apply {
            color = 0x6633CCFF
            strokeWidth = 2f
        }
        val text = android.graphics.Paint().apply {
            color = 0xCCFF6600.toInt()
            textSize = 22f
            isAntiAlias = true
        }
        var x = 0
        while (x < out.width) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), out.height.toFloat(), line)
            canvas.drawText(x.toString(), x + 3f, 22f, text)
            x += step
        }
        var y = 0
        while (y < out.height) {
            canvas.drawLine(0f, y.toFloat(), out.width.toFloat(), y.toFloat(), line)
            canvas.drawText(y.toString(), 3f, (y + 20).toFloat(), text)
            y += step
        }
        return out
    }

    fun recognize(bitmap: Bitmap, timeoutMs: Long = 8000): Result {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return Result(emptyList(), 0, 0)
        }
        if (isNearlyBlack(bitmap)) {
            AppLogger.w(TAG, "截图为黑屏（锁屏/FLAG_SECURE？）${bitmap.width}x${bitmap.height}")
            return Result(emptyList(), bitmap.width, bitmap.height, blank = true)
        }
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val vision = Tasks.await(recognizer.process(image), timeoutMs, TimeUnit.MILLISECONDS)
            val items = ArrayList<Item>()
            for (block in vision.textBlocks) {
                for (line in block.lines) {
                    val txt = line.text?.trim().orEmpty()
                    val box = line.boundingBox ?: continue
                    if (txt.isEmpty()) continue
                    val item = Item(txt, Rect(box))
                    if (!isNoise(item)) items.add(item)
                }
            }
            AppLogger.d(TAG, "OCR 识别 ${items.size} 行（${bitmap.width}x${bitmap.height}）")
            Result(items, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            AppLogger.e(TAG, "OCR 失败: ${e.javaClass.simpleName}: ${e.message}")
            Result(emptyList(), bitmap.width, bitmap.height)
        }
    }
}
