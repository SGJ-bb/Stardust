package com.aicompanion.localmodel

import android.content.Context
import android.graphics.Bitmap
import com.aicompanion.util.AppLogger
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
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
        var ocrText = ""
        var sceneClassification: List<ClassificationResult> = emptyList()
        var uiElements: List<ClassificationResult> = emptyList()

        if (isOcrEnabled) {
            ocrText = performOcr(bitmap)
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
        val bitmap = screenCapture.captureScreen() ?: return null
        return analyzeScreen(bitmap)
    }

    suspend fun performOcr(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            chineseRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.textBlocks.joinToString("\n") { block ->
                        block.text
                    }
                    continuation.resume(text)
                }
                .addOnFailureListener { e ->
                    AppLogger.e(TAG, "OCR failed: ${e.message}")
                    continuation.resume("")
                }
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
    }
}
