/** API预设配置: 预配置各厂商AI API地址/模型, 支持一键切换提供商 */
package com.aicompanion.api

object ApiProviderPreset {

    data class TtsVoiceInfo(
        val value: String,
        val label: String,
        val description: String = ""
    )

    data class ApiProvider(
        val name: String,
        val icon: String,
        val chatUrl: String,
        val modelDefault: String,
        val screenApiUrl: String = "",
        val screenModel: String = "",
        val ttsUrl: String = "",
        val ttsModel: String = "tts-1",
        val ttsVoice: String = "alloy",
        val ttsVoices: List<TtsVoiceInfo> = emptyList(),
        val asrUrl: String = "",
    )

    val providers = listOf(
        ApiProvider(
            name = "OpenAI (GPT)",
            icon = "\uD83D\uDFE2",
            chatUrl = "https://api.openai.com/v1/chat/completions",
            modelDefault = "gpt-4o-mini",
            screenApiUrl = "https://api.openai.com/v1/chat/completions",
            screenModel = "gpt-4o",
            ttsUrl = "https://api.openai.com/v1/audio/speech",
            ttsModel = "tts-1",
            ttsVoice = "alloy",
            ttsVoices = listOf(
                TtsVoiceInfo("alloy", "Alloy (中性)", "中性温和的男声"),
                TtsVoiceInfo("echo", "Echo (温和)", "温和的男声"),
                TtsVoiceInfo("fable", "Fable (英式)", "英式口音男声"),
                TtsVoiceInfo("onyx", "Onyx (深沉)", "深沉有力的男声"),
                TtsVoiceInfo("nova", "Nova (温柔)", "温柔的女声"),
                TtsVoiceInfo("shimmer", "Shimmer (清亮)", "清脆的女声"),
            ),
            asrUrl = "https://api.openai.com/v1/audio/transcriptions"
        ),
        ApiProvider(
            name = "DeepSeek",
            icon = "\uD83D\uDD35",
            chatUrl = "https://api.deepseek.com/v1/chat/completions",
            modelDefault = "deepseek-chat",
        ),
        ApiProvider(
            name = "\u7845\u57FA\u6D41\u52A8 (SiliconFlow)",
            icon = "\uD83D\uDFE3",
            chatUrl = "https://api.siliconflow.cn/v1/chat/completions",
            modelDefault = "THUDM/glm-4-9b-chat",
            ttsUrl = "https://api.siliconflow.cn/v1/audio/speech",
            ttsModel = "FunAudioLLM/CosyVoice2-0.5B",
            ttsVoice = "FunAudioLLM/CosyVoice2-0.5B:alex",
            ttsVoices = listOf(
                TtsVoiceInfo("FunAudioLLM/CosyVoice2-0.5B:alex", "Alex (沉稳男声)", "沉稳成熟男声"),
                TtsVoiceInfo("FunAudioLLM/CosyVoice2-0.5B:anna", "Anna (沉稳女声)", "沉稳优雅女声"),
                TtsVoiceInfo("FunAudioLLM/CosyVoice2-0.5B:bella", "Bella (激情女声)", "富有感情的女声"),
                TtsVoiceInfo("FunAudioLLM/CosyVoice2-0.5B:benjamin", "Benjamin (低沉男声)", "低沉男声"),
                TtsVoiceInfo("FunAudioLLM/CosyVoice2-0.5B:charles", "Charles (磁性男声)", "磁性男声"),
                TtsVoiceInfo("FunAudioLLM/CosyVoice2-0.5B:claire", "Claire (温柔女声)", "温柔女声"),
                TtsVoiceInfo("FunAudioLLM/CosyVoice2-0.5B:david", "David (欢快男声)", "欢快男声"),
                TtsVoiceInfo("FunAudioLLM/CosyVoice2-0.5B:diana", "Diana (甜美女声)", "甜美欢快女声"),
            ),
        ),
        ApiProvider(
            name = "\u901A\u4E49\u5343\u95EE (Qwen)",
            icon = "\uD83D\uDFE0",
            chatUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            modelDefault = "qwen-turbo",
            screenApiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            screenModel = "qwen-vl-plus",
            ttsUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
            ttsModel = "cosyvoice-v1",
            ttsVoice = "longxiaochun",
            ttsVoices = listOf(
                TtsVoiceInfo("longxiaochun", "\u9F99\u5C0F\u6625 (女)", "\u6E29\u67D4\u5973\u58F0"),
                TtsVoiceInfo("longyuxiang", "\u9F99\u96E8\u9999 (女)", "\u6E05\u4EAE\u5973\u58F0"),
                TtsVoiceInfo("longcheng", "\u9F99\u6A59 (男)", "\u9633\u5149\u7537\u58F0"),
                TtsVoiceInfo("longhua", "\u9F99\u534E (男)", "\u6C89\u7A33\u7537\u58F0"),
                TtsVoiceInfo("sambert-zhichu-v1", "\u829D\u521D (女)", "\u77E5\u6027\u5973\u58F0"),
            ),
        ),
        ApiProvider(
            name = "\u667A\u8C31AI (Zhipu)",
            icon = "\uD83D\uDD34",
            chatUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            modelDefault = "glm-4-flash",
            screenApiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            screenModel = "glm-4v-plus",
        ),
        ApiProvider(
            name = "\u6708\u4E4B\u6697\u9762 (Moonshot)",
            icon = "\uD83C\uDF19",
            chatUrl = "https://api.moonshot.cn/v1/chat/completions",
            modelDefault = "moonshot-v1-8k",
            screenApiUrl = "https://api.moonshot.cn/v1/chat/completions",
            screenModel = "moonshot-v1-8k",
        ),
        ApiProvider(
            name = "n1n",
            icon = "\uD83D\uDFE1",
            chatUrl = "https://api.n1n.ai/v1/chat/completions",
            modelDefault = "gpt-4o-mini",
        ),
        ApiProvider(
            name = "\u81EA\u5B9A\u4E49",
            icon = "\u2699\uFE0F",
            chatUrl = "",
            modelDefault = "",
        ),
    )

    val allTtsVoices: List<TtsVoiceInfo> = providers
        .flatMap { it.ttsVoices }
        .distinctBy { it.value }

    fun findVoiceLabel(provider: ApiProvider, voiceValue: String): String {
        val match = provider.ttsVoices.find { it.value == voiceValue }
        if (match != null) return match.label
        val global = allTtsVoices.find { it.value == voiceValue }
        return global?.label ?: voiceValue
    }
}