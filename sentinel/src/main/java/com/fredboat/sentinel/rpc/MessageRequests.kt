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
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.UserImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono

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

        return channel.sendMessage(request.message)
                .mono("sendMessage")
                .map { SendMessageResponse(it.idLong) }
    }

    @SentinelRequest
    fun consume(request: SendEmbedRequest): Mono<SendMessageResponse> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendEmbedRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        return channel.sendMessage(request.embed.toJda()).mono("sendEmbed").map {
            SendMessageResponse(it.idLong)
        }
    }

    @SentinelRequest
    fun consume(request: SendPrivateMessageRequest): Mono<SendMessageResponse> {
        val shard = shardManager.shards.find { it.status == JDA.Status.CONNECTED } as JDAImpl
        val user = UserImpl(request.recipient, shard)

        return user.openPrivateChannel()
            .mono("openPrivateChannel")
            .flatMap { it.sendMessage(request.message).mono("sendPrivateMessage") }
            .map { SendMessageResponse(it.idLong) }
    }

    @SentinelRequest
    fun consume(request: EditMessageRequest): Mono<Void> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditMessageRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        return channel.editMessageById(request.messageId, request.message).mono("editMessage").then()
    }

    @SentinelRequest
    fun consume(request: EditEmbedRequest): Mono<EditEmbedRequest> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditEmbedRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        return channel.editMessageById(request.messageId, request.embed.toJda())
            .mono("editEmbedMessage")
            .map { EditEmbedRequest(request.channel, request.messageId, request.embed) }
    }

    @SentinelRequest
    fun consume(request: AddReactionRequest): Mono<AddReactionRequest> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received AddReactionRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        return channel.addReactionById(request.messageId, request.emote)
            .mono("addReaction")
            .map { AddReactionRequest(request.channel, request.messageId, request.emote) }
    }

    @SentinelRequest
    fun consume(request: AddReactionsRequest): Mono<AddReactionsRequest> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received AddReactionsRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        for (emote in request.emote) {
            channel.addReactionById(request.messageId, emote).queue()
        }

        return Mono.empty()
    }

    @SentinelRequest
    fun consume(request: RemoveReactionRequest): Mono<RemoveReactionRequest> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received RemoveReactionRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        return shardManager.retrieveUserById(request.userId)
            .mono("removeReaction")
            .onErrorContinue { t, _ ->
                t is ErrorResponseException && t.errorResponse == ErrorResponse.UNKNOWN_USER
            }
            .flatMap { user -> channel.removeReactionById(request.messageId, request.emote, user).mono("removeReaction") }
            .map { RemoveReactionRequest(channel.idLong, request.messageId, request.userId, request.emote) }
    }

    @SentinelRequest
    fun consume(request: RemoveReactionsRequest): Mono<RemoveReactionsRequest> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received RemoveReactionsRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        return channel.clearReactionsById(request.messageId)
            .mono("removeReactions")
            .map { RemoveReactionsRequest(channel.idLong, request.messageId) }
    }

    @SentinelRequest
    fun consume(request: MessageDeleteRequest): Mono<MessageDeleteRequest> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received MessageDeleteRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        if (request.messages.size < 2) {
            return channel.deleteMessageById(request.messages[0].toString())
                .mono("deleteMessage")
                .map { MessageDeleteRequest(request.channel, request.messages) }
        }

        val list = request.messages.map { toString() }
        return channel.deleteMessagesByIds(list)
            .mono("deleteMessages")
            .map { MessageDeleteRequest(request.channel, request.messages) }
    }

    @SentinelRequest
    fun consume(request: SendTypingRequest): Mono<SendTypingRequest> {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendTypingRequest for channel ${request.channel} which was not found")
            return Mono.empty()
        }

        return channel.sendTyping()
            .mono("sendTyping")
            .map { SendTypingRequest(request.channel) }
    }
}