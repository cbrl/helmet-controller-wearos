package com.cbrl.pixelblaze.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface AsyncResult<out T> {
    data class Success<T>(val data: T) : AsyncResult<T>
    data class Error(val exception: Throwable? = null) : AsyncResult<Nothing>
    data object Loading : AsyncResult<Nothing>
}

fun <T, R> AsyncResult<T>.mapSuccess(transform: (T) -> R): AsyncResult<R> {
    return when (this) {
        is AsyncResult.Loading -> AsyncResult.Loading
        is AsyncResult.Success -> AsyncResult.Success(transform(data))
        is AsyncResult.Error -> AsyncResult.Error(exception)
    }
}

fun <T> Flow<T>.asAsyncResult(): Flow<AsyncResult<T>> {
    return this
        .map<T, AsyncResult<T>> {
            AsyncResult.Success(it)
        }
        .onStart { emit(AsyncResult.Loading) }
        .catch { emit(AsyncResult.Error(it)) }
}

@Composable
fun <T> AsyncResultHandler(
    asyncResult: AsyncResult<T>,
    onError: @Composable (String) -> Unit = { message ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = message,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    },
    onLoading: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    },
    onSuccess: @Composable (T) -> Unit
) {
    when (asyncResult) {
        is AsyncResult.Loading -> onLoading()
        is AsyncResult.Success -> onSuccess(asyncResult.data)
        is AsyncResult.Error -> onError(asyncResult.exception?.message ?: "Unknown error")
    }
}
