/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.GuildSubscribeRequest
import com.fredboat.sentinel.entities.GuildUnsubscribeRequest
import com.fredboat.sentinel.io.SocketContext
import com.fredboat.sentinel.jda.SubscriptionCache
import com.fredboat.sentinel.jda.VoiceServerUpdateCache
import com.fredboat.sentinel.util.execute
import com.fredboat.sentinel.util.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.AudioChannel
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.utils.PermissionUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SubscriptionHandler(
    private val voiceServerUpdateCache: VoiceServerUpdateCache,
    private val subscriptionCache: SubscriptionCache
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SubscriptionHandler::class.java)
    }

    lateinit var shardManager: ShardManager

    suspend fun consume(request: GuildSubscribeRequest, context: SocketContext) {
        val jda = shardManager.getShardById(request.shardId)?.awaitReady()
        if (jda == null) {
            val msg = "Attempt subscribe to ${request.id} guild while JDA instance is null"
            log.error(msg)
            context.sendResponse(Guild::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        val guild = jda.getGuildById(request.id)
        log.info("Request subscribe to $guild received after ${System.currentTimeMillis() - request.requestTime.toLong()}ms")
        if (guild == null) {
            val msg = "Attempt subscribe to unknown guild ${request.id}"
            log.error(msg)
            context.sendResponse(Guild::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        val added = subscriptionCache.add(request.id.toLong())
        if (added) {
            guild.loadMembers().execute(
                Guild::class.java.simpleName,
                request.responseId,
                context
            ).onSuccess {
                sendGuildSubscribeResponse(request, context, guild)
                log.info(
                    StringBuilder()
                        .append("Subscribe to $guild processed after")
                        .append(" ")
                        .append("${System.currentTimeMillis() - request.requestTime.toLong()}ms with Discord")
                        .append(", ")
                        .append("total users cache size ${shardManager.userCache.size()}").toString()
                )
            }
        } else {
            if (subscriptionCache.contains(request.id.toLong())) {
                sendGuildSubscribeResponse(request, context, guild)
                log.info(
                    StringBuilder()
                        .append("Subscribe to $guild when we are already, processed after")
                        .append(" ")
                        .append("${System.currentTimeMillis() - request.requestTime.toLong()}ms")
                        .append(", ")
                        .append("total users cache size ${shardManager.userCache.size()}").toString()
                )
            } else {
                log.error("Subscribe to ${request.id} failed")
                sendGuildSubscribeResponse(request, context, guild)
            }
        }
    }

    fun consume(request: GuildUnsubscribeRequest) {
        val removed = subscriptionCache.remove(request.id.toLong())
        if (removed) {
            val guild = shardManager.getGuildById(request.id)
            if (guild != null) {
                //guild.pruneMemberCache()
                log.info("Request to unsubscribe from ${guild.id} processed, total user cache size ${shardManager.userCache.size()}")
            } else {
                log.warn("Attempt to unsubscribe from ${request.id} while guild is null in JDA")
            }
        } else {
            if (!subscriptionCache.contains(request.id.toLong())) {
                log.warn("Attempt to unsubscribe from ${request.id} while we are not subscribed")
            } else {
                log.error("Failed to unsubscribe from ${request.id}")
            }
        }
    }

    private fun sendGuildSubscribeResponse(request: GuildSubscribeRequest, context: SocketContext, guild: Guild) {
        context.sendResponse(Guild::class.java.simpleName, context.gson.toJson(guild.toEntity(voiceServerUpdateCache)), request.responseId)
    }
}