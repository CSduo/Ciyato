package com.ciyato.launcher.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherInteractionStateTest {

    @Test
    fun `back from confirmation restores the exact previous interaction`() {
        val editor = LauncherInteractionState.LayoutEditing(isControlSheetVisible = false)
        val confirmation = LauncherInteractionState.Confirmation(
            action = LauncherConfirmation.RemoveCategory(
                categoryKey = "WORK",
                isCustom = false,
            ),
            returnState = editor,
        )

        assertEquals(editor, confirmation.afterBack())
    }

    @Test
    fun `back from a category editor returns to its parent mode`() {
        val editor = LauncherInteractionState.CategoryEditor(
            categoryKey = "Personal",
            returnState = LauncherInteractionState.Browsing,
        )

        assertEquals(LauncherInteractionState.Browsing, editor.afterBack())
    }

    @Test
    fun `back from a drag or resize returns to editable layout`() {
        val drag = LauncherInteractionState.Dragging(
            itemKey = "SOCIAL",
            source = DragSource.HOME_CATEGORY,
        )
        val resize = LauncherInteractionState.Resizing(
            categoryKey = "WORK",
            originalSize = "medium",
        )
        val expected = LauncherInteractionState.LayoutEditing(isControlSheetVisible = false)

        assertEquals(expected, drag.afterBack())
        assertEquals(expected, resize.afterBack())
    }

    @Test
    fun `back from a selected app returns to general editing`() {
        assertEquals(
            LauncherInteractionState.LayoutEditing(isControlSheetVisible = false),
            LauncherInteractionState.ItemSelected("com.example.app").afterBack(),
        )
    }
}
