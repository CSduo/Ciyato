package com.ciyato.launcher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.*

/**
 * AccessibilityHelpers — Suggestion #109
 * Utility modifiers and helpers for TalkBack support and content descriptions.
 */

// appItemSemantics lived here with ZERO call sites and two defects, and is now
// AccessibleAppTile.appTileSemantics instead (F-047).
//
// It appended "Double-tap to open. Long-press for options." to the description.
// TalkBack announces the activation gesture for a Button role itself, so that
// duplicated the instruction - and a long-press described in prose is still not
// REACHABLE by switch access or a keyboard, which is the thing that actually
// matters. It also did not merge descendants, so the label Text inside a tile
// announced separately: "Gmail app, 3 notifications" and then, as a second item,
// "Gmail".
//
// A dead helper is not harmless. The next person to need app-tile semantics
// would have found it, used it, and shipped both defects.

/**
 * Makes an action button announce itself meaningfully to TalkBack.
 */
fun Modifier.actionSemantics(
    label: String,
    stateDescription: String? = null,
): Modifier = this.semantics {
    contentDescription = label
    if (stateDescription != null) {
        this.stateDescription = stateDescription
    }
    role = Role.Button
}

/**
 * Marks a decorative element so TalkBack skips it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.decorative(): Modifier = this.semantics { contentDescription = ""; this.invisibleToUser() }

/**
 * Announces an action to accessibility services without visual change.
 * Use for ephemeral events like "App hidden" or "Settings saved".
 */
fun Modifier.announceAction(announcement: String): Modifier = this.semantics {
    liveRegion = LiveRegionMode.Polite
    contentDescription = announcement
}

/**
 * Marks a group of related elements so TalkBack traverses them together.
 */
fun Modifier.groupAccessibility(description: String): Modifier = this.semantics(mergeDescendants = true) {
    contentDescription = description
}

/**
 * Screen-level heading semantics (e.g., for top-level sections).
 */
fun Modifier.headingSemantics(): Modifier = this.semantics { heading() }
