package com.apollographql.apollo.support.coroutines

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.suspendCancellableCoroutine

suspend fun <T> ApolloCall<T>.await() : T  = suspendCancellableCoroutine {  continuation ->
    continuation.invokeOnCompletion { if (continuation.isCancelled) cancel() }

    val callback = object : ApolloCall.Callback<T>() {
        override fun onFailure(e: ApolloException) {
            if(continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }

        override fun onResponse(response: Response<T>) {
            if(continuation.isActive) {
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