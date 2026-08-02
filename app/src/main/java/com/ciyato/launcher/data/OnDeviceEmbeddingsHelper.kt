package com.ciyato.launcher.data

import kotlin.math.sqrt

/**
 * OnDeviceEmbeddingsHelper — Suggestion #41
 * On-device embeddings for semantic file/app search using a lightweight
 * bag-of-words TF-IDF model with cosine similarity.
 *
 * For a full neural embedding, swap the `embed()` function with an ONNX or
 * TFLite Universal Sentence Encoder model. The cosine similarity and index
 * structures remain identical.
 */
object OnDeviceEmbeddingsHelper {

    data class EmbeddedDocument(
        val id: String,
        val text: String,
        val vector: FloatArray,
    )

    private val documentIndex = java.util.concurrent.CopyOnWriteArrayList<EmbeddedDocument>()
    private val idfCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

    /** Tokenize text into lowercase word tokens. */
    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 1 }

    /** Compute TF vector for a document. */
    private fun tfVector(tokens: List<String>, vocab: List<String>): FloatArray {
        val freq = tokens.groupingBy { it }.eachCount()
        val total = tokens.size.toFloat().coerceAtLeast(1f)
        return FloatArray(vocab.size) { i -> (freq[vocab[i]] ?: 0) / total }
    }

    /** Build the shared vocabulary from all indexed documents. */
    private fun buildVocab(): List<String> =
        documentIndex.flatMap { tokenize(it.text) }.toSet().sorted()

    /** Compute IDF weights for all vocab terms. */
    private fun computeIdf(vocab: List<String>): Map<String, Float> {
        val n = documentIndex.size.toFloat()
        return vocab.associateWith { term ->
            val df = documentIndex.count { doc -> tokenize(doc.text).contains(term) }.toFloat()
            if (df == 0f) 0f else Math.log((n / df) + 1.0).toFloat()
        }
    }

    /** Embed a text string into a TF-IDF vector over the current vocabulary. */
    fun embed(text: String, vocab: List<String> = buildVocab()): FloatArray {
        val tokens = tokenize(text)
        val tf = tfVector(tokens, vocab)
        return FloatArray(vocab.size) { i ->
            tf[i] * (idfCache[vocab[i]] ?: 1f)
        }
    }

    /** Cosine similarity between two vectors. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }

    /** Index a document for future semantic search. */
    fun index(id: String, text: String) {
        documentIndex.removeAll { it.id == id }
        documentIndex.add(EmbeddedDocument(id, text, FloatArray(0))) // vector computed lazily
        idfCache.clear() // Invalidate IDF cache
    }

    /** Index a batch of documents. */
    fun indexAll(documents: Map<String, String>) {
        documents.forEach { (id, text) -> index(id, text) }
        val vocab = buildVocab()
        idfCache.putAll(computeIdf(vocab))
        // Pre-compute vectors
        val indexed = documentIndex.map { doc ->
            doc.copy(vector = embed(doc.text, vocab))
        }
        documentIndex.clear()
        documentIndex.addAll(indexed)
    }

    /**
     * Semantic search: returns top-k document IDs ranked by cosine similarity
     * to the query embedding.
     */
    fun search(query: String, topK: Int = 10): List<Pair<String, Float>> {
        val vocab = buildVocab()
        if (idfCache.isEmpty()) idfCache.putAll(computeIdf(vocab))
        val queryVec = embed(query, vocab)
        return documentIndex
            .map { doc ->
                val vec = if (doc.vector.isEmpty()) embed(doc.text, vocab) else doc.vector
                doc.id to cosineSimilarity(queryVec, vec)
            }
            .filter { it.second > 0.01f }
            .sortedByDescending { it.second }
            .take(topK)
    }

    fun clearIndex() { documentIndex.clear(); idfCache.clear() }
}
