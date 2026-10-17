package com.cbrl.pixelblaze.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Shows [message], with an Undo action when [onUndo] is set. A new message replaces the current one. */
fun CoroutineScope.showMessage(host: SnackbarHostState, message: String, onUndo: (() -> Unit)? = null) {
    launch {
        host.currentSnackbarData?.dismiss()
        val result = host.showSnackbar(
            message = message,
            actionLabel = if (onUndo != null) "Undo" else null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndo?.invoke()
    }
}
