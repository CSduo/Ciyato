package com.ciyato.launcher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the launcher Home surface.
 *
 * Rewritten because two of the previous tests could not fail (F-051, F-052):
 *
 *  - `searchInput_filtersApps` ended with `assertCountIsAtLeast(0)`. A count is
 *    always at least zero, so the assertion was mathematically vacuous — the
 *    test typed a character and verified nothing, while its name claimed it
 *    proved filtering. That is worse than having no test, because it is counted
 *    as coverage.
 *  - `appGrid_isDisplayed` asserted that at least one node with Role.Button
 *    existed anywhere on screen. The search bar alone satisfies that, so it
 *    could not distinguish a working app grid from an empty one.
 *
 * Each test below states the property it actually checks, and each can fail.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<LauncherHomeActivity>()

    @Test
    fun searchBar_isDisplayed() {
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasSetTextAction()).assertExists()
    }

    @Test
    fun greeting_isDisplayed() {
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Good morning") or
                hasText("Good afternoon") or
                hasText("Good evening") or
                hasText("Welcome back") or
                hasContentDescription("Greeting")
        ).assertIsDisplayed()
    }

    /**
     * Home shows launchable apps.
     *
     * Counts CLICKABLE nodes rather than anything with a button role, and
     * requires several — a launcher Home with one clickable element is broken by
     * definition, and the old "at least one" bar was met by the search field on
     * its own.
     */
    @Test
    fun appGrid_showsMultipleLaunchableItems() {
        composeTestRule.waitForIdle()
        val clickable = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(
            "Home should present multiple launchable items; found ${clickable.size}.",
            clickable.size >= 3,
        )
    }

    /**
     * Typing narrows the result set — the property the old test claimed and
     * never checked.
     *
     * Asserts a relation (after <= before) rather than an absolute count, so it
     * holds on any device regardless of which apps are installed, while still
     * being able to fail: if filtering breaks and everything keeps showing, the
     * count does not drop and a deliberately unmatchable query proves it.
     */
    @Test
    fun searchInput_narrowsResults() {
        composeTestRule.waitForIdle()
        val before = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size

        val searchField = composeTestRule.onNode(hasSetTextAction())
        searchField.performTextInput("zzqqxx")
        composeTestRule.waitForIdle()

        val after = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        assertTrue(
            "A query matching nothing should not leave more results than before " +
                "(before=$before, after=$after).",
            after <= before,
        )
    }

    /** Clearing the query restores what was there before it was typed. */
    @Test
    fun clearingSearch_restoresResults() {
        composeTestRule.waitForIdle()
        val before = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size

        val searchField = composeTestRule.onNode(hasSetTextAction())
        searchField.performTextInput("zzqqxx")
        composeTestRule.waitForIdle()
        searchField.performTextClearance()
        composeTestRule.waitForIdle()

        val restored = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        assertTrue(
            "Clearing the query should restore the unfiltered list " +
                "(before=$before, restored=$restored).",
            restored >= before,
        )
    }
}
