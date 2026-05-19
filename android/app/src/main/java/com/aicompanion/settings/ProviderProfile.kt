package com.aicompanion.settings

data class ProviderProfile(
    val id: String,
    val displayName: String,
    val apiUrl: String,
    val defaultModel: String,
    val tempRange: ClosedFloatingPointRange<Float> = 0f..2f,
    val topPRange: ClosedFloatingPointRange<Float> = 0f..1f,
    val freqPenaltyRange: ClosedFloatingPointRange<Float>? = (-2f)..2f,
    val presPenaltyRange: ClosedFloatingPointRange<Float>? = (-2f)..2f,
    val maxTokensLimit: Int = 16384,
    val defaultTemp: Float = 1.05f,
    val defaultTopP: Float = 0.92f,
    val defaultFreqPenalty: Float = 0.35f,
    val defaultPresPenalty: Float = 0.5f,
    val defaultMaxTokens: Int = 500,
    val supportsFreqPenalty: Boolean = true,
    val supportsPresPenalty: Boolean = true,
    val paramHints: Map<String, String> = emptyMap()
) {
    companion object {
        private val PROFILES = listOf(
            ProviderProfile(
                id = "custom",
                displayName = "自定义",
                apiUrl = "",
                defaultModel = "",
                maxTokensLimit = 131072,
                paramHints = mapOf(
                    "freq_penalty" to "部分API不支持此参数",
                    "pres_penalty" to "部分API不支持此参数"
                )
            ),
            ProviderProfile(
                id = "openai",
                displayName = "OpenAI",
                apiUrl = "https://api.openai.com/v1/chat/completions",
                defaultModel = "gpt-4o-mini",
                tempRange = 0f..2f,
                topPRange = 0f..1f,
                freqPenaltyRange = (-2f)..2f,
                presPenaltyRange = (-2f)..2f,
                maxTokensLimit = 16384,
                defaultTemp = 1.0f,
                defaultTopP = 0.9f,
                defaultFreqPenalty = 0f,
                defaultPresPenalty = 0f,
                defaultMaxTokens = 1024
            ),
            ProviderProfile(
                id = "deepseek",
                displayName = "DeepSeek",
                apiUrl = "https://api.deepseek.com/v1/chat/completions",
                defaultModel = "deepseek-v4-flash",
                tempRange = 0f..2f,
                topPRange = 0f..1f,
                freqPenaltyRange = 0f..2f,
                presPenaltyRange = 0f..2f,
                maxTokensLimit = 393216,
                defaultTemp = 1.0f,
                defaultTopP = 0.9f,
                defaultFreqPenalty = 0f,
                defaultPresPenalty = 0f,
                defaultMaxTokens = 4096,
                paramHints = mapOf(
                    "freq_penalty" to "DeepSeek: 仅支持0~2(无负数)",
                    "pres_penalty" to "DeepSeek: 仅支持0~2(无负数)",
                    "max_tokens" to "DeepSeek-V4-Flash最大输出384K tokens"
                )
            ),
            ProviderProfile(
                id = "aliyun",
                displayName = "阿里云百炼",
                apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                defaultModel = "qwen-plus",
                tempRange = 0f..2f,
                topPRange = 0f..1f,
                freqPenaltyRange = null,
                presPenaltyRange = null,
                maxTokensLimit = 8192,
                defaultTemp = 0.85f,
                defaultTopP = 0.8f,
                defaultMaxTokens = 2048,
                supportsFreqPenalty = false,
                supportsPresPenalty = false,
                paramHints = mapOf(
                    "freq_penalty" to "阿里云百炼不支持此参数",
                    "pres_penalty" to "阿里云百炼不支持此参数"
                )
            ),
            ProviderProfile(
                id = "qwen",
                displayName = "通义千问",
                apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                defaultModel = "qwen-max",
                tempRange = 0f..2f,
                topPRange = 0f..1f,
                freqPenaltyRange = null,
                presPenaltyRange = null,
                maxTokensLimit = 8192,
                defaultTemp = 0.85f,
                defaultTopP = 0.8f,
                defaultMaxTokens = 2048,
                supportsFreqPenalty = false,
                supportsPresPenalty = false,
                paramHints = mapOf(
                    "freq_penalty" to "通义千问不支持此参数",
                    "pres_penalty" to "通义千问不支持此参数"
                )
            ),
            ProviderProfile(
                id = "zhipu",
                displayName = "智谱AI",
                apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                defaultModel = "glm-4-flash",
                tempRange = 0f..1f,
                topPRange = 0f..1f,
                freqPenaltyRange = null,
                presPenaltyRange = null,
                maxTokensLimit = 8192,
                defaultTemp = 0.7f,
                defaultTopP = 0.7f,
                defaultMaxTokens = 2048,
                supportsFreqPenalty = false,
                supportsPresPenalty = false,
                paramHints = mapOf(
                    "temperature" to "智谱AI: 温度范围0~1",
                    "freq_penalty" to "智谱AI不支持此参数",
                    "pres_penalty" to "智谱AI不支持此参数"
                )
            ),
            ProviderProfile(
                id = "minimax",
                displayName = "MiniMax",
                apiUrl = "https://api.minimax.chat/v1/text/chatcompletion_v2",
                defaultModel = "MiniMax-Text-01",
                tempRange = 0f..1f,
                topPRange = 0f..1f,
                freqPenaltyRange = null,
                presPenaltyRange = null,
                maxTokensLimit = 8192,
                defaultTemp = 0.7f,
                defaultTopP = 0.7f,
                defaultMaxTokens = 2048,
                supportsFreqPenalty = false,
                supportsPresPenalty = false,
                paramHints = mapOf(
                    "temperature" to "MiniMax: 温度范围0~1",
                    "freq_penalty" to "MiniMax不支持此参数",
                    "pres_penalty" to "MiniMax不支持此参数"
                )
            ),
            ProviderProfile(
                id = "moonshot",
                displayName = "月之暗面",
                apiUrl = "https://api.moonshot.cn/v1/chat/completions",
                defaultModel = "moonshot-v1-8k",
                tempRange = 0f..1f,
                topPRange = 0f..1f,
                freqPenaltyRange = null,
                presPenaltyRange = null,
                maxTokensLimit = 8192,
                defaultTemp = 0.7f,
                defaultTopP = 0.7f,
                defaultMaxTokens = 2048,
                supportsFreqPenalty = false,
                supportsPresPenalty = false,
                paramHints = mapOf(
                    "temperature" to "月之暗面: 温度范围0~1",
                    "freq_penalty" to "月之暗面不支持此参数",
                    "pres_penalty" to "月之暗面不支持此参数"
                )
            ),
            ProviderProfile(
                id = "n1n",
                displayName = "n1n",
                apiUrl = "https://api.n1n.ai/v1/chat/completions",
                defaultModel = "gpt-4o-mini",
                maxTokensLimit = 32768,
                defaultTemp = 1.0f,
                defaultTopP = 0.9f,
                defaultMaxTokens = 1024,
                paramHints = mapOf(
                    "max_tokens" to "具体限制取决于所选模型"
                )
            ),
            ProviderProfile(
                id = "siliconflow",
                displayName = "硅基流动",
                apiUrl = "https://api.siliconflow.cn/v1/chat/completions",
                defaultModel = "Qwen/Qwen2.5-7B-Instruct",
                tempRange = 0f..2f,
                topPRange = 0f..1f,
                freqPenaltyRange = null,
                presPenaltyRange = null,
                maxTokensLimit = 8192,
                defaultTemp = 0.7f,
                defaultTopP = 0.7f,
                defaultMaxTokens = 2048,
                supportsFreqPenalty = false,
                supportsPresPenalty = false,
                paramHints = mapOf(
                    "freq_penalty" to "硅基流动部分模型不支持此参数",
                    "pres_penalty" to "硅基流动部分模型不支持此参数",
                    "max_tokens" to "具体限制取决于所选模型"
                )
            ),
            ProviderProfile(
                id = "openrouter",
                displayName = "OpenRouter",
                apiUrl = "https://openrouter.ai/api/v1/chat/completions",
                defaultModel = "google/gemini-2.0-flash-001",
                tempRange = 0f..2f,
                topPRange = 0f..1f,
                freqPenaltyRange = (-2f)..2f,
                presPenaltyRange = (-2f)..2f,
                maxTokensLimit = 131072,
                defaultTemp = 1.0f,
                defaultTopP = 0.9f,
                defaultMaxTokens = 4096,
                paramHints = mapOf(
                    "max_tokens" to "具体限制取决于所选模型"
                )
            )
        )

        fun getProfile(id: String): ProviderProfile {
            return PROFILES.find { it.id == id } ?: PROFILES.first()
        }

        fun getAllProfiles(): List<ProviderProfile> = PROFILES

        fun getDisplayName(id: String): String = getProfile(id).displayName

        fun shouldSendFreqPenalty(providerId: String): Boolean {
            return getProfile(providerId).supportsFreqPenalty
        }

        fun shouldSendPresPenalty(providerId: String): Boolean {
            return getProfile(providerId).supportsPresPenalty
        }

        fun getMaxTokensLimit(providerId: String): Int {
            return getProfile(providerId).maxTokensLimit
        }
    }
}
