/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.util.complete
import com.fredboat.sentinel.util.queue
import com.fredboat.sentinel.util.toJda
import net.dv8tion.jda.bot.sharding.ShardManager
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.impl.JDAImpl
import net.dv8tion.jda.core.entities.impl.UserImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MessageRequests(private val shardManager: ShardManager) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MessageRequests::class.java)
    }

    fun consume(request: SendMessageRequest): SendMessageResponse? {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendMessageRequest for channel ${request.channel} which was not found")
            return null
        }

        return channel.sendMessage(request.message)
                .complete("sendMessage")
                .let { SendMessageResponse(it.idLong) }
    }

    fun consume(request: SendEmbedRequest): SendMessageResponse? {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendEmbedRequest for channel ${request.channel} which was not found")
            return null
        }

        return SendMessageResponse(
                channel.sendMessage(request.embed.toJda()).complete("sendEmbed").idLong
        )
    }

    fun consume(request: SendPrivateMessageRequest): SendMessageResponse? {
        val shard = shardManager.shards.find { it.status == JDA.Status.CONNECTED } as JDAImpl
        val user = UserImpl(request.recipient, shard)
        val channel = user.openPrivateChannel().complete(true)!!

        return SendMessageResponse(
                channel.sendMessage(request.message).complete("sendPrivate").idLong
        )
    }

    fun consume(request: EditMessageRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditMessageRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageById(request.messageId, request.message).queue("editMessage")
    }

    fun consume(request: MessageDeleteRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received MessageDeleteRequest for channel ${request.channel} which was not found")
            return
        }

        if (request.messages.size < 2) {
            channel.deleteMessageById(request.messages[0].toString()).queue("deleteMessage")
            return
        }

        val list = request.messages.map { toString() }
        channel.deleteMessagesByIds(list).queue("deleteMessages")
    }

    fun consume(request: SendTypingRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendTypingRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendTyping().queue("sendTyping")
    }

}