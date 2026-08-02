package com.ciyato.launcher.data

/**
 * TFLiteCategorizerHelper — Suggestion #28
 * On-device ML categorization using TensorFlow Lite.
 * Falls back to rule-based AppCategorizer when the TFLite model is not bundled.
 *
 * To enable full TFLite:
 *  1. Add `implementation("org.tensorflow:tensorflow-lite:2.14.0")` to build.gradle.
 *  2. Place `app_categorizer.tflite` in assets/ml/
 *  3. Uncomment the Interpreter block in classify().
 *
 * Model input:  float32[1, 64] — character n-gram embedding of label + package name.
 * Model output: float32[1, N_CATEGORIES] — softmax probability per AppCategory.
 */
object TFLiteCategorizerHelper {

    private val CATEGORY_LABELS = AppCategory.entries.map { it.name }

    private fun textToEmbedding(text: String, size: Int = 64): FloatArray {
        val result     = FloatArray(size)
        val normalized = text.lowercase().replace(Regex("[^a-z0-9]"), "")
        for (i in normalized.indices) {
            val idx = (normalized[i].code * 31 + i) % size
            result[idx] = (result[idx] + 1f).coerceAtMost(1f)
        }
        return result
    }

    /**
     * Classify an app using TFLite when the model is available,
     * otherwise delegate to the rule-based AppCategorizer.
     *
     * @param label       Human-readable app name (e.g. "Spotify").
     * @param packageName Android package name (e.g. "com.spotify.music").
     */
    fun classify(label: String, packageName: String): AppCategory {
        // TFLite path — uncomment when model asset is bundled:
        // val embedding = textToEmbedding("$label $packageName")
        // val interpreter = Interpreter(loadModelFile(context, "ml/app_categorizer.tflite"))
        // val output = Array(1) { FloatArray(CATEGORY_LABELS.size) }
        // interpreter.run(arrayOf(embedding), output)
        // val bestIdx = output[0].indices.maxByOrNull { output[0][it] } ?: 0
        // return AppCategory.valueOf(CATEGORY_LABELS[bestIdx])

        // Rule-based fallback — note: AppCategorizer.categorize(packageName, label)
        return AppCategorizer.categorize(packageName, label)
    }

    /**
     * Batch-classify a list of installed apps.
     * Returns a map of packageName → predicted AppCategory.
     */
    fun classifyAll(apps: List<InstalledApp>): Map<String, AppCategory> =
        apps.associate { it.packageName to classify(it.label, it.packageName) }

    /**
     * Returns a confidence score [0.1 – 1.0] for the current classifier's prediction.
     * 1.0 = full keyword match, 0.5 = prefix match, 0.1 = default/unknown.
     */
    fun confidence(label: String, packageName: String): Float {
        val category  = classify(label, packageName)
        val combined  = "$label $packageName".lowercase()
        val keywords  = AppCategorizer.categoryKeywords(category)
        return when {
            keywords.any { combined.contains(it) }             -> 1.0f
            keywords.any { combined.contains(it.take(4)) }     -> 0.5f
            else                                               -> 0.1f
        }
    }
}
