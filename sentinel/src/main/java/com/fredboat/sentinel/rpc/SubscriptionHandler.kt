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
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.*
import net.dv8tion.jda.internal.handle.EventCache
import net.dv8tion.jda.internal.utils.cache.SnowflakeCacheViewImpl
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
            val jda = shardManager.getShardById(request.shardId)?.awaitReady()
            if (jda == null) {
                log.warn("Attempt to subscribe to ${request.id} guild while JDA instance is null")
                return@runAsync
            }

            val guild = jda.getGuildById(request.id)
            log.info(
                "Request to subscribe to $guild received after " +
                        "${System.currentTimeMillis() - request.requestTime.toLong()}ms"
            )
            if (guild == null) {
                log.warn("Attempt to subscribe to unknown guild ${request.id}")
                return@runAsync
            }

            val added = SocketServer.subscriptionsCache.add(request.id.toLong())
            if (added) {
                guild.loadMembers().onSuccess {
                    sendGuildSubscribeResponse(request, client, guild)
                    log.info(StringBuilder()
                        .append("Request to subscribe to $guild processed after")
                        .append(" ")
                        .append("${System.currentTimeMillis() - request.requestTime.toLong()}ms with Discord")
                        .append(", ")
                        .append("total user cache size ${shardManager.userCache.size()}").toString()
                    )
                }
            } else {
                if (SocketServer.subscriptionsCache.contains(request.id.toLong())) {
                    sendGuildSubscribeResponse(request, client, guild)
                    log.info(StringBuilder()
                        .append("Request to subscribe to $guild when we are already, processed after")
                        .append(" ")
                        .append("${System.currentTimeMillis() - request.requestTime.toLong()}ms")
                        .append(", ")
                        .append("total user cache size ${shardManager.userCache.size()}").toString()
                    )
                } else {
                    log.error("Failed to subscribe to ${request.id}")
                    sendGuildSubscribeResponse(request, client, guild)
                }
            }
        }
    }

    fun consume(request: GuildUnsubscribeRequest) {
        val removed = SocketServer.subscriptionsCache.remove(request.id.toLong())
        if (removed) {
            val guild = shardManager.getGuildById(request.id)
            if (guild != null) {
                invalidateGuildCache(guild)
                log.info("Request to unsubscribe from ${guild.id} processed, total user cache size ${shardManager.userCache.size()}")
            } else {
                log.warn("Attempt to unsubscribe from ${request.id} while guild is null in JDA")
            }
        } else {
            if (!SocketServer.subscriptionsCache.contains(request.id.toLong())) {
                log.warn("Attempt to unsubscribe from ${request.id} while we are not subscribed")
            } else {
                log.error("Failed to unsubscribe from ${request.id}")
            }
        }
    }

    private fun sendGuildSubscribeResponse(request: GuildSubscribeRequest, client: SocketIOClient, guild: Guild) {
        client.sendEvent("guild-${request.responseId}", guild.toEntity(voiceServerUpdateCache))
    }

    private fun invalidateGuildCache(guild: Guild) {
        val membersView = (guild as GuildImpl).membersView
        membersView.writeLock().use {
            membersView.map.forEachEntry { id, member ->
                if (id != guild.jda.selfUser.idLong) {
                    updateMemberCache(member as MemberImpl, guild.jda, true)
                }; true
            }
        }
    }

    fun updateMemberCache(member: MemberImpl, jda: JDAImpl, forceRemove: Boolean): Boolean {
        val guild = member.guild
        val user = member.user as UserImpl
        val membersView = guild.membersView
        if (forceRemove) {
            if (membersView.remove(member.idLong) == null) return false
            log.debug("Unloading member {}", member)
            if (user.mutualGuilds.isEmpty()) {
                user.setFake(true)
                jda.usersView.remove(user.idLong)
            }
            val voiceState = member.voiceState as GuildVoiceStateImpl?
            if (voiceState != null) {
                val connectedChannel = voiceState.channel as VoiceChannelImpl?
                connectedChannel?.connectedMembersMap?.remove(member.idLong)
                voiceState.setConnectedChannel(null)
            }

            return false
        } else if (guild.getMemberById(member.idLong) != null) {
            return true
        }

        log.debug("Loading member {}", member)

        if (jda.getUserById(user.idLong) == null) {
            val usersView: SnowflakeCacheViewImpl<User> = jda.usersView
            usersView.writeLock().use { usersView.map.put(user.idLong, user) }
        }

        membersView.writeLock().use {
            membersView.map.put(member.idLong, member)
            if (member.isOwner) guild.owner = member
        }

        val hashId = guild.idLong xor user.idLong
        jda.eventCache.playbackCache(EventCache.Type.USER, member.idLong)
        jda.eventCache.playbackCache(EventCache.Type.MEMBER, hashId)
        return true
    }
}