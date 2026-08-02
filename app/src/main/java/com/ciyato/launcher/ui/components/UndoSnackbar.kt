package com.ciyato.launcher.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.ciyato.launcher.ui.theme.CiyatoGold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * UndoSnackbar — Suggestion #107
 * Generic undo mechanism for destructive operations (hide app, delete, move).
 *
 * Usage:
 *   val undoController = rememberUndoController(snackbarHostState, scope)
 *   undoController.show("App hidden", onUndo = { viewModel.unhideApp(app) })
 */

data class UndoAction(
    val message: String,
    val onUndo: () -> Unit,
    val onCommit: (() -> Unit)? = null,
)

class UndoController(
    private val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    private var pendingAction: UndoAction? = null
    private var commitJob: Job? = null

    fun show(message: String, onUndo: () -> Unit, onCommit: (() -> Unit)? = null) {
        commitJob?.cancel()
        pendingAction?.onCommit?.invoke()

        val action = UndoAction(message, onUndo, onCommit)
        pendingAction = action

        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingAction?.onUndo?.invoke()
                pendingAction = null
            } else {
                pendingAction?.onCommit?.invoke()
                pendingAction = null
            }
        }

        commitJob = scope.launch {
            delay(4_500L)
            if (pendingAction == action) {
                pendingAction?.onCommit?.invoke()
                pendingAction = null
            }
        }
    }
}

@Composable
fun rememberUndoController(
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope = rememberCoroutineScope(),
): UndoController {
    return remember(snackbarHostState, scope) {
        UndoController(snackbarHostState, scope)
    }
}

@Composable
fun CiyatoSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = Color(0xFF1E2128),
            contentColor = Color(0xFFE2E8F0),
            actionColor = CiyatoGold,
            actionContentColor = CiyatoGold,
        )
    }
}
