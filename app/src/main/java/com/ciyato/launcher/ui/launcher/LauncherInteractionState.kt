package com.ciyato.launcher.ui.launcher

/**
 * The launcher has mutually exclusive interaction modes. Keeping the active mode explicit
 * prevents an edit gesture, a modal confirmation, and Android navigation from leaving Home
 * in contradictory local states.
 */
sealed interface LauncherInteractionState {
    val isEditing: Boolean

    data object Browsing : LauncherInteractionState {
        override val isEditing = false
    }

    data class LayoutEditing(
        val isControlSheetVisible: Boolean,
    ) : LauncherInteractionState {
        override val isEditing = true
    }

    data class ItemSelected(
        val packageName: String,
        val returnState: LayoutEditing = LayoutEditing(isControlSheetVisible = false),
    ) : LauncherInteractionState {
        override val isEditing = true
    }

    data class CategoryEditor(
        val categoryKey: String,
        val returnState: LauncherInteractionState,
    ) : LauncherInteractionState {
        override val isEditing = returnState.isEditing
    }

    data class Dragging(
        val itemKey: String,
        val source: DragSource,
    ) : LauncherInteractionState {
        override val isEditing = true
    }

    data class Resizing(
        val categoryKey: String,
        val originalSize: String,
    ) : LauncherInteractionState {
        override val isEditing = true
    }

    data class Confirmation(
        val action: LauncherConfirmation,
        val returnState: LauncherInteractionState,
    ) : LauncherInteractionState {
        override val isEditing = returnState.isEditing
    }
}

enum class DragSource {
    HOME_CATEGORY,
    WORKSPACE_CATEGORY,
    WORKSPACE_APP,
}

sealed interface LauncherConfirmation {
    data class RemoveCategory(
        val categoryKey: String,
        val isCustom: Boolean,
        val workspaceIndex: Int? = null,
    ) : LauncherConfirmation
}

fun LauncherInteractionState.afterBack(): LauncherInteractionState = when (this) {
    is LauncherInteractionState.Confirmation -> returnState
    is LauncherInteractionState.CategoryEditor -> returnState
    is LauncherInteractionState.Dragging,
    is LauncherInteractionState.Resizing -> LauncherInteractionState.LayoutEditing(isControlSheetVisible = false)
    is LauncherInteractionState.ItemSelected -> returnState
    is LauncherInteractionState.LayoutEditing,
    LauncherInteractionState.Browsing -> LauncherInteractionState.Browsing
}
