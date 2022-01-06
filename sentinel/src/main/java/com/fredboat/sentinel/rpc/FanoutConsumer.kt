/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.corundumstudio.socketio.SocketIOClient
import com.corundumstudio.socketio.SocketIOServer
import com.fredboat.sentinel.SocketServer
import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.config.SentinelProperties
import com.fredboat.sentinel.entities.FredBoatHello
import com.fredboat.sentinel.entities.SentinelHello
import com.fredboat.sentinel.entities.SyncSessionQueueRequest
import com.fredboat.sentinel.jda.RemoteSessionController
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.sharding.ShardManager
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration

@Configuration
class FanoutConsumer(
        private val sentinelProperties: SentinelProperties,
        private val key: RoutingKey,
        private val shardManager: ShardManager,
        private val sessionController: RemoteSessionController,
        socketIOServer: SocketIOServer
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(FanoutConsumer::class.java)
    }

    var knownFredBoatId: String? = null

    init {
        socketIOServer.addConnectListener {
            sendHello(it)
        }
        socketIOServer.addEventListener("fredBoatHello", JSONObject::class.java) { client, event, _ ->
            onHello(SocketServer.gson.fromJson(event.toString(), FredBoatHello::class.java), client)
        }
        socketIOServer.addEventListener("syncSessionQueueRequest", JSONObject::class.java) { _, event, _ ->
            consume(SocketServer.gson.fromJson(event.toString(), SyncSessionQueueRequest::class.java))
        }
        log.info("FanoutConsumer events registered")
    }

    fun onHello(event: FredBoatHello, client: SocketIOClient) {
        if (event.id != knownFredBoatId) {
            log.info("FredBoat ${event.id} says hello \uD83D\uDC4B - Replaces $knownFredBoatId")
            knownFredBoatId = event.id
            sessionController.syncSessionQueue()
        } else {
            log.info("FredBoat ${event.id} says hello \uD83D\uDC4B")
        }

        sendHello(client)

        val game = if (event.game.isBlank()) null else Activity.listening(event.game)
        shardManager.shards.forEach {
            if (it.presence.activity?.name != game?.name) {
                it.presence.setPresence(OnlineStatus.ONLINE, game)
            }
        }
    }

    private fun sendHello(client: SocketIOClient) {
        val message = sentinelProperties.run {  SentinelHello(
                shardStart,
                shardEnd,
                shardCount,
                key.key
        )}
        client.sendEvent("sentinelHello", message)
    }

    fun consume(request: SyncSessionQueueRequest) {
        sessionController.syncSessionQueue()
    }
}