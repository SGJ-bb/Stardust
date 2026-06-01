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
    val supportsVision: Boolean = false,
    val paramHints: Map<String, String> = emptyMap()
) {
    companion object {
        private val PROFILES = listOf(
            ProviderProfile(
                id = "custom",
                displayName = "自定义",
                apiUrl = ServicePresets.getLlmUrl("custom"),
                defaultModel = ServicePresets.getLlmDefaultModel("custom"),
                maxTokensLimit = 131072,
                supportsVision = true,
                paramHints = mapOf(
                    "freq_penalty" to "部分API不支持此参数",
                    "pres_penalty" to "部分API不支持此参数"
                )
            ),
            ProviderProfile(
                id = "openai",
                displayName = "OpenAI",
                apiUrl = ServicePresets.getLlmUrl("openai"),
                defaultModel = ServicePresets.getLlmDefaultModel("openai"),
                tempRange = 0f..2f,
                topPRange = 0f..1f,
                freqPenaltyRange = (-2f)..2f,
                presPenaltyRange = (-2f)..2f,
                maxTokensLimit = 16384,
                defaultTemp = 1.0f,
                defaultTopP = 0.9f,
                defaultFreqPenalty = 0f,
                defaultPresPenalty = 0f,
                defaultMaxTokens = 1024,
                supportsVision = true
            ),
            ProviderProfile(
                id = "deepseek",
                displayName = "DeepSeek",
                apiUrl = ServicePresets.getLlmUrl("deepseek"),
                defaultModel = ServicePresets.getLlmDefaultModel("deepseek"),
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
                supportsVision = true,
                paramHints = mapOf(
                    "freq_penalty" to "DeepSeek: 仅支持0~2(无负数)",
                    "pres_penalty" to "DeepSeek: 仅支持0~2(无负数)",
                    "max_tokens" to "DeepSeek-V4-Flash最大输出384K tokens"
                )
            ),
            ProviderProfile(
                id = "aliyun",
                displayName = "阿里云百炼",
                apiUrl = ServicePresets.getLlmUrl("aliyun"),
                defaultModel = ServicePresets.getLlmDefaultModel("aliyun"),
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
                supportsVision = true,
                paramHints = mapOf(
                    "freq_penalty" to "阿里云百炼不支持此参数",
                    "pres_penalty" to "阿里云百炼不支持此参数"
                )
            ),
            ProviderProfile(
                id = "qwen",
                displayName = "通义千问",
                apiUrl = ServicePresets.getLlmUrl("qwen"),
                defaultModel = ServicePresets.getLlmDefaultModel("qwen"),
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
                supportsVision = true,
                paramHints = mapOf(
                    "freq_penalty" to "通义千问不支持此参数",
                    "pres_penalty" to "通义千问不支持此参数"
                )
            ),
            ProviderProfile(
                id = "zhipu",
                displayName = "智谱AI",
                apiUrl = ServicePresets.getLlmUrl("zhipu"),
                defaultModel = ServicePresets.getLlmDefaultModel("zhipu"),
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
                supportsVision = true,
                paramHints = mapOf(
                    "temperature" to "智谱AI: 温度范围0~1",
                    "freq_penalty" to "智谱AI不支持此参数",
                    "pres_penalty" to "智谱AI不支持此参数"
                )
            ),
            ProviderProfile(
                id = "minimax",
                displayName = "MiniMax",
                apiUrl = ServicePresets.getLlmUrl("minimax"),
                defaultModel = ServicePresets.getLlmDefaultModel("minimax"),
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
                supportsVision = true,
                paramHints = mapOf(
                    "temperature" to "MiniMax: 温度范围0~1",
                    "freq_penalty" to "MiniMax不支持此参数",
                    "pres_penalty" to "MiniMax不支持此参数"
                )
            ),
            ProviderProfile(
                id = "moonshot",
                displayName = "月之暗面",
                apiUrl = ServicePresets.getLlmUrl("moonshot"),
                defaultModel = ServicePresets.getLlmDefaultModel("moonshot"),
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
                supportsVision = true,
                paramHints = mapOf(
                    "temperature" to "月之暗面: 温度范围0~1",
                    "freq_penalty" to "月之暗面不支持此参数",
                    "pres_penalty" to "月之暗面不支持此参数"
                )
            ),
            ProviderProfile(
                id = "n1n",
                displayName = "n1n",
                apiUrl = ServicePresets.getLlmUrl("n1n"),
                defaultModel = ServicePresets.getLlmDefaultModel("n1n"),
                maxTokensLimit = 32768,
                defaultTemp = 1.0f,
                defaultTopP = 0.9f,
                defaultMaxTokens = 1024,
                supportsVision = true,
                paramHints = mapOf(
                    "max_tokens" to "具体限制取决于所选模型"
                )
            ),
            ProviderProfile(
                id = "siliconflow",
                displayName = "硅基流动",
                apiUrl = ServicePresets.getLlmUrl("siliconflow"),
                defaultModel = ServicePresets.getLlmDefaultModel("siliconflow"),
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
                supportsVision = true,
                paramHints = mapOf(
                    "freq_penalty" to "硅基流动部分模型不支持此参数",
                    "pres_penalty" to "硅基流动部分模型不支持此参数",
                    "max_tokens" to "具体限制取决于所选模型"
                )
            ),
            ProviderProfile(
                id = "openrouter",
                displayName = "OpenRouter",
                apiUrl = ServicePresets.getLlmUrl("openrouter"),
                defaultModel = ServicePresets.getLlmDefaultModel("openrouter"),
                tempRange = 0f..2f,
                topPRange = 0f..1f,
                freqPenaltyRange = (-2f)..2f,
                presPenaltyRange = (-2f)..2f,
                maxTokensLimit = 131072,
                defaultTemp = 1.0f,
                defaultTopP = 0.9f,
                defaultMaxTokens = 4096,
                supportsVision = true,
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

        fun supportsVision(providerId: String): Boolean {
            return getProfile(providerId).supportsVision
        }

        fun getMaxTokensLimit(providerId: String): Int {
            return getProfile(providerId).maxTokensLimit
        }
    }
}
