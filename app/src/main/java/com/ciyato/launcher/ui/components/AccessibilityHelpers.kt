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

/**
 * Adds a semantic role and content description for TalkBack.
 */
fun Modifier.appItemSemantics(
    appLabel: String,
    isPinned: Boolean = false,
    isHidden: Boolean = false,
    badgeCount: Int = 0,
): Modifier = this.semantics {
    val extras = buildString {
        if (isPinned) append(", pinned")
        if (isHidden) append(", hidden")
        if (badgeCount > 0) append(", $badgeCount notification${if (badgeCount > 1) "s" else ""}")
    }
    contentDescription = "$appLabel app$extras. Double-tap to open. Long-press for options."
    role = Role.Button
}

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
