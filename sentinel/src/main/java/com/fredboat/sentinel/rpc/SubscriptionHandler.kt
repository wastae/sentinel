/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.corundumstudio.socketio.SocketIOClient
import com.fredboat.sentinel.SocketServer
import com.fredboat.sentinel.entities.GuildSubscribeRequest
import com.fredboat.sentinel.entities.GuildUnsubscribeRequest
import com.fredboat.sentinel.jda.VoiceServerUpdateCache
import com.fredboat.sentinel.util.toEntity
import net.dv8tion.jda.api.sharding.ShardManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class SubscriptionHandler(
    private val shardManager: ShardManager,
    private val voiceServerUpdateCache: VoiceServerUpdateCache
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SubscriptionHandler::class.java)
    }

    fun consume(request: GuildSubscribeRequest, client: SocketIOClient) {
        CompletableFuture.runAsync {
            val guild = shardManager.getShardById(request.shardId)?.awaitReady()?.getGuildById(request.id)
            log.info(
                "Request to subscribe to {} received after {}ms",
                guild,
                System.currentTimeMillis() - request.requestTime.toLong()
            )

            if (guild == null) {
                log.warn("Attempt to subscribe to unknown guild ${request.id}")
                return@runAsync
            }

            val added = SocketServer.subscriptionsCache.add(request.id.toLong())
            if (added) {
                guild.loadMembers().onSuccess { log.info("Successfully loaded ${it.size} members for $guild") }.get()
            } else {
                if (SocketServer.subscriptionsCache.contains(request.id.toLong())) {
                    log.warn("Attempt to subscribe ${request.id} while we are already subscribed")
                } else {
                    log.error("Failed to subscribe to ${request.id}")
                }
            }

            val entity = guild.toEntity(voiceServerUpdateCache)
            log.info(
                "Request to subscribe to {} processed after {}ms",
                guild,
                System.currentTimeMillis() - request.requestTime.toLong()
            )
            client.sendEvent("guild-${request.responseId}", entity)
        }
    }

    fun consume(request: GuildUnsubscribeRequest) {
        val removed = SocketServer.subscriptionsCache.remove(request.id.toLong())
        if (!removed) {
            if (!SocketServer.subscriptionsCache.contains(request.id.toLong())) {
                log.warn("Attempt to unsubscribe ${request.id} while we are not subscribed")
            } else {
                log.error("Failed to unsubscribe from ${request.id}")
            }
        }
    }
}
