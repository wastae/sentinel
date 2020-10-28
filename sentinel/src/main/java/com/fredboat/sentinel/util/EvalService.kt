/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.util

import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.config.SentinelProperties
import net.dv8tion.jda.api.sharding.ShardManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.function.Supplier
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

@Service
class EvalService(
        private val key: RoutingKey,
        private val shards: ShardManager,
        private val sentinelProps: SentinelProperties,
        private val springContext: Supplier<ApplicationContext>
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(EvalService::class.java)
    }

    fun evalScript(source: String, timeout: Int?): Mono<String> {
        val mono = createMono(source).onErrorResume { ex ->
            log.info("Error occurred in eval", ex)
            Mono.just(String.format("`%s`", ex.message))
        }

        timeout ?: return mono

        return mono.timeout(
                Duration.ofSeconds(timeout.toLong()),

                // Fallback on timeout
                Mono.just("Task exceeded time limit of $timeout seconds.")
        )
    }

    private fun createMono(source: String): Mono<String> = Mono.create { sink ->
        val engine: ScriptEngine = ScriptEngineManager().getEngineByExtension("kts")

        engine.put("key", key.key)
        engine.put("shards", shards)
        engine.put("sentinelProps", sentinelProps)
        engine.put("spring", springContext.get())

        val out = engine.eval(source)

        val outputS = when {
            out == null -> ":ok_hand::skin-tone-3:"
            out.toString().contains("\n") -> "Eval: ```\n$out```"
            else -> "Eval: `$out`"
        }
        sink.success(outputS)
    }
}