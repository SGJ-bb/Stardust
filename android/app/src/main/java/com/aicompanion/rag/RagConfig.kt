package com.aicompanion.rag

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.util.AppLogger

/**
 * RAG全局配置
 *
 * 所有配置自动持久化到SharedPreferences,
 * 确保重启后设置不丢失。
 *
 * 使用方式:
 * 1. Application.onCreate中调用 RagConfig.init(context)
 * 2. 之后直接读写 RagConfig.xxx 即可自动持久化
 */
object RagConfig {
    private const val PREFS_NAME = "rag_config"
    private lateinit var prefs: SharedPreferences

    // 默认值
    private const val DEFAULT_PERSONA_RAG_ENABLED = true
    private const val DEFAULT_PERSONA_TOP_K = 3
    private const val DEFAULT_CHUNK_MAX_CHARS = 300
    private const val DEFAULT_CHUNK_OVERLAP_CHARS = 60
    private const val DEFAULT_MIN_SIMILARITY = 0.12f
    private const val DEFAULT_EMBEDDING_MODE = "tfidf"
    private const val DEFAULT_CLOUD_MODEL = "text-embedding-3-small"
    private const val DEFAULT_RERANKER_ENABLED = false
    private const val DEFAULT_RERANKER_TOP_K_BEFORE = 10
    private const val DEFAULT_RERANKER_TOP_K_AFTER = 3
    private const val DEFAULT_RAG_TOOL_MODE = "auto"  // "auto"=自动注入 | "tool"=工具调用

    /**
     * 初始化(在Application.onCreate中调用)
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        AppLogger.i("RagConfig", "Initialized with SharedPreferences")
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = ::prefs.isInitialized

    // ─── 基础配置 ──────────────────────────────────

    var personaRagEnabled: Boolean
        get() = if (isInitialized()) prefs.getBoolean("personaRagEnabled", DEFAULT_PERSONA_RAG_ENABLED) else DEFAULT_PERSONA_RAG_ENABLED
        set(value) { if (isInitialized()) prefs.edit().putBoolean("personaRagEnabled", value).apply() }

    var personaTopK: Int
        get() = if (isInitialized()) prefs.getInt("personaTopK", DEFAULT_PERSONA_TOP_K) else DEFAULT_PERSONA_TOP_K
        set(value) { if (isInitialized()) prefs.edit().putInt("personaTopK", value).apply() }

    var chunkMaxChars: Int
        get() = if (isInitialized()) prefs.getInt("chunkMaxChars", DEFAULT_CHUNK_MAX_CHARS) else DEFAULT_CHUNK_MAX_CHARS
        set(value) { if (isInitialized()) prefs.edit().putInt("chunkMaxChars", value).apply() }

    var chunkOverlapChars: Int
        get() = if (isInitialized()) prefs.getInt("chunkOverlapChars", DEFAULT_CHUNK_OVERLAP_CHARS) else DEFAULT_CHUNK_OVERLAP_CHARS
        set(value) { if (isInitialized()) prefs.edit().putInt("chunkOverlapChars", value).apply() }

    var minSimilarity: Float
        get() = if (isInitialized()) prefs.getFloat("minSimilarity", DEFAULT_MIN_SIMILARITY) else DEFAULT_MIN_SIMILARITY
        set(value) { if (isInitialized()) prefs.edit().putFloat("minSimilarity", value).apply() }

    // ─── 嵌入模式 ──────────────────────────────────

    /**
     * 嵌入模式: "tfidf" | "local" | "cloud"
     */
    var embeddingMode: String
        get() = if (isInitialized()) prefs.getString("embeddingMode", DEFAULT_EMBEDDING_MODE) ?: DEFAULT_EMBEDDING_MODE else DEFAULT_EMBEDDING_MODE
        set(value) {
            val validated = if (value in listOf("tfidf", "local", "cloud")) value else {
                AppLogger.e("RagConfig", "Invalid embeddingMode: $value, falling back to tfidf")
                "tfidf"
            }
            if (isInitialized()) prefs.edit().putString("embeddingMode", validated).apply()
        }

    /**
     * 兼容旧配置: useCloudEmbedding 迁移到 embeddingMode
     */
    var useCloudEmbedding: Boolean
        get() = embeddingMode == "cloud"
        set(value) { if (value) embeddingMode = "cloud" }

    // ─── 云端嵌入配置 ──────────────────────────────

    var cloudEmbeddingModel: String
        get() = if (isInitialized()) prefs.getString("cloudEmbeddingModel", DEFAULT_CLOUD_MODEL) ?: DEFAULT_CLOUD_MODEL else DEFAULT_CLOUD_MODEL
        set(value) { if (isInitialized()) prefs.edit().putString("cloudEmbeddingModel", value).apply() }

    var cloudEmbeddingUrl: String
        get() = if (isInitialized()) prefs.getString("cloudEmbeddingUrl", "") ?: "" else ""
        set(value) { if (isInitialized()) prefs.edit().putString("cloudEmbeddingUrl", value).apply() }

    var cloudEmbeddingApiKey: String
        get() = if (isInitialized()) prefs.getString("cloudEmbeddingApiKey", "") ?: "" else ""
        set(value) { if (isInitialized()) prefs.edit().putString("cloudEmbeddingApiKey", value).apply() }

    // ─── 重排序配置 ──────────────────────────────

    var rerankerEnabled: Boolean
        get() = if (isInitialized()) prefs.getBoolean("rerankerEnabled", DEFAULT_RERANKER_ENABLED) else DEFAULT_RERANKER_ENABLED
        set(value) { if (isInitialized()) prefs.edit().putBoolean("rerankerEnabled", value).apply() }

    var rerankerTopKBefore: Int
        get() = if (isInitialized()) prefs.getInt("rerankerTopKBefore", DEFAULT_RERANKER_TOP_K_BEFORE) else DEFAULT_RERANKER_TOP_K_BEFORE
        set(value) { if (isInitialized()) prefs.edit().putInt("rerankerTopKBefore", value).apply() }

    var rerankerTopKAfter: Int
        get() = if (isInitialized()) prefs.getInt("rerankerTopKAfter", DEFAULT_RERANKER_TOP_K_AFTER) else DEFAULT_RERANKER_TOP_K_AFTER
        set(value) { if (isInitialized()) prefs.edit().putInt("rerankerTopKAfter", value).apply() }

    // ─── RAG模式 ──────────────────────────────

    /**
     * RAG模式:
     * - "auto": 自动注入(每次对话都检索并注入相关上下文)
     * - "tool": 工具调用(LLM主动决定何时检索)
     */
    var ragMode: String
        get() = if (isInitialized()) prefs.getString("ragMode", DEFAULT_RAG_TOOL_MODE) ?: DEFAULT_RAG_TOOL_MODE else DEFAULT_RAG_TOOL_MODE
        set(value) { if (isInitialized()) prefs.edit().putString("ragMode", value).apply() }

    // ─── 树结构RAG配置 ──────────────────────────

    /**
     * 树结构RAG: 检索时沿超链接扩展上下文
     */
    var treeRagEnabled: Boolean
        get() = if (isInitialized()) prefs.getBoolean("treeRagEnabled", true) else true
        set(value) { if (isInitialized()) prefs.edit().putBoolean("treeRagEnabled", value).apply() }

    /**
     * 树结构RAG: 最大扩展深度
     */
    var treeRagMaxDepth: Int
        get() = if (isInitialized()) prefs.getInt("treeRagMaxDepth", 2) else 2
        set(value) { if (isInitialized()) prefs.edit().putInt("treeRagMaxDepth", value).apply() }
}