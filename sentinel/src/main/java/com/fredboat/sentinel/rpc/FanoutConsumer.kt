/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.config.SentinelProperties
import com.fredboat.sentinel.entities.FredBoatHello
import com.fredboat.sentinel.entities.SentinelHello
import com.fredboat.sentinel.entities.SyncSessionQueueRequest
import com.fredboat.sentinel.jda.RemoteSessionController
import com.fredboat.sentinel.io.SocketContext
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.sharding.ShardManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FanoutConsumer(
    private val sentinelProperties: SentinelProperties,
    private val key: RoutingKey,
    private val sessionController: RemoteSessionController
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(FanoutConsumer::class.java)

        fun sendHello(context: SocketContext, sentinelProperties: SentinelProperties, key: RoutingKey) {
            val message = sentinelProperties.run { SentinelHello(
                shardStart,
                shardEnd,
                shardCount,
                key.key
            ) }

            context.sendResponse(SentinelHello::class.java.simpleName, context.gson.toJson(message), "0")
        }
    }

    var knownFredBoatId: String? = null
    lateinit var shardManager: ShardManager

    fun onHello(event: FredBoatHello, context: SocketContext) {
        if (event.id != knownFredBoatId) {
            log.info("FredBoat ${event.id} says hello \uD83D\uDC4B - Replaces $knownFredBoatId")
            knownFredBoatId = event.id
            sessionController.syncSessionQueue()
        } else {
            log.info("FredBoat ${event.id} says hello \uD83D\uDC4B")
        }

        sendHello(context, sentinelProperties, key)

        val game = if (event.game.isBlank()) null else Activity.listening(event.game)
        shardManager.shards.forEach {
            if (it.presence.activity?.name != game?.name) {
                it.presence.setPresence(OnlineStatus.ONLINE, game)
            }
        }
    }

    fun consume(request: SyncSessionQueueRequest) {
        sessionController.syncSessionQueue()
    }
}