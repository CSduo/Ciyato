package com.ciyato.launcher.data

import android.content.Context
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * AIOptimizerManager — real, on-device cache cleanup.
 *
 * Retained only for [LauncherViewModel.optimizeSystem], which is compiled but
 * not currently reachable from any screen (its UI was removed as dead weight).
 * Deletes this app's own stale cache files (logs, temp files, and anything
 * larger than 500KB in the private cache directory) and reports the real
 * number of bytes freed. No fabricated metrics, no simulated "AI agents".
 */
class AIOptimizerManager(private val context: Context) {

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing

    private val _freedBytes = MutableStateFlow(0L)
    val freedBytes: StateFlow<Long> = _freedBytes

    /** Deletes stale cache files and reports the real number of bytes freed. */
    suspend fun optimizeSystem(viewModel: LauncherViewModel) {
        _isOptimizing.value = true
        val junkFiles = scanJunkFiles()
        val freed = junkFiles.sumOf { it.length() }
        junkFiles.forEach { file -> runCatching { if (file.exists()) file.delete() } }
        _freedBytes.value = freed
        _isOptimizing.value = false
    }

    private fun scanJunkFiles(): List<File> {
        val list = mutableListOf<File>()
        runCatching {
            context.cacheDir?.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".log") || file.name.endsWith(".tmp") || file.length() > 500 * 1024)) {
                    list.add(file)
                }
            }
        }
        return list
    }
}
