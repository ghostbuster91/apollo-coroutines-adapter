package com.apollographql.apollo.support.coroutines

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloPrefetch
import com.apollographql.apollo.ApolloQueryWatcher
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import kotlin.coroutines.experimental.CoroutineContext

suspend fun <T> ApolloCall<T>.await(): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCompletion { if (continuation.isCancelled) cancel() }

    val callback = object : ApolloCall.Callback<T>() {
        override fun onFailure(e: ApolloException) {
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }

        override fun onResponse(response: Response<T>) {
            if (continuation.isActive) {
                continuation.tryToResume { response.data()!! }
            }
        }
    }
    enqueue(callback)
}

private inline fun <T> CancellableContinuation<T>.tryToResume(function: () -> T) {
    try {
        resume(function())
    } catch (exception: Throwable) {
        resumeWithException(exception)
    }
}

suspend fun ApolloPrefetch.await(): Unit = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCompletion {
        if (continuation.isCancelled) {
            println("Canceling")
            cancel()
        }
    }

    val callback = object : ApolloPrefetch.Callback() {
        override fun onSuccess() {
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }

        override fun onFailure(e: ApolloException) {
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }
    enqueue(callback)
}

suspend fun <T> ApolloQueryWatcher<T>.await(coroutineContext: CoroutineContext): Channel<T> {
    val channel = Channel<T>(Channel.UNLIMITED)
    val callback = object : ApolloCall.Callback<T>() {
        override fun onResponse(response: Response<T>) {
            if (!channel.isClosedForSend) {
                channel.offer(response.data()!!)
            }
        }

        override fun onFailure(e: ApolloException) {
            channel.close(e)
        }
    }
    enqueueAndWatch(callback)
    return channel
}
