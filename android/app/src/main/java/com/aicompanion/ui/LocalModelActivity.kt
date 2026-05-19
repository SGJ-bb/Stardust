package com.aicompanion.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aicompanion.R
import com.aicompanion.localmodel.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalModelActivity : AppCompatActivity() {

    private lateinit var modelManager: LocalModelManager
    private lateinit var deviceProfile: DeviceProfile

    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvRamInfo: TextView
    private lateinit var tvStorageInfo: TextView
    private lateinit var tvRecommendedTier: TextView
    private lateinit var switchOcr: SwitchMaterial
    private lateinit var switchScene: SwitchMaterial
    private lateinit var switchAutoAnalyze: SwitchMaterial
    private lateinit var modelListContainer: LinearLayout
    private lateinit var tvCaptureStatus: TextView
    private lateinit var btnRequestCapture: MaterialButton
    private lateinit var btnTestCapture: MaterialButton

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val resultCode = result.resultCode
            val data = result.data!!
            lifecycleScope.launch {
                val success = withContext(Dispatchers.IO) {
                    modelManager.getScreenCaptureManager().startCapture(resultCode, data)
                }
                withContext(Dispatchers.Main) {
                    updateCaptureStatus()
                    if (success) {
                        showSnackbar("截屏权限已开启")
                    } else {
                        showSnackbar("截屏权限开启失败，请重试")
                    }
                }
            }
        } else {
            showSnackbar("截屏权限被拒绝，请在系统弹窗中点击「立即开始」")
            updateCaptureStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_model)

        modelManager = LocalModelManager(this)
        deviceProfile = modelManager.getDeviceProfile()

        initViews()
        setupDeviceInfo()
        setupSwitches()
        setupCaptureSection()
        renderModelList()
    }

    private fun initViews() {
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)
        tvRamInfo = findViewById(R.id.tvRamInfo)
        tvStorageInfo = findViewById(R.id.tvStorageInfo)
        tvRecommendedTier = findViewById(R.id.tvRecommendedTier)
        switchOcr = findViewById(R.id.switchOcr)
        switchScene = findViewById(R.id.switchScene)
        switchAutoAnalyze = findViewById(R.id.switchAutoAnalyze)
        modelListContainer = findViewById(R.id.modelListContainer)
        tvCaptureStatus = findViewById(R.id.tvCaptureStatus)
        btnRequestCapture = findViewById(R.id.btnRequestCapture)
        btnTestCapture = findViewById(R.id.btnTestCapture)
    }

    private fun setupDeviceInfo() {
        tvDeviceInfo.text = deviceProfile.deviceInfo
        tvRamInfo.text = "RAM: ${deviceProfile.availableRamMB}/${deviceProfile.totalRamMB} MB"
        tvStorageInfo.text = "存储: ${deviceProfile.availableStorageMB} MB 可用"

        val tierLabel = when (deviceProfile.recommendedTier) {
            ModelTier.LITE -> "推荐等级: 轻量版 ✅"
            ModelTier.STANDARD -> "推荐等级: 标准版 ✅"
            ModelTier.PRO -> "推荐等级: 专业版 ✅"
        }
        val tierColor = when (deviceProfile.recommendedTier) {
            ModelTier.LITE -> "#4CAF50"
            ModelTier.STANDARD -> "#2196F3"
            ModelTier.PRO -> "#FF9800"
        }
        tvRecommendedTier.text = tierLabel
        tvRecommendedTier.setTextColor(Color.parseColor(tierColor))
    }

    private fun setupSwitches() {
        switchOcr.isChecked = modelManager.isOcrEnabled
        switchScene.isChecked = modelManager.isSceneEnabled
        switchAutoAnalyze.isChecked = modelManager.isAutoAnalyze

        switchOcr.setOnCheckedChangeListener { _, isChecked ->
            modelManager.setOcrEnabled(isChecked)
        }
        switchScene.setOnCheckedChangeListener { _, isChecked ->
            modelManager.setSceneEnabled(isChecked)
        }
        switchAutoAnalyze.setOnCheckedChangeListener { _, isChecked ->
            modelManager.setAutoAnalyze(isChecked)
        }
    }

    private fun setupCaptureSection() {
        updateCaptureStatus()

        btnRequestCapture.setOnClickListener {
            requestScreenCapture()
        }

        btnTestCapture.setOnClickListener {
            testCapture()
        }
    }

    private fun requestScreenCapture() {
        try {
            val captureManager = modelManager.getScreenCaptureManager()
            val intent = captureManager.getScreenCaptureIntent()
            if (intent != null) {
                screenCaptureLauncher.launch(intent)
            } else {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? android.media.projection.MediaProjectionManager
                if (mpm != null) {
                    screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                } else {
                    showSnackbar("无法获取截屏服务，请检查系统版本")
                }
            }
        } catch (e: Exception) {
            showSnackbar("请求截屏权限失败: ${e.message}")
        }
    }

    private fun testCapture() {
        lifecycleScope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = withContext(Dispatchers.IO) {
                    val mgr = modelManager.getScreenCaptureManager()
                    if (!mgr.isCapturing) {
                        return@withContext null
                    }
                    var attempt = 0
                    var bmp: android.graphics.Bitmap? = null
                    while (bmp == null && attempt < 8) {
                        bmp = mgr.captureScreen()
                        if (bmp == null) {
                            kotlinx.coroutines.delay(500)
                            attempt++
                        }
                    }
                    if (bmp == null) {
                        com.aicompanion.util.AppLogger.e("LocalModelActivity", "testCapture: failed after $attempt attempts")
                    } else {
                        com.aicompanion.util.AppLogger.d("LocalModelActivity", "testCapture: got bitmap ${bmp.width}x${bmp.height}")
                    }
                    bmp
                }
            } catch (e: Exception) {
                showSnackbar("截图异常: ${e.message}")
                return@launch
            }

            if (bitmap != null) {
                val result = modelManager.analyzeScreen(bitmap)
                val ocr = result.ocrText
                val msg = if (ocr.isNotBlank()) {
                    "截图成功！OCR识别到 ${ocr.length} 字: ${ocr.take(100)}"
                } else {
                    val sceneInfo = if (result.sceneClassification.isNotEmpty()) {
                        " 场景: ${result.sceneClassification.first().label}"
                    } else ""
                    "截图成功但未识别到文字${sceneInfo}。可能原因：1)当前屏幕无文字 2)ML Kit中文模型未下载(需网络) 3)图片分辨率过低"
                }
                showSnackbar(msg)
                bitmap.recycle()
            } else {
                showSnackbar("截图失败，虚拟显示器可能还在初始化，请稍后再试")
            }
        }
    }

    private fun updateCaptureStatus() {
        val isCapturing = modelManager.getScreenCaptureManager().isCapturing
        tvCaptureStatus.text = if (isCapturing) "✅ 截屏权限已开启" else "❌ 未开启截屏权限"
        btnTestCapture.visibility = if (isCapturing) View.VISIBLE else View.GONE
        btnRequestCapture.text = if (isCapturing) "重新请求权限" else "请求截屏权限"
    }

    private fun renderModelList() {
        modelListContainer.removeAllViews()

        for (model in ModelRegistry.models) {
            val card = createModelCard(model)
            modelListContainer.addView(card)
        }
    }

    private fun createModelCard(model: ModelInfo): View {
        val cardLayout = layoutInflater.inflate(R.layout.item_model_card, modelListContainer, false)

        val tvTierBadge: TextView = cardLayout.findViewById(R.id.tvTierBadge)
        val tvModelName: TextView = cardLayout.findViewById(R.id.tvModelName)
        val tvModelSize: TextView = cardLayout.findViewById(R.id.tvModelSize)
        val tvModelDesc: TextView = cardLayout.findViewById(R.id.tvModelDesc)
        val tvModelCapabilities: TextView = cardLayout.findViewById(R.id.tvModelCapabilities)
        val tvModelStatus: TextView = cardLayout.findViewById(R.id.tvModelStatus)
        val btnModelAction: MaterialButton = cardLayout.findViewById(R.id.btnModelAction)
        val tvIncompatibility: TextView = cardLayout.findViewById(R.id.tvIncompatibility)
        val progressDownload: ProgressBar = cardLayout.findViewById(R.id.progressDownload)

        tvTierBadge.text = model.tier.label
        tvTierBadge.setBackgroundColor(Color.parseColor(model.tier.colorHex))
        tvModelName.text = model.name
        tvModelSize.text = formatSize(model.sizeBytes)
        tvModelDesc.text = model.description
        tvModelCapabilities.text = model.capabilities.joinToString(" · ") { " #$it" }

        val canRun = DeviceProfiler.canRunModel(deviceProfile, model)
        val isDownloaded = modelManager.isModelAvailable(model)

        if (!canRun) {
            val reason = DeviceProfiler.getIncompatibilityReason(deviceProfile, model)
            tvIncompatibility.text = "⚠️ $reason"
            tvIncompatibility.visibility = View.VISIBLE
            tvModelStatus.text = "不兼容"
            tvModelStatus.setTextColor(Color.parseColor("#FF5252"))
            btnModelAction.text = "不可用"
            btnModelAction.isEnabled = false
        } else if (model.builtIn) {
            tvModelStatus.text = "✅ 内置"
            tvModelStatus.setTextColor(Color.parseColor("#4CAF50"))
            btnModelAction.text = "已就绪"
            btnModelAction.isEnabled = false
        } else if (isDownloaded) {
            tvModelStatus.text = "✅ 已下载"
            tvModelStatus.setTextColor(Color.parseColor("#4CAF50"))
            btnModelAction.text = "删除"
            btnModelAction.setOnClickListener {
                modelManager.getModelDownloader().deleteModel(model)
                renderModelList()
            }
        } else {
            tvModelStatus.text = "未下载"
            tvModelStatus.setTextColor(Color.parseColor("#888888"))
            btnModelAction.text = "下载"
            btnModelAction.setOnClickListener {
                startModelDownload(model, progressDownload, btnModelAction, tvModelStatus)
            }
        }

        return cardLayout
    }

    private fun startModelDownload(
        model: ModelInfo,
        progressBar: ProgressBar,
        button: MaterialButton,
        statusText: TextView
    ) {
        if (model.downloadUrl.isNullOrBlank()) {
            showSnackbar("该模型暂未提供下载地址，请手动将 .tflite 文件放入 assets/models/ 目录")
            return
        }

        progressBar.visibility = View.VISIBLE
        button.text = "下载中..."
        button.isEnabled = false

        lifecycleScope.launch {
            val success = modelManager.getModelDownloader().downloadModel(model) { progress ->
                runOnUiThread {
                    progressBar.progress = progress.percentage
                    statusText.text = "下载中 ${progress.percentage}%"
                }
            }

            runOnUiThread {
                progressBar.visibility = View.GONE
                if (success) {
                    showSnackbar("${model.name} 下载完成！")
                    renderModelList()
                } else {
                    showSnackbar("${model.name} 下载失败")
                    renderModelList()
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        try {
            com.google.android.material.snackbar.Snackbar.make(
                findViewById(android.R.id.content), message,
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {}
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "内置"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1) "${"%.1f".format(mb)} MB" else "${"%.0f".format(kb)} KB"
    }

    override fun onDestroy() {
        modelManager.release()
        super.onDestroy()
    }
}
