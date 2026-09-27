package com.ciyato.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * The one description an app tile announces, wherever it is drawn.
 *
 * Every surface that draws an app — the Home grid, the dock, the drawer, a
 * category, the hidden-apps list — had its own answer to "what does TalkBack
 * say about this", and several had none at all. The icon itself is correctly
 * `contentDescription = null`, which is only safe when a parent supplies the
 * label, the role, and the state; that contract was held by convention across
 * five call sites and was not actually held in all of them (F-047).
 *
 * There was a helper for this, `appItemSemantics`, with **zero call sites** and
 * two defects of its own. It appended "Double-tap to open. Long-press for
 * options." to the description — TalkBack already announces the activation
 * gesture for a Button role, so that was a duplicated instruction, and a
 * long-press described in prose is still not *reachable* by switch access or a
 * keyboard. And it did not merge descendants, so the label `Text` inside the
 * tile announced separately: "Gmail app, 3 notifications" immediately followed
 * by "Gmail".
 *
 * So this replaces it: state in the description, gestures left to the platform,
 * long-press published as a real action, and descendants merged so a tile is one
 * thing.
 */

/**
 * What TalkBack should say for an app tile.
 *
 * Pure, so the wording is testable — a screen-reader string is exactly the kind
 * of thing that is never checked by looking at the screen.
 *
 * Deliberately no gesture text. "Double-tap to activate" is the platform's
 * sentence and it says it already; adding our own produces "Gmail app. Double-tap
 * to open. Long-press for options. Double-tap to activate."
 */
fun appTileDescription(
    label: String,
    badgeCount: Int = 0,
    isPinned: Boolean = false,
    isHidden: Boolean = false,
    isLocked: Boolean = false,
): String = buildString {
    append(label.ifBlank { "Unnamed app" })
    // Order is fixed so the same tile always reads the same way. Locked first:
    // it changes what happens when you activate the tile, which matters more
    // than where the tile lives.
    if (isLocked) append(", locked")
    if (isHidden) append(", hidden")
    if (isPinned) append(", pinned to dock")
    if (badgeCount > 0) {
        append(", $badgeCount notification")
        if (badgeCount != 1) append("s")
    }
}

/**
 * Merged semantics for one interactive app tile.
 *
 * Applied to the tile's outermost node, so the icon and the label text inside it
 * are announced as one item rather than two.
 *
 * @param positionNote grid position while editing, or any other state a sighted
 *   person can see and a screen-reader user cannot. Empty string clears it —
 *   `stateDescription` is not nullable in the semantics receiver.
 * @param onShowOptions published as `onLongClick`, which is what makes the
 *   context menu reachable without a touch long-press. Describing it in prose
 *   does not.
 */
fun Modifier.appTileSemantics(
    label: String,
    badgeCount: Int = 0,
    isPinned: Boolean = false,
    isHidden: Boolean = false,
    isLocked: Boolean = false,
    positionNote: String = "",
    onShowOptions: (() -> Unit)? = null,
    extraActions: List<CustomAccessibilityAction> = emptyList(),
): Modifier = this.semantics(mergeDescendants = true) {
    contentDescription = appTileDescription(label, badgeCount, isPinned, isHidden, isLocked)
    role = Role.Button
    stateDescription = positionNote
    if (onShowOptions != null) {
        onLongClick("Show options") { onShowOptions(); true }
    }
    if (extraActions.isNotEmpty()) customActions = extraActions
}

/**
 * An interactive app tile with its accessibility contract already satisfied.
 *
 * Prefer this where a tile is being written fresh. Where a call site already has
 * a modifier chain it needs to keep — a drag detector, a displacement
 * `graphicsLayer`, a drop-target border — apply [appTileSemantics] to that chain
 * instead; the contract is the same and it avoids wrapping a tile in a Box whose
 * only purpose is to hold semantics.
 */
@Composable
fun AccessibleAppTile(
    label: String,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    isPinned: Boolean = false,
    isHidden: Boolean = false,
    isLocked: Boolean = false,
    positionNote: String = "",
    onShowOptions: (() -> Unit)? = null,
    extraActions: List<CustomAccessibilityAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.appTileSemantics(
            label = label,
            badgeCount = badgeCount,
            isPinned = isPinned,
            isHidden = isHidden,
            isLocked = isLocked,
            positionNote = positionNote,
            onShowOptions = onShowOptions,
            extraActions = extraActions,
        ),
    ) {
        content()
    }
}
