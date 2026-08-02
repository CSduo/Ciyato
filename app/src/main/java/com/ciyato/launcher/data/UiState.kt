package com.ciyato.launcher.data

/**
 * Generic sealed UI state wrapper — Suggestion #103.
 * Replaces ad-hoc isLoading/error booleans across all screens.
 *
 * Usage:
 *   val appsState: StateFlow<UiState<List<InstalledApp>>> = ...
 *   when (val s = appsState.collectAsState().value) {
 *     is UiState.Loading -> ...
 *     is UiState.Success -> s.data
 *     is UiState.Error   -> s.message
 *     is UiState.Empty   -> ...
 *   }
 */
sealed class UiState<out T> {
    /** Data is being fetched. [partial] optionally holds stale data to show while loading. */
    data class Loading<T>(val partial: T? = null) : UiState<T>()

    /** Successful result. */
    data class Success<T>(val data: T) : UiState<T>()

    /** An error occurred. [retryable] indicates whether the UI should show a Retry button. */
    data class Error(val message: String, val retryable: Boolean = true) : UiState<Nothing>()

    /** Success but result is empty (e.g. no apps, no files). */
    data class Empty(val hint: String = "Nothing here yet") : UiState<Nothing>()

    // ── Helpers ───────────────────────────────────────────────────────────────

    val isLoading get() = this is Loading
    val isSuccess get() = this is Success
    val isError   get() = this is Error
    val isEmpty   get() = this is Empty

    fun dataOrNull(): T? = if (this is Success) data else null

    /** Map a Success value; other states pass through unchanged. */
    fun <R> mapSuccess(transform: (T) -> R): UiState<R> = when (this) {
        is Loading -> Loading(partial?.let { transform(it) })
        is Success -> Success(transform(data))
        is Error   -> this
        is Empty   -> this
    }
}

/** One-shot event wrapper to prevent re-consumption from StateFlow (Suggestion #104). */
class Event<out T>(private val content: T) {
    private var consumed = false
    fun consume(): T? = if (!consumed) { consumed = true; content } else null
    val isConsumed get() = consumed
}
