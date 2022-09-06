package com.fredboat.sentinel.util

import com.fredboat.sentinel.io.SocketContext
import com.fredboat.sentinel.metrics.Counters
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.concurrent.Task
import java.util.concurrent.CompletableFuture

fun <T> RestAction<T>.execute(
    name: String,
    responseId: String,
    context: SocketContext
): CompletableFuture<T> = this.submit().whenCompleteAsync { _, throwable ->
    if (throwable == null) {
        Counters.successfulRestActions.labels(name).inc()
    } else {
        val errCode = (throwable as? ErrorResponseException)?.errorCode?.toString() ?: "none"
        Counters.failedRestActions.labels(name, errCode).inc()
        context.sendResponse(
            name,
            throwable.stackTraceToString(),
            responseId,
            jsonResponse = false,
            successful = false
        )
    }
}

fun <T> Task<T>.execute(
    name: String,
    responseId: String,
    context: SocketContext
): Task<T> = this.onSuccess {
    Counters.successfulRestActions.labels(name).inc()
}.onError { throwable ->
    val errCode = (throwable as? ErrorResponseException)?.errorCode?.toString() ?: "none"
    Counters.failedRestActions.labels(name, errCode).inc()
    context.sendResponse(
        name,
        throwable.stackTraceToString(),
        responseId,
        jsonResponse = false,
        successful = false
    )
}
