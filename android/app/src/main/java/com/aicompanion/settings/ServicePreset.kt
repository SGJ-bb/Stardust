package com.aicompanion.settings

data class ServicePreset(
    val id: String,
    val displayName: String,
    val url: String,
    val defaultModel: String,
    val defaultVoice: String = "",
    val formatType: String = "openai",
    val models: List<String> = emptyList()
)

object ServicePresets {

    val llmPresets = listOf(
        ServicePreset("custom", "自定义", "", "", "", "openai", emptyList()),
        ServicePreset("openai", "OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini", "", "openai",
            listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo", "o1-mini", "o1-preview")),
        ServicePreset("aliyun", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus", "", "openai",
            listOf("qwen-max", "qwen-plus", "qwen-turbo", "qwen-long", "qwen-vl-max", "qwen-vl-plus")),
        ServicePreset("zhipu", "智谱AI", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash", "", "openai",
            listOf("glm-4-plus", "glm-4", "glm-4-air", "glm-4-airx", "glm-4-flash", "glm-4-flashx", "glm-4v")),
        ServicePreset("minimax", "MiniMax", "https://api.minimax.chat/v1/text/chatcompletion_v2", "MiniMax-M1", "", "openai",
            listOf("MiniMax-M1", "MiniMax-Text-01", "abab6.5s-chat", "abab6.5-chat")),
        ServicePreset("moonshot", "月之暗面", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k", "", "openai",
            listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k", "moonshot-v1-auto")),
        ServicePreset("n1n", "n1n", "https://api.n1n.ai/v1/chat/completions", "gpt-4o-mini", "", "openai",
            listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "claude-3.5-sonnet", "deepseek-chat")),
        ServicePreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1/chat/completions", "deepseek-chat", "", "openai",
            listOf("deepseek-chat", "deepseek-reasoner", "deepseek-coder", "deepseek-v4-flash")),
        ServicePreset("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1/chat/completions", "Qwen/Qwen2.5-7B-Instruct", "", "openai",
            listOf("Qwen/Qwen2.5-7B-Instruct", "Qwen/Qwen2.5-72B-Instruct", "deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1", "meta-llama/Llama-3.3-70B-Instruct")),
        ServicePreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "google/gemini-2.0-flash-001", "", "openai",
            listOf("google/gemini-2.0-flash-001", "openai/gpt-4o", "openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet", "deepseek/deepseek-chat")),
        ServicePreset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-max", "", "openai",
            listOf("qwen-max", "qwen-plus", "qwen-turbo", "qwen-long"))
    )

    val ttsPresets = listOf(
        ServicePreset("custom", "自定义", "", "", "", "openai"),
        ServicePreset("openai", "OpenAI", "https://api.openai.com/v1/audio/speech", "tts-1", "alloy", "openai"),
        ServicePreset("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1/audio/speech", "FunAudioLLM/CosyVoice2-0.5B", "FunAudioLLM/CosyVoice2-0.5B:alex", "openai"),
        ServicePreset("fish_audio", "Fish Audio", "https://api.fish.audio/v1/tts", "fish-speech-1.5", "", "fish_audio"),
        ServicePreset("aliyun", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1/audio/speech", "cosyvoice-v1", "longxiaochun", "openai"),
        ServicePreset("mimo", "小米 MiMoTTS", "https://api.xiaomimimo.com/v1/chat/completions", "mimo-v2.5-tts", "冰糖", "mimo")
    )

    val asrPresets = listOf(
        ServicePreset("custom", "自定义", "", "", "", "openai"),
        ServicePreset("openai", "OpenAI", "https://api.openai.com/v1/audio/transcriptions", "whisper-1", "", "openai"),
        ServicePreset("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1/audio/transcriptions", "FunAudioLLM/SenseVoiceSmall", "", "openai"),
        ServicePreset("aliyun", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1/audio/transcriptions", "sensevoice-v1", "", "openai")
    )

    val imageGenPresets = listOf(
        ServicePreset("custom", "自定义", "", "", "", "openai"),
        ServicePreset("openai", "OpenAI (DALL-E)", "https://api.openai.com/v1/images/generations", "dall-e-3", "", "openai"),
        ServicePreset("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1/images/generations", "Kwai-Kolors/Kolors", "", "siliconflow"),
        ServicePreset("kling", "可灵AI (快手官方)", "https://api-beijing.klingai.com/v1/images/generations", "kling-v2-master", "", "kling"),
        ServicePreset("aliyun_kling", "阿里云百炼-可灵 (快手)", "https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation", "kling/kling-v3-image-generation", "", "aliyun_async"),
        ServicePreset("aliyun_wan", "阿里云百炼-万相", "https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation", "wan2.1-t2i-turbo", "", "aliyun_async"),
        ServicePreset("zhipu", "智谱AI (CogView)", "https://open.bigmodel.cn/api/paas/v4/images/generations", "cogview-4-250304", "", "openai")
    )

    val imageRecogPresets = listOf(
        ServicePreset("custom", "自定义", "", "", "", "openai"),
        ServicePreset("openai", "OpenAI (GPT-4o)", "https://api.openai.com/v1/chat/completions", "gpt-4o", "", "openai"),
        ServicePreset("aliyun", "阿里云百炼 (Qwen-VL)", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-vl-max", "", "openai"),
        ServicePreset("zhipu", "智谱AI (GLM-4V)", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4v-flash", "", "openai"),
        ServicePreset("siliconflow", "硅基流动 (Qwen-VL)", "https://api.siliconflow.cn/v1/chat/completions", "Qwen/Qwen2-VL-7B-Instruct", "", "openai")
    )

    fun findLlmPreset(id: String): ServicePreset = llmPresets.find { it.id == id } ?: llmPresets[0]
    fun findTtsPreset(id: String): ServicePreset = ttsPresets.find { it.id == id } ?: ttsPresets[0]
    fun findAsrPreset(id: String): ServicePreset = asrPresets.find { it.id == id } ?: asrPresets[0]
    fun findImageGenPreset(id: String): ServicePreset = imageGenPresets.find { it.id == id } ?: imageGenPresets[0]
    fun findImageRecogPreset(id: String): ServicePreset = imageRecogPresets.find { it.id == id } ?: imageRecogPresets[0]

    fun getLlmDisplayNames(): Array<String> = llmPresets.map { it.displayName }.toTypedArray()
    fun getTtsDisplayNames(): Array<String> = ttsPresets.map { it.displayName }.toTypedArray()
    fun getAsrDisplayNames(): Array<String> = asrPresets.map { it.displayName }.toTypedArray()
    fun getImageGenDisplayNames(): Array<String> = imageGenPresets.map { it.displayName }.toTypedArray()
    fun getImageRecogDisplayNames(): Array<String> = imageRecogPresets.map { it.displayName }.toTypedArray()

    fun getLlmIndex(id: String): Int = llmPresets.indexOfFirst { it.id == id }.coerceAtLeast(0)
    fun getTtsIndex(id: String): Int = ttsPresets.indexOfFirst { it.id == id }.coerceAtLeast(0)
    fun getAsrIndex(id: String): Int = asrPresets.indexOfFirst { it.id == id }.coerceAtLeast(0)
    fun getImageGenIndex(id: String): Int = imageGenPresets.indexOfFirst { it.id == id }.coerceAtLeast(0)
    fun getImageRecogIndex(id: String): Int = imageRecogPresets.indexOfFirst { it.id == id }.coerceAtLeast(0)

    private val llmKnownDefaults = llmPresets.map { it.defaultModel }.filter { it.isNotEmpty() }.toSet()
        .plus(setOf("deepseek-chat", "deepseek-reasoner", "gpt-3.5-turbo", "gpt-4", "gpt-4-turbo"))

    fun getLlmKnownDefaults(): Set<String> = llmKnownDefaults

    fun getLlmDefaultModel(id: String): String = findLlmPreset(id).defaultModel
    fun getLlmUrl(id: String): String = findLlmPreset(id).url
    fun getLlmDisplayName(id: String): String = findLlmPreset(id).displayName
}
