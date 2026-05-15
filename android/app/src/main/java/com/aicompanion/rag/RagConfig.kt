package com.aicompanion.rag

object RagConfig {
    var personaRagEnabled: Boolean = true
    var useCloudEmbedding: Boolean = false
    var personaTopK: Int = 3
    var chunkMaxChars: Int = 300
    var chunkOverlapChars: Int = 60
    var minSimilarity: Float = 0.12f
    var cloudEmbeddingModel: String = "text-embedding-3-small"
}