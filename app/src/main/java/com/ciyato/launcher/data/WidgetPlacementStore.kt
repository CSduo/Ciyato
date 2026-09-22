package com.ciyato.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Where a placed Android widget lives and how big it is.
 *
 * [heightDp] of 0 means "whatever the provider asked for" — the provider's own
 * `minHeight` is used. A non-zero value is a size the person chose by resizing,
 * and it wins over the provider's minimum because they can see the widget and
 * the provider cannot.
 *
 * [widthPercent] is a percentage of the usable canvas width rather than a dp
 * count, for the same reason [CanvasPos] stores fractions: a dp width chosen on
 * a phone is wrong on a tablet and wrong again after a rotation.
 *
 * The canvas *position* is deliberately NOT here. A placed widget is a Home
 * canvas object like any other, so its x/y live in
 * [WorkspaceRecord.objectPositions] under `widget:<appWidgetId>`, and it gets
 * dragging, z-order, reset-to-flow and the long-press menu from the same engine
 * as the greeting and the weather card. Two position stores would be two
 * answers to "where is this widget".
 */
data class WidgetPlacement(
    val appWidgetId: Int,
    val heightDp: Int = 0,
    val widthPercent: Int = 100,
) {
    /** Canvas object id — the single spelling, so nothing re-derives it by hand. */
    val objectId: String get() = objectIdFor(appWidgetId)

    companion object {
        /** Prefix marking a Home canvas object as a hosted Android widget. */
        const val OBJECT_PREFIX = "widget:"

        fun objectIdFor(appWidgetId: Int) = "$OBJECT_PREFIX$appWidgetId"

        /** The widget ID inside a canvas object id, or null if it is not one. */
        fun appWidgetIdIn(objectId: String): Int? =
            objectId.removePrefix(OBJECT_PREFIX)
                .takeIf { it != objectId }
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
    }
}

/**
 * Persists which widgets are on Home, and at what size.
 *
 * Replaces the bare list of IDs that [PlacedWidgetStore] wrote. Widgets were
 * hosted inside the manager screen and nowhere else: Settings promised
 * placement on the home screen and the feature delivered a gallery (F-179).
 * Placing them on Home means a widget now needs a size that survives a restart,
 * which a bare ID cannot carry.
 *
 * [parse] still reads the old `[1, 2, 3]` array. Anyone who added widgets
 * before this change keeps them — at provider-default size, which is exactly
 * what they had — instead of opening Home to an empty canvas and a set of
 * allocated IDs nothing owns any more.
 */
object WidgetPlacementStore {

    /** Matches the old store's cap, so a migrated layout can never grow by loading. */
    const val MAX_WIDGETS = 40

    /** Height bounds, in dp. A provider can report an absurd minHeight, and a
     *  widget that grows without limit pushes the rest of Home off the screen. */
    const val MIN_HEIGHT_DP = 48
    const val MAX_HEIGHT_DP = 420

    /** One resize step, in dp. */
    const val HEIGHT_STEP_DP = 40

    const val MIN_WIDTH_PERCENT = 40
    const val MAX_WIDTH_PERCENT = 100

    private const val KEY_ID = "id"
    private const val KEY_HEIGHT = "h"
    private const val KEY_WIDTH = "w"

    fun parse(raw: String): List<WidgetPlacement> = runCatching {
        val array = JSONArray(raw)
        val out = LinkedHashMap<Int, WidgetPlacement>()
        for (i in 0 until array.length()) {
            val placement = when (val entry = array.opt(i)) {
                // The format this store writes.
                is JSONObject -> {
                    val id = entry.optInt(KEY_ID, -1)
                    if (id <= 0) null else WidgetPlacement(
                        appWidgetId = id,
                        heightDp = clampHeight(entry.optInt(KEY_HEIGHT, 0)),
                        widthPercent = clampWidth(entry.optInt(KEY_WIDTH, 100)),
                    )
                }
                // The format the previous version wrote: a bare widget ID.
                is Number -> entry.toInt().takeIf { it > 0 }?.let { WidgetPlacement(it) }
                // A numeric string, which is what JSONArray hands back for an
                // array written by some other JSON writer.
                is String -> entry.toIntOrNull()?.takeIf { it > 0 }?.let { WidgetPlacement(it) }
                else -> null
            }
            // Keyed by ID so a file that somehow lists the same widget twice
            // cannot produce two host views fighting over one AppWidget ID.
            if (placement != null) out.putIfAbsent(placement.appWidgetId, placement)
        }
        out.values.take(MAX_WIDGETS)
    }.getOrDefault(emptyList())

    fun serialize(placements: List<WidgetPlacement>): String = JSONArray().apply {
        placements.distinctBy { it.appWidgetId }.take(MAX_WIDGETS).forEach { placement ->
            put(
                JSONObject()
                    .put(KEY_ID, placement.appWidgetId)
                    .put(KEY_HEIGHT, clampHeight(placement.heightDp))
                    .put(KEY_WIDTH, clampWidth(placement.widthPercent)),
            )
        }
    }.toString()

    /** 0 is preserved: it means "use the provider's own minimum", not "zero high". */
    fun clampHeight(dp: Int): Int = if (dp <= 0) 0 else dp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)

    fun clampWidth(percent: Int): Int =
        if (percent <= 0) MAX_WIDTH_PERCENT else percent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)

    /**
     * The height to render at, in dp.
     *
     * @param providerMinHeightDp the provider's declared minimum, already
     *   converted from px. Used only when the person has not resized.
     */
    fun resolvedHeightDp(placement: WidgetPlacement, providerMinHeightDp: Int): Int =
        if (placement.heightDp > 0) placement.heightDp
        else providerMinHeightDp.takeIf { it > 0 }?.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP) ?: 120

    /** Resize by one step, clamped. Starts from what is on screen now, so the
     *  first tap after a provider-sized render moves from the size the person
     *  can actually see rather than jumping to a default. */
    fun resized(placement: WidgetPlacement, providerMinHeightDp: Int, steps: Int): WidgetPlacement =
        placement.copy(
            heightDp = (resolvedHeightDp(placement, providerMinHeightDp) + steps * HEIGHT_STEP_DP)
                .coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
        )
}
