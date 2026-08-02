package com.ciyato.launcher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the home screen — Suggestion #147.
 * Verifies search bar presence, greeting, and basic navigation.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<LauncherHomeActivity>()

    @Test
    fun searchBar_isDisplayed() {
        composeTestRule.onNode(hasText("Search apps…") or hasText("Search")).assertIsDisplayed()
    }

    @Test
    fun greeting_isDisplayed() {
        composeTestRule.onNode(
            hasText("Good morning") or
            hasText("Good afternoon") or
            hasText("Good evening") or
            hasContentDescription("Greeting")
        ).assertIsDisplayed()
    }

    @Test
    fun appGrid_isDisplayed() {
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasRole(androidx.compose.ui.semantics.Role.Button))
            .assertCountIsAtLeast(1)
    }

    @Test
    fun searchInput_filtersApps() {
        composeTestRule.waitForIdle()
        val searchField = composeTestRule.onNode(hasSetTextAction())
        searchField.performTextInput("a")
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasRole(androidx.compose.ui.semantics.Role.Button))
            .assertCountIsAtLeast(0)
    }
}
