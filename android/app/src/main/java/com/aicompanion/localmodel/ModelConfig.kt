package com.aicompanion.localmodel

enum class ModelTier(val label: String, val colorHex: String) {
    LITE("轻量", "#4CAF50"),
    STANDARD("标准", "#2196F3"),
    PRO("专业", "#FF9800")
}

data class ModelInfo(
    val id: String,
    val name: String,
    val tier: ModelTier,
    val version: String,
    val sizeBytes: Long,
    val description: String,
    val capabilities: List<String>,
    val minRamMB: Int,
    val gpuRequired: Boolean,
    val builtIn: Boolean,
    val downloadUrl: String?,
    val fileName: String,
    val inputSize: Int,
    val labels: List<String>,
    val md5: String? = null
)

object ModelRegistry {

    val models = listOf(
        ModelInfo(
            id = "ocr_lite",
            name = "文字识别 (OCR)",
            tier = ModelTier.LITE,
            version = "1.0",
            sizeBytes = 0,
            description = "基于ML Kit的屏幕文字识别，可读取图片和游戏界面中的文字",
            capabilities = listOf("OCR文字识别"),
            minRamMB = 2048,
            gpuRequired = false,
            builtIn = true,
            downloadUrl = null,
            fileName = "",
            inputSize = 0,
            labels = emptyList()
        ),
        ModelInfo(
            id = "scene_lite",
            name = "场景分类",
            tier = ModelTier.LITE,
            version = "1.0",
            sizeBytes = 4_194_304,
            description = "轻量级屏幕场景分类，识别聊天/游戏/浏览器等场景类型",
            capabilities = listOf("场景分类"),
            minRamMB = 2048,
            gpuRequired = false,
            builtIn = true,
            downloadUrl = null,
            fileName = "scene_lite.tflite",
            inputSize = 224,
            labels = listOf("聊天", "游戏", "浏览器", "视频", "音乐", "社交", "设置", "桌面", "相机", "地图", "购物", "阅读", "工作", "其他")
        ),
        ModelInfo(
            id = "ui_standard",
            name = "UI元素检测",
            tier = ModelTier.STANDARD,
            version = "1.0",
            sizeBytes = 31_457_280,
            description = "检测屏幕上的按钮、输入框、图标等UI元素位置和类型",
            capabilities = listOf("UI元素检测", "场景分类", "布局分析"),
            minRamMB = 4096,
            gpuRequired = false,
            builtIn = false,
            downloadUrl = "",
            fileName = "ui_standard.tflite",
            inputSize = 300,
            labels = listOf("按钮", "输入框", "图片", "文字", "图标", "开关", "滑块", "复选框", "下拉框", "卡片", "列表", "标签页")
        ),
        ModelInfo(
            id = "screen_pro",
            name = "屏幕理解",
            tier = ModelTier.PRO,
            version = "1.0",
            sizeBytes = 125_829_120,
            description = "全面的屏幕内容理解，包含OCR、UI检测、场景分析和内容描述",
            capabilities = listOf("OCR文字识别", "UI元素检测", "场景分类", "布局分析", "内容描述"),
            minRamMB = 6144,
            gpuRequired = true,
            builtIn = false,
            downloadUrl = "",
            fileName = "screen_pro.tflite",
            inputSize = 512,
            labels = emptyList()
        )
    )

    fun getById(id: String): ModelInfo? = models.find { it.id == id }
    fun getByTier(tier: ModelTier): List<ModelInfo> = models.filter { it.tier == tier }
    fun getBuiltIn(): List<ModelInfo> = models.filter { it.builtIn }
    fun getDownloadable(): List<ModelInfo> = models.filter { !it.builtIn }
}
