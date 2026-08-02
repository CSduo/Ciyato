package com.ciyato.launcher

import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.AppCategorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppCategorizerTest {

    @Test
    fun `known social apps use deterministic mappings`() {
        val socialApps = listOf(
            "com.instagram.android" to "Instagram",
            "com.twitter.android" to "Twitter",
            "com.facebook.katana" to "Facebook",
        )

        socialApps.forEach { (packageName, label) ->
            assertEquals(
                "Expected SOCIAL for $packageName",
                AppCategory.SOCIAL,
                AppCategorizer.categorize(packageName, label),
            )
        }
    }

    @Test
    fun `messaging apps use communication or social primary categories`() {
        val categories = listOf(
            AppCategorizer.categorize("org.telegram.messenger", "Telegram"),
            AppCategorizer.categorize("com.whatsapp", "WhatsApp"),
        )
        categories.forEach {
            check(it == AppCategory.COMMUNICATION || it == AppCategory.SOCIAL)
        }
    }

    @Test
    fun `known work apps use deterministic mappings`() {
        val workApps = listOf(
            "com.google.android.apps.docs" to "Google Docs",
            "com.microsoft.office.word" to "Microsoft Word",
            "com.slack" to "Slack",
            "com.zoom.videomeetings" to "Zoom",
        )

        workApps.forEach { (packageName, label) ->
            assertEquals(
                "Expected WORK for $packageName",
                AppCategory.WORK,
                AppCategorizer.categorize(packageName, label),
            )
        }
    }

    @Test
    fun `media apps map to entertainment`() {
        val entertainmentApps = listOf(
            "com.spotify.music" to "Spotify",
            "com.netflix.mediaclient" to "Netflix",
            "com.google.android.youtube" to "YouTube",
        )

        entertainmentApps.forEach { (packageName, label) ->
            assertEquals(
                "Expected ENTERTAINMENT for $packageName",
                AppCategory.ENTERTAINMENT,
                AppCategorizer.categorize(packageName, label),
            )
        }
    }

    @Test
    fun `AI assistants use their own category`() {
        assertEquals(
            AppCategory.AI,
            AppCategorizer.categorize("com.openai.chatgpt", "ChatGPT"),
        )
        assertEquals(
            AppCategory.AI,
            AppCategorizer.categorize("com.example.assistant", "AI Assistant"),
        )
    }

    @Test
    fun `store contacts and video editors avoid entertainment`() {
        assertEquals(AppCategory.UTILITIES, AppCategorizer.categorize("com.android.vending", "Play Store"))
        assertEquals(AppCategory.CONTACTS, AppCategorizer.categorize("com.google.android.contacts", "Contacts"))
        assertEquals(AppCategory.VIDEO_EDITING, AppCategorizer.categorize("com.example.editor", "AI Video Enhancer"))
    }

    @Test
    fun `query intent detects supported categories`() {
        assertEquals(AppCategory.ENTERTAINMENT, AppCategorizer.detectQueryIntent("open a music app"))
        assertEquals(AppCategory.COMMUNICATION, AppCategorizer.detectQueryIntent("send a message"))
        assertEquals(AppCategory.FINANCE, AppCategorizer.detectQueryIntent("show payment apps"))
        assertEquals(AppCategory.AI, AppCategorizer.detectQueryIntent("open an AI app"))
        assertEquals(AppCategory.VIDEO_EDITING, AppCategorizer.detectQueryIntent("open a video editor"))
    }

    @Test
    fun `blank query has no category intent`() {
        assertNull(AppCategorizer.detectQueryIntent(""))
    }

    @Test
    fun `unknown and blank apps still receive a safe category`() {
        assertNotNull(AppCategorizer.categorize("com.totally.unknown.app", "Random App"))
        assertNotNull(AppCategorizer.categorize("", ""))
    }

    @Test
    fun `games use the games category`() {
        assertEquals(
            AppCategory.GAMES,
            AppCategorizer.categorize("com.supercell.clashofclans", "Clash of Clans"),
        )
    }
}
