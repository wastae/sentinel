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
import reactor.core.publisher.Mono

fun <T> RestAction<T>.mono(name: String): Mono<T> = Mono.create { sink ->
    this.queue({ result ->
        Counters.successfulRestActions.labels(name).inc()
        sink.success(result)
    }, { t ->
        val errCode = (t as? ErrorResponseException)?.errorCode?.toString() ?: "none"
        Counters.failedRestActions.labels(name, errCode).inc()
        sink.error(t)
    })
}