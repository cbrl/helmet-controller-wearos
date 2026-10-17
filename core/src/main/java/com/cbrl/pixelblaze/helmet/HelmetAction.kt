package com.cbrl.pixelblaze.helmet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A helmet change that starts outside the app screens, for example from the watch tile. */
sealed interface HelmetAction {
    data class SetBlackout(val enabled: Boolean) : HelmetAction

    data class LoadPreset(val id: String) : HelmetAction

    /** The action as a short text, for an intent extra. [fromToken] reads it. */
    fun toToken(): String = when (this) {
        is SetBlackout -> if (enabled) BLACKOUT_ON else BLACKOUT_OFF
        is LoadPreset -> PRESET_PREFIX + id
    }

    companion object {
        private const val BLACKOUT_ON = "blackout:on"
        private const val BLACKOUT_OFF = "blackout:off"
        private const val PRESET_PREFIX = "preset:"

        fun fromToken(token: String?): HelmetAction? = when {
            token == BLACKOUT_ON -> SetBlackout(true)
            token == BLACKOUT_OFF -> SetBlackout(false)
            token != null && token.startsWith(PRESET_PREFIX) ->
                token.removePrefix(PRESET_PREFIX).takeIf(String::isNotBlank)?.let(::LoadPreset)
            else -> null
        }
    }
}

/** The result of a [HelmetAction]. [message] is a short text for a confirmation. */
data class ActionResult(val action: HelmetAction, val success: Boolean, val message: String)

/**
 * Runs one [HelmetAction] when the helmet is ready.
 *
 * A new action replaces an action that still waits. An action fails when the helmet is not ready
 * within [timeoutMillis].
 */
class ActionQueue(
    private val scope: CoroutineScope,
    private val ready: Flow<Boolean>,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
    private val perform: (HelmetAction) -> ActionResult,
) {
    companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val NOT_READY_MESSAGE = "Helmet offline"
    }

    private val _waiting = MutableStateFlow<HelmetAction?>(null)

    /** The action that waits for the helmet, or null. */
    val waiting: StateFlow<HelmetAction?> = _waiting

    private val _result = MutableStateFlow<ActionResult?>(null)

    /** The result of the last action, until [clearResult]. */
    val result: StateFlow<ActionResult?> = _result

    private var job: Job? = null

    fun submit(action: HelmetAction) {
        job?.cancel()
        _waiting.value = action
        _result.value = null
        job = scope.launch {
            val isReady = withTimeoutOrNull(timeoutMillis) { ready.first { it } } != null
            _waiting.value = null
            _result.value = if (isReady) perform(action) else ActionResult(action, false, NOT_READY_MESSAGE)
        }
    }

    fun clearResult() {
        _result.value = null
    }
}
