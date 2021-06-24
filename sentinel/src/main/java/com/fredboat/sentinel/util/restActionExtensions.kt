/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.util

import com.fredboat.sentinel.metrics.Counters
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.RestAction
import java.util.concurrent.TimeUnit

fun <T> RestAction<T>.queue(name: String) = toFuture(name)
fun <T> RestAction<T>.complete(name: String): T = toFuture(name).get(30, TimeUnit.SECONDS)

fun <T> RestAction<T>.toFuture(name: String) = submit().whenComplete { _, t ->
    if (t == null) {
        Counters.successfulRestActions.labels(name).inc()
        return@whenComplete
    }
    val errCode = (t as? ErrorResponseException)?.errorCode?.toString() ?: "none"
    Counters.failedRestActions.labels(name, errCode).inc()
}.toCompletableFuture()!!