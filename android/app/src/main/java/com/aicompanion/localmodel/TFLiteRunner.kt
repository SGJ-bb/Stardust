package com.aicompanion.localmodel

import android.content.Context
import android.graphics.Bitmap
import com.aicompanion.util.AppLogger
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ClassificationResult(
    val label: String,
    val confidence: Float
)

class TFLiteRunner {

    companion object {
        private const val TAG = "TFLiteRunner"
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var modelInfo: ModelInfo? = null

    val isLoaded: Boolean get() = interpreter != null
    val loadedModelId: String? get() = modelInfo?.id

    fun loadModel(context: Context, modelInfo: ModelInfo): Boolean {
        close()
        this.modelInfo = modelInfo

        try {
            val buffer = if (modelInfo.builtIn) {
                loadModelFromAssets(context, modelInfo.fileName)
            } else {
                loadModelFromFile(context, modelInfo.fileName)
            }

            if (buffer == null) {
                AppLogger.e(TAG, "loadModel: model file not found: ${modelInfo.fileName}")
                return false
            }

            val options = Interpreter.Options()

            if (modelInfo.gpuRequired) {
                try {
                    gpuDelegate = GpuDelegate()
                    options.addDelegate(gpuDelegate!!)
                    AppLogger.d(TAG, "loadModel: GPU delegate added")
                } catch (e: Exception) {
                    AppLogger.d(TAG, "loadModel: GPU not available, using CPU: ${e.message}")
                }
            }

            options.setNumThreads(4)

            interpreter = Interpreter(buffer, options)
            AppLogger.d(TAG, "loadModel: loaded ${modelInfo.id} successfully")
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadModel failed: ${e.message}")
            close()
            return false
        }
    }

    fun classify(bitmap: Bitmap): List<ClassificationResult> {
        val interp = interpreter ?: return emptyList()
        val info = modelInfo ?: return emptyList()

        if (info.labels.isEmpty()) return emptyList()

        try {
            val resized = Bitmap.createScaledBitmap(bitmap, info.inputSize, info.inputSize, false)
            val input = convertBitmapToByteBuffer(resized, info.inputSize)

            val output = Array(1) { FloatArray(info.labels.size) }
            interp.run(input, output)

            val results = mutableListOf<ClassificationResult>()
            for (i in info.labels.indices) {
                if (output[0][i] > 0.01f) {
                    results.add(ClassificationResult(info.labels[i], output[0][i]))
                }
            }
            return results.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            AppLogger.e(TAG, "classify failed: ${e.message}")
            return emptyList()
        }
    }

    fun classifyTop(bitmap: Bitmap, topK: Int = 3): List<ClassificationResult> {
        return classify(bitmap).take(topK)
    }

    fun close() {
        try {
            interpreter?.close()
            gpuDelegate?.close()
        } catch (_: Exception) {}
        interpreter = null
        gpuDelegate = null
        modelInfo = null
    }

    private fun loadModelFromAssets(context: Context, fileName: String): MappedByteBuffer? {
        return try {
            val assetFd = context.assets.openFd("models/$fileName")
            val inputStream = FileInputStream(assetFd.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFd.startOffset
            val declaredLength = assetFd.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadModelFromAssets failed: ${e.message}")
            null
        }
    }

    private fun loadModelFromFile(context: Context, fileName: String): MappedByteBuffer? {
        return try {
            val file = File(context.getDir("models", Context.MODE_PRIVATE), fileName)
            if (!file.exists()) return null
            val inputStream = FileInputStream(file)
            val fileChannel = inputStream.channel
            fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadModelFromFile failed: ${e.message}")
            null
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap, inputSize: Int): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val `val` = intValues[pixel++]
                byteBuffer.putFloat(((`val` shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((`val` shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((`val` and 0xFF) / 255.0f)
            }
        }
        return byteBuffer
    }
}
