package com.aicompanion.rag

import kotlin.math.ln
import kotlin.math.sqrt

interface RagEmbedder {
    suspend fun embed(texts: List<String>): List<FloatArray>
    suspend fun embedSingle(text: String): FloatArray
    fun dimension(): Int
}

class TfidfEmbedder : RagEmbedder {

    private var vocabulary: MutableMap<String, Int> = mutableMapOf()
    private var idf: FloatArray = floatArrayOf()
    private var isBuilt = false

    override fun dimension(): Int = if (isBuilt) vocabulary.size else 0

    fun buildVocabulary(corpus: List<String>) {
        vocabulary.clear()
        val df = mutableMapOf<String, Int>()

        val tokenizedCorpus = corpus.map { tokenize(it) }

        for (tokens in tokenizedCorpus) {
            val uniqueTokens = tokens.toSet()
            for (token in uniqueTokens) {
                df[token] = (df[token] ?: 0) + 1
            }
        }

        val sortedTokens = df.entries
            .sortedByDescending { it.value }
            .take(2000)
            .map { it.key }

        vocabulary.clear()
        for ((i, token) in sortedTokens.withIndex()) {
            vocabulary[token] = i
        }

        val N = corpus.size.toFloat()
        idf = FloatArray(vocabulary.size)
        for ((token, idx) in vocabulary) {
            val docFreq = df[token] ?: 1
            idf[idx] = ln((N + 1) / (docFreq + 1)) + 1.0f
        }

        isBuilt = true
    }

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        return texts.map { embedSingleSync(it) }
    }

    override suspend fun embedSingle(text: String): FloatArray {
        return embedSingleSync(text)
    }

    fun embedSingleSync(text: String): FloatArray {
        if (!isBuilt) return FloatArray(0)

        val vec = FloatArray(vocabulary.size)
        val tokens = tokenize(text)
        val tokenCounts = tokens.groupingBy { it }.eachCount()
        val totalTokens = tokens.size.toFloat()

        for ((token, count) in tokenCounts) {
            val idx = vocabulary[token] ?: continue
            val tf = count / totalTokens
            vec[idx] = tf * idf[idx]
        }

        val norm = sqrt(vec.map { it * it }.sum())
        if (norm > 0) {
            for (i in vec.indices) vec[i] /= norm
        }

        return vec
    }

    fun embedSync(texts: List<String>): List<FloatArray> {
        return texts.map { embedSingleSync(it) }
    }

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()

        val cleaned = text.replace(Regex("[\\s\\p{Punct}&&[^。，！？…～]]"), "")
        if (cleaned.isEmpty()) return tokens

        for (i in cleaned.indices) {
            tokens.add(cleaned[i].toString())
        }

        for (i in 0 until cleaned.length - 1) {
            tokens.add(cleaned.substring(i, i + 2))
        }

        return tokens.distinct()
    }

    fun isReady(): Boolean = isBuilt
}