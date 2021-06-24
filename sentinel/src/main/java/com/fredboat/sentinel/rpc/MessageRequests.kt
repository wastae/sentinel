/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.rpc.meta.SentinelRequest
import com.fredboat.sentinel.util.mono
import com.fredboat.sentinel.util.toJda
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.UserImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
@SentinelRequest
class MessageRequests(private val shardManager: ShardManager) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MessageRequests::class.java)
    }

    @SentinelRequest
    fun consume(request: SendMessageRequest): Mono<SendMessageResponse> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendMessageRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        return channel.sendMessage(request.message).mono("sendMessage")
            .map {
                SendMessageResponse(it.idLong)
            }
    }

    @SentinelRequest
    fun consume(request: SendEmbedRequest): Mono<SendMessageResponse> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendEmbedRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        return channel.sendMessage(request.embed.toJda()).mono("sendEmbed")
            .map {
                SendMessageResponse(it.idLong)
            }
    }

    @SentinelRequest
    fun consume(request: SendPrivateMessageRequest): Mono<SendMessageResponse> {
        val shard = shardManager.shards.find { it.status == JDA.Status.CONNECTED } as JDAImpl
        val user = UserImpl(request.recipient, shard)

        return user.openPrivateChannel().mono("openPrivateChannel")
            .flatMap {
                it.sendMessage(request.message).mono("sendPrivateMessage")
            }.map {
                SendMessageResponse(it.idLong)
            }
    }

    @SentinelRequest
    fun consume(request: EditMessageRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditMessageRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageById(request.messageId, request.message).mono("editMessage")
    }

    @SentinelRequest
    fun consume(request: EditEmbedRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditEmbedRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageById(request.messageId, request.embed.toJda()).mono("editEmbedMessage")
    }

    @SentinelRequest
    fun consume(request: AddReactionRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received AddReactionRequest for channel ${request.channel} which was not found")
            return
        }

        channel.addReactionById(request.messageId, request.emote).mono("addReaction")
    }

    @SentinelRequest
    fun consume(request: AddReactionsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received AddReactionsRequest for channel ${request.channel} which was not found")
            return
        }

        for (emote in request.emote) {
            channel.addReactionById(request.messageId, emote).mono("addReactions")
        }
    }

    @SentinelRequest
    fun consume(request: RemoveReactionRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received RemoveReactionRequest for channel ${request.channel} which was not found")
            return
        }

        shardManager.retrieveUserById(request.userId).mono("retrieveUser")
            .subscribe {
                channel.removeReactionById(request.messageId, request.emote, it).mono("removeReaction")
            }
    }

    @SentinelRequest
    fun consume(request: RemoveReactionsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received RemoveReactionsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.clearReactionsById(request.messageId).mono("clearReactions")
    }

    @SentinelRequest
    fun consume(request: MessageDeleteRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received MessageDeleteRequest for channel ${request.channel} which was not found")
            return
        }

        if (request.messages.size < 2) {
            channel.deleteMessageById(request.messages[0].toString()).mono("deleteMessage")
            return
        }

        val list = request.messages.map { toString() }
        channel.deleteMessagesByIds(list).mono("deleteMessages")
    }

    @SentinelRequest
    fun consume(request: SendTypingRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendTypingRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendTyping().mono("sendTyping")
    }
}
