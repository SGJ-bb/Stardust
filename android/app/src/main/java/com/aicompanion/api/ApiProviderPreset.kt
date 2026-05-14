/** API预设配置: 预配置各厂商AI API地址/模型, 支持一键切换提供商 */
package com.aicompanion.api

object ApiProviderPreset {
    data class ApiProvider(
        val name: String,
        val icon: String,
        val chatUrl: String,
        val modelDefault: String,
        val screenApiUrl: String = "",
        val screenModel: String = "",
        val ttsUrl: String = "",
        val ttsModel: String = "tts-1",
        val asrUrl: String = "",
    )

    val providers = listOf(
        ApiProvider(
            name = "OpenAI (GPT)",
            icon = "🟢",
            chatUrl = "https://api.openai.com/v1/chat/completions",
            modelDefault = "gpt-4o-mini",
            screenApiUrl = "https://api.openai.com/v1/chat/completions",
            screenModel = "gpt-4o",
            ttsUrl = "https://api.openai.com/v1/audio/speech",
            ttsModel = "tts-1",
            asrUrl = "https://api.openai.com/v1/audio/transcriptions"
        ),
        ApiProvider(
            name = "DeepSeek",
            icon = "🔵",
            chatUrl = "https://api.deepseek.com/v1/chat/completions",
            modelDefault = "deepseek-chat",
        ),
        ApiProvider(
            name = "硅基流动 (SiliconFlow)",
            icon = "🟣",
            chatUrl = "https://api.siliconflow.cn/v1/chat/completions",
            modelDefault = "THUDM/glm-4-9b-chat",
        ),
        ApiProvider(
            name = "通义千问 (Qwen)",
            icon = "🟠",
            chatUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            modelDefault = "qwen-turbo",
        ),
        ApiProvider(
            name = "智谱AI (Zhipu)",
            icon = "🔴",
            chatUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            modelDefault = "glm-4-flash",
        ),
        ApiProvider(
            name = "月之暗面 (Moonshot)",
            icon = "🌙",
            chatUrl = "https://api.moonshot.cn/v1/chat/completions",
            modelDefault = "moonshot-v1-8k",
        ),
        ApiProvider(
            name = "自定义",
            icon = "⚙️",
            chatUrl = "",
            modelDefault = "",
        ),
    )
}
