package com.ciyato.launcher.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Free on-device photo categorization via ML Kit's bundled image-labeling
 * model. No API keys, no credits, works offline. Labels are collapsed into a
 * small set of user-facing collections.
 */
object PhotoAiLabeler {

    /** ML Kit label → Ciyato collection. Unlisted labels are ignored. */
    private val LABEL_BUCKETS: Map<String, String> = buildMap {
        listOf("Food", "Dessert", "Drink", "Fruit", "Vegetable", "Pizza", "Cake").forEach { put(it, "Food") }
        listOf("Dog", "Cat", "Bird", "Pet", "Animal", "Horse", "Fish").forEach { put(it, "Pets & Animals") }
        listOf("Person", "Selfie", "Smile", "Crowd", "Bride", "Baby").forEach { put(it, "People") }
        listOf("Beach", "Mountain", "Sky", "Sunset", "Flower", "Tree", "Waterfall", "Snow", "Lake", "Nature").forEach { put(it, "Nature") }
        listOf("Car", "Motorcycle", "Bicycle", "Airplane", "Train", "Boat", "Vehicle").forEach { put(it, "Vehicles") }
        listOf("Paper", "Text", "Receipt", "Document", "Whiteboard", "Menu", "Poster").forEach { put(it, "Documents") }
        listOf("Building", "House", "Skyscraper", "Bridge", "Room", "Furniture", "Interior").forEach { put(it, "Places") }
        listOf("Screenshot", "Website", "Computer", "Monitor", "Phone").forEach { put(it, "Screens & Tech") }
        listOf("Fashion", "Shoe", "Dress", "Jeans", "Jewelry").forEach { put(it, "Fashion") }
    }

    private const val MIN_CONFIDENCE = 0.72f

    data class AiScanResult(
        val collections: Map<String, List<PhotoDeviceLibrary.DeviceImage>>,
        val scannedCount: Int,
    )

    /**
     * Labels up to [maxImages] images (newest first) and groups them.
     * [onProgress] reports (done, total) for UI feedback.
     */
    suspend fun categorize(
        context: Context,
        images: List<PhotoDeviceLibrary.DeviceImage>,
        maxImages: Int = 250,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): AiScanResult = withContext(Dispatchers.Default) {
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(MIN_CONFIDENCE)
                .build(),
        )
        val targets = images.take(maxImages)
        val grouped = mutableMapOf<String, MutableList<PhotoDeviceLibrary.DeviceImage>>()
        try {
            targets.forEachIndexed { index, image ->
                val labels = labelUri(context, labeler, image.uri)
                labels
                    .mapNotNull { LABEL_BUCKETS[it] }
                    .distinct()
                    .forEach { bucket -> grouped.getOrPut(bucket, ::mutableListOf).add(image) }
                onProgress(index + 1, targets.size)
            }
        } finally {
            labeler.close()
        }
        AiScanResult(
            collections = grouped.filterValues { it.size >= 3 },
            scannedCount = targets.size,
        )
    }

    private suspend fun labelUri(
        context: Context,
        labeler: com.google.mlkit.vision.label.ImageLabeler,
        uri: Uri,
    ): List<String> = suspendCancellableCoroutine { continuation ->
        runCatching { InputImage.fromFilePath(context, uri) }
            .onFailure { if (continuation.isActive) continuation.resume(emptyList()) }
            .onSuccess { input ->
                labeler.process(input)
                    .addOnSuccessListener { labels ->
                        if (continuation.isActive) continuation.resume(labels.map { it.text })
                    }
                    .addOnFailureListener { if (continuation.isActive) continuation.resume(emptyList()) }
            }
    }
}
