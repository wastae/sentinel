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
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.sharding.ShardManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SubscriptionHandler(
    private val shardManager: ShardManager,
    private val voiceServerUpdateCache: VoiceServerUpdateCache
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SubscriptionHandler::class.java)
    }

    fun consume(request: GuildSubscribeRequest, client: SocketIOClient) {
        val jda = shardManager.getShardById(request.shardId)?.awaitReady()
        if (jda == null) {
            log.warn("Attempt subscribe to ${request.id} guild while JDA instance is null")
            return
        }

        val guild = jda.getGuildById(request.id)
        log.info(
            "Request subscribe to $guild received after " +
                    "${System.currentTimeMillis() - request.requestTime.toLong()}ms"
        )
        if (guild == null) {
            log.warn("Attempt subscribe to unknown guild ${request.id}")
            return
        }

        val added = SocketServer.subscriptionsCache.add(request.id.toLong())
        if (added) {
            guild.loadMembers().onSuccess {
                sendGuildSubscribeResponse(request, client, guild)
                log.info(StringBuilder()
                    .append("Subscribe to $guild processed after")
                    .append(" ")
                    .append("${System.currentTimeMillis() - request.requestTime.toLong()}ms with Discord")
                    .append(", ")
                    .append("total users cache size ${shardManager.userCache.size()}").toString()
                )
            }
        } else {
            if (SocketServer.subscriptionsCache.contains(request.id.toLong())) {
                sendGuildSubscribeResponse(request, client, guild)
                log.info(StringBuilder()
                    .append("Subscribe to $guild when we are already, processed after")
                    .append(" ")
                    .append("${System.currentTimeMillis() - request.requestTime.toLong()}ms")
                    .append(", ")
                    .append("total users cache size ${shardManager.userCache.size()}").toString()
                )
            } else {
                log.error("Subscribe to ${request.id} failed")
                sendGuildSubscribeResponse(request, client, guild)
            }
        }
    }

    fun consume(request: GuildUnsubscribeRequest) {
        val removed = SocketServer.subscriptionsCache.remove(request.id.toLong())
        if (removed) {
            val guild = shardManager.getGuildById(request.id)
            if (guild != null) {
                guild.pruneMemberCache()
                log.info("Unsubscribe from $guild, total users cache size ${shardManager.userCache.size()}")
            } else {
                log.warn("Attempt unsubscribe from ${request.id} while guild is null in JDA")
            }
        } else {
            if (!SocketServer.subscriptionsCache.contains(request.id.toLong())) {
                log.warn("Attempt unsubscribe from ${request.id} while we are not subscribed")
            } else {
                log.error("Unsubscribe from ${request.id} failed")
            }
        }
    }

    private fun sendGuildSubscribeResponse(request: GuildSubscribeRequest, client: SocketIOClient, guild: Guild) {
        client.sendEvent("guild-${request.responseId}", guild.toEntity(voiceServerUpdateCache))
    }
}