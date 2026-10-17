package com.cbrl.pixelblaze.presentation.components

import android.app.RemoteInput
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender

private const val TEXT_KEY = "text"

/**
 * Returns a function that opens the watch keyboard and voice input. [choices] show as quick
 * replies. [onText] gets the text when it is not blank.
 */
@Composable
fun rememberTextInput(
    label: String,
    choices: List<String> = emptyList(),
    onText: (String) -> Unit,
): () -> Unit {
    val currentLabel by rememberUpdatedState(label)
    val currentChoices by rememberUpdatedState(choices)
    val currentOnText by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)?.getCharSequence(TEXT_KEY)?.toString()
        if (!text.isNullOrBlank()) currentOnText(text)
    }
    return remember(launcher) {
        {
            val input = RemoteInput.Builder(TEXT_KEY)
                .setLabel(currentLabel)
                .setChoices(currentChoices.takeIf { it.isNotEmpty() }?.toTypedArray<CharSequence>())
                .wearableExtender {
                    setEmojisAllowed(false)
                    setInputActionType(EditorInfo.IME_ACTION_DONE)
                }
                .build()
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
            launcher.launch(intent)
        }
    }
}
