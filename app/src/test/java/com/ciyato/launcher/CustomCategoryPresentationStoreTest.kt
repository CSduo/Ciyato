package com.ciyato.launcher

import com.ciyato.launcher.data.CustomCategoryPresentation
import com.ciyato.launcher.data.CustomCategoryPresentationStore
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomCategoryPresentationStoreTest {

    @Test
    fun `legacy custom category defaults to compact group`() {
        assertEquals(
            CustomCategoryPresentation.GROUP,
            CustomCategoryPresentationStore.presentationFor("{}", "Reading"),
        )
    }

    @Test
    fun `presentation persists through rename and removes only deleted key`() {
        val initial = CustomCategoryPresentationStore.update(
            "{}",
            "Reading",
            CustomCategoryPresentation.CARD,
        )
        val renamed = CustomCategoryPresentationStore.rename(initial, "Reading", "Books")

        assertEquals(
            CustomCategoryPresentation.CARD,
            CustomCategoryPresentationStore.presentationFor(renamed, "Books"),
        )
        assertEquals(
            CustomCategoryPresentation.GROUP,
            CustomCategoryPresentationStore.presentationFor(renamed, "Reading"),
        )
        assertEquals(
            CustomCategoryPresentation.GROUP,
            CustomCategoryPresentationStore.presentationFor(
                CustomCategoryPresentationStore.remove(renamed, "Books"),
                "Books",
            ),
        )
    }
}
