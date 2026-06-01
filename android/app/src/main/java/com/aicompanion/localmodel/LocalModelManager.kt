package com.aicompanion.localmodel

import android.content.Context
import android.graphics.Bitmap
import com.aicompanion.util.AppLogger
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class ScreenAnalysisResult(
    val ocrText: String = "",
    val sceneClassification: List<ClassificationResult> = emptyList(),
    val uiElements: List<ClassificationResult> = emptyList(),
    val combinedDescription: String = ""
) {
    fun toContextBlock(): String {
        val sb = StringBuilder()
        if (ocrText.isNotBlank()) {
            sb.appendLine("[屏幕OCR识别]")
            sb.appendLine(ocrText.take(500))
        }
        if (sceneClassification.isNotEmpty()) {
            sb.appendLine("[场景分类]")
            for (result in sceneClassification.take(3)) {
                sb.appendLine("- ${result.label} (${(result.confidence * 100).toInt()}%)")
            }
        }
        if (uiElements.isNotEmpty()) {
            sb.appendLine("[UI元素]")
            for (result in uiElements.take(5)) {
                sb.appendLine("- ${result.label} (${(result.confidence * 100).toInt()}%)")
            }
        }
        return sb.toString().trimEnd()
    }
}

class LocalModelManager(private val context: Context) {

    companion object {
        private const val TAG = "LocalModelManager"
        private const val PREFS_NAME = "local_model_prefs"
        private const val KEY_OCR_ENABLED = "ocr_enabled"
        private const val KEY_SCENE_ENABLED = "scene_enabled"
        private const val KEY_AUTO_ANALYZE = "auto_analyze"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val deviceProfiler = DeviceProfiler
    private val tfliteRunner = TFLiteRunner()
    private val modelDownloader = ModelDownloader(context)
    private val screenCapture = ScreenCaptureManager(context)

    private val chineseRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val latinRecognizer by lazy {
        TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.Builder().build())
    }

    private var ocrModelChecked = false
    private var ocrModelAvailable = false
    private var useChineseOcr = true

    fun isOcrModelAvailable(): Boolean = ocrModelAvailable

    private var loadedModelId: String? = null
    private var deviceProfile: DeviceProfile? = null

    val isOcrEnabled: Boolean get() = prefs.getBoolean(KEY_OCR_ENABLED, true)
    val isSceneEnabled: Boolean get() = prefs.getBoolean(KEY_SCENE_ENABLED, true)
    val isAutoAnalyze: Boolean get() = prefs.getBoolean(KEY_AUTO_ANALYZE, false)

    fun setOcrEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_OCR_ENABLED, enabled).apply()
    fun setSceneEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SCENE_ENABLED, enabled).apply()
    fun setAutoAnalyze(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_ANALYZE, enabled).apply()

    fun getDeviceProfile(): DeviceProfile {
        if (deviceProfile == null) {
            deviceProfile = DeviceProfiler.profile(context)
        }
        return deviceProfile!!
    }

    fun isModelAvailable(modelInfo: ModelInfo): Boolean {
        if (modelInfo.builtIn) return true
        return modelDownloader.isModelDownloaded(modelInfo)
    }

    fun isModelLoaded(modelId: String): Boolean = loadedModelId == modelId && tfliteRunner.isLoaded

    fun loadTFLiteModel(modelInfo: ModelInfo): Boolean {
        if (!isModelAvailable(modelInfo)) return false
        if (modelInfo.id == "ocr_lite") return true
        val success = tfliteRunner.loadModel(context, modelInfo)
        if (success) {
            loadedModelId = modelInfo.id
        }
        return success
    }

    fun unloadModel() {
        tfliteRunner.close()
        loadedModelId = null
    }

    suspend fun analyzeScreen(bitmap: Bitmap): ScreenAnalysisResult = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            AppLogger.e(TAG, "analyzeScreen: invalid bitmap (recycled=${bitmap.isRecycled}, size=${bitmap.width}x${bitmap.height})")
            return@withContext ScreenAnalysisResult()
        }

        AppLogger.d(TAG, "analyzeScreen: bitmap=${bitmap.width}x${bitmap.height}, ocrEnabled=$isOcrEnabled, sceneEnabled=$isSceneEnabled")

        var ocrText = ""
        var sceneClassification: List<ClassificationResult> = emptyList()
        var uiElements: List<ClassificationResult> = emptyList()

        if (isOcrEnabled) {
            try {
                ocrText = performOcr(bitmap)
                AppLogger.d(TAG, "analyzeScreen: OCR result length=${ocrText.length}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "analyzeScreen: OCR exception: ${e.message}")
            }
        } else {
            AppLogger.d(TAG, "analyzeScreen: OCR is disabled")
        }

        if (isSceneEnabled && tfliteRunner.isLoaded) {
            sceneClassification = tfliteRunner.classifyTop(bitmap, 3)
        }

        val combined = buildString {
            if (ocrText.isNotBlank()) append("屏幕文字: ${ocrText.take(200)}")
            if (sceneClassification.isNotEmpty()) {
                if (isNotBlank()) append(" | ")
                append("场景: ${sceneClassification.first().label}")
            }
        }

        ScreenAnalysisResult(
            ocrText = ocrText,
            sceneClassification = sceneClassification,
            uiElements = uiElements,
            combinedDescription = combined
        )
    }

    suspend fun analyzeCurrentScreen(): ScreenAnalysisResult? {
        if (!screenCapture.isCapturing) {
            AppLogger.w(TAG, "analyzeCurrentScreen: screen capture not started")
            return null
        }
        val bitmap = screenCapture.captureScreen()
        if (bitmap == null) {
            AppLogger.w(TAG, "analyzeCurrentScreen: captureScreen returned null")
            return null
        }
        AppLogger.d(TAG, "analyzeCurrentScreen: captured bitmap ${bitmap.width}x${bitmap.height}")
        val sm = com.aicompanion.settings.SettingsManager(context)
        return if (sm.useChatModelForVision && com.aicompanion.settings.ProviderProfile.supportsVision(sm.apiProvider)) {
            analyzeScreenWithVisionModel(bitmap)
        } else {
            analyzeScreen(bitmap)
        }
    }

    suspend fun analyzeScreenWithVisionModel(bitmap: Bitmap): ScreenAnalysisResult = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return@withContext ScreenAnalysisResult()
        }
        try {
            val sm = com.aicompanion.settings.SettingsManager(context)
            if (!com.aicompanion.settings.ProviderProfile.supportsVision(sm.apiProvider)) {
                return@withContext analyzeScreen(bitmap)
            }
            if (!sm.useChatModelForVision) {
                return@withContext analyzeScreen(bitmap)
            }
            val maxDim = 1280
            val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
            val scaledBitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            val stream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, stream)
            if (scaledBitmap !== bitmap) scaledBitmap.recycle()
            val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$base64"

            val client = com.aicompanion.network.ApiClient(
                sm.chatApiUrl, sm.chatApiKey, sm.chatModel,
                0.3f, 0.9f, 0f, 0f, 300, sm.apiProvider
            )
            val response = client.sendChat(
                userId = "screen_vision",
                message = "请详细描述这个屏幕截图的内容。包括：1)屏幕上有哪些文字 2)这是什么应用 3)有哪些可点击的按钮或元素。用简洁的列表格式回答。",
                personaName = "assistant",
                personaPrompt = "你是一个屏幕内容分析助手，请准确描述屏幕截图中的内容。",
                emotion = "NEUTRAL",
                action = "IDLE",
                memories = emptyList(),
                appCategory = "unknown",
                imageUrls = listOf(dataUrl)
            )
            val visionText = response?.text?.trim() ?: ""
            if (visionText.isBlank()) {
                return@withContext analyzeScreen(bitmap)
            }
            ScreenAnalysisResult(
                ocrText = visionText,
                sceneClassification = emptyList(),
                uiElements = emptyList(),
                combinedDescription = visionText.take(500)
            )
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "analyzeScreenWithVisionModel failed: ${e.message}")
            analyzeScreen(bitmap)
        } catch (e: OutOfMemoryError) {
            com.aicompanion.util.AppLogger.e(TAG, "analyzeScreenWithVisionModel OOM: ${e.message}")
            analyzeScreen(bitmap)
        }
    }

    suspend fun performOcr(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled) {
            AppLogger.e(TAG, "performOcr: bitmap is recycled")
            return@withContext ""
        }
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            AppLogger.e(TAG, "performOcr: invalid bitmap size ${bitmap.width}x${bitmap.height}")
            return@withContext ""
        }

        var lastError: String? = null
        for (attempt in 1..2) {
            try {
                val result = performOcrInternal(bitmap)
                if (result.isNotBlank()) return@withContext result
                if (attempt == 1) {
                    AppLogger.d(TAG, "performOcr: attempt $attempt returned empty, retrying...")
                    kotlinx.coroutines.delay(300)
                }
            } catch (e: Exception) {
                lastError = e.message
                AppLogger.e(TAG, "performOcr attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}")
                if (attempt < 2) {
                    kotlinx.coroutines.delay(500)
                }
            }
        }
        if (lastError != null) {
            AppLogger.e(TAG, "performOcr all attempts failed, last error: $lastError")
        } else {
            AppLogger.w(TAG, "performOcr: no text detected in image after 2 attempts")
        }
        ""
    }

    private suspend fun performOcrInternal(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = if (useChineseOcr) chineseRecognizer else latinRecognizer
            val task = recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (continuation.isActive) {
                        val blocks = visionText.textBlocks
                        if (blocks.isEmpty()) {
                            AppLogger.d(TAG, "OCR: no text blocks found (chinese=$useChineseOcr)")
                            continuation.resume("")
                        } else {
                            val text = blocks.joinToString("\n") { block -> block.text }
                            AppLogger.d(TAG, "OCR: found ${blocks.size} text blocks, ${text.length} chars (chinese=$useChineseOcr)")
                            continuation.resume(text)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) {
                        val errorMsg = when {
                            e.message?.contains("ML Kit") == true -> "ML Kit模型未就绪，请确保网络连接后重试"
                            e.message?.contains("download") == true -> "OCR模型下载失败，请检查网络连接"
                            e.message?.contains("buffer") == true -> "图像数据处理异常"
                            else -> "OCR识别失败: ${e.javaClass.simpleName}: ${e.message}"
                        }
                        AppLogger.e(TAG, "OCR failure (chinese=$useChineseOcr): $errorMsg")
                        if (useChineseOcr && (e.message?.contains("download") == true || e.message?.contains("ML Kit") == true || e.message?.contains("model") == true)) {
                            useChineseOcr = false
                            AppLogger.w(TAG, "Chinese OCR model unavailable, falling back to Latin OCR")
                        }
                        continuation.resume("")
                    }
                }
            continuation.invokeOnCancellation { }
        }
    }

    fun classifyScene(bitmap: Bitmap): List<ClassificationResult> {
        if (!tfliteRunner.isLoaded) return emptyList()
        return tfliteRunner.classifyTop(bitmap, 5)
    }

    fun getScreenCaptureManager(): ScreenCaptureManager = screenCapture

    fun getModelDownloader(): ModelDownloader = modelDownloader

    fun getDownloadedModels(): List<ModelInfo> {
        return ModelRegistry.models.filter { modelDownloader.isModelDownloaded(it) }
    }

    fun getAvailableModels(): List<ModelInfo> {
        val profile = getDeviceProfile()
        return ModelRegistry.models.filter { DeviceProfiler.canRunModel(profile, it) }
    }

    fun release() {
        tfliteRunner.close()
        screenCapture.stopCapture()
        loadedModelId = null
        try { chineseRecognizer.close() } catch (_: Exception) {}
        try { latinRecognizer.close() } catch (_: Exception) {}
    }
}
