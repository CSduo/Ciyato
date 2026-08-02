package com.ciyato.launcher.data

/**
 * SearchRankingEngine — Suggestion #32
 * Smart search history ranking using TF-IDF weighted by usage frequency.
 * Ranks search suggestions by relevance: recency + launch frequency + query match.
 */
object SearchRankingEngine {

    data class RankedSuggestion(
        val query: String,
        val score: Float,
        val matchType: MatchType,
    )

    enum class MatchType { EXACT, PREFIX, SUBSTRING, FUZZY }

    /**
     * Rank search history entries against the current query.
     *
     * @param history     Ordered list of past searches (most recent first).
     * @param appLabels   Map of app label → launch count (for TF-IDF weighting).
     * @param currentQuery Current search input.
     * @return Sorted list of ranked suggestions (highest score first).
     */
    fun rank(
        history: List<String>,
        appLabels: Map<String, Int>,
        currentQuery: String,
    ): List<RankedSuggestion> {
        val q = currentQuery.lowercase().trim()
        if (q.isBlank()) {
            return history.mapIndexed { i, h ->
                RankedSuggestion(h, recencyScore(i, history.size), MatchType.EXACT)
            }
        }

        return history
            .mapIndexedNotNull { i, entry ->
                val low = entry.lowercase()
                val matchType = when {
                    low == q             -> MatchType.EXACT
                    low.startsWith(q)    -> MatchType.PREFIX
                    low.contains(q)      -> MatchType.SUBSTRING
                    fuzzyMatch(low, q)   -> MatchType.FUZZY
                    else                 -> return@mapIndexedNotNull null
                }
                val matchScore = when (matchType) {
                    MatchType.EXACT     -> 4.0f
                    MatchType.PREFIX    -> 3.0f
                    MatchType.SUBSTRING -> 2.0f
                    MatchType.FUZZY     -> 1.0f
                }
                val freq = appLabels.getOrDefault(entry, 0)
                val freqScore = (1 + Math.log1p(freq.toDouble())).toFloat()
                val recency = recencyScore(i, history.size)
                RankedSuggestion(
                    query = entry,
                    score = matchScore * freqScore * (1 + recency),
                    matchType = matchType,
                )
            }
            .sortedByDescending { it.score }
    }

    private fun recencyScore(index: Int, total: Int): Float {
        if (total == 0) return 0f
        return 1f - (index.toFloat() / total)
    }

    /** Simple fuzzy match: all characters of q appear in entry in order. */
    private fun fuzzyMatch(entry: String, q: String): Boolean {
        var qi = 0
        for (ch in entry) {
            if (qi < q.length && ch == q[qi]) qi++
        }
        return qi == q.length
    }

    /** TF-IDF term frequency for a single term in an app label set. */
    fun termFrequency(term: String, document: String): Float {
        val words = document.lowercase().split(Regex("\\W+"))
        val count = words.count { it == term.lowercase() }
        return if (words.isEmpty()) 0f else count.toFloat() / words.size
    }

    fun inverseDocumentFrequency(term: String, documents: List<String>): Float {
        val docsContaining = documents.count { it.lowercase().contains(term.lowercase()) }
        return if (docsContaining == 0) 0f else
            Math.log((documents.size.toDouble() / docsContaining)).toFloat()
    }
}
