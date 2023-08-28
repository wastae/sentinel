/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.io.SocketContext
import com.fredboat.sentinel.util.execute
import com.fredboat.sentinel.util.toJda
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.UserImpl
import net.dv8tion.jda.internal.interactions.InteractionHookImpl
import net.dv8tion.jda.internal.interactions.command.CommandAutoCompleteInteractionImpl
import net.dv8tion.jda.internal.interactions.command.CommandInteractionImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MessageRequests(private val shardManager: ShardManager) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MessageRequests::class.java)
    }

    fun consume(request: SendMessageRequest, context: SocketContext) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            val msg = "Received SendMessageRequest for channel ${request.channel} which was not found"
            log.error(msg)
            context.sendResponse(
                SendMessageResponse::class.java.simpleName, msg, request.responseId, false, false
            )
            return
        }

        val sendMessage = channel.sendMessage(request.content)
        request.embed?.let { sendMessage.setEmbeds(it.toJda()) }
        request.buttons?.let { sendMessage.setComponents(it.toJda()) }
        request.menu?.let { sendMessage.setActionRow(it.toJda()) }

        sendMessage.execute(
            SendMessageResponse::class.java.simpleName,
            request.responseId,
            context
        ).whenCompleteAsync { it, _ ->
            context.sendResponse(
                SendMessageResponse::class.java.simpleName,
                context.gson.toJson(SendMessageResponse(it.id)), request.responseId
            )
        }
    }

    fun consume(request: SendPrivateMessageRequest, context: SocketContext) {
        val shard = shardManager.shards.find { it.status == JDA.Status.CONNECTED } as JDAImpl
        val user =  UserImpl(request.recipient.toLong(), shard)

        user.openPrivateChannel().execute(
            SendMessageResponse::class.java.simpleName,
            request.responseId,
            context
        ).whenCompleteAsync { channel, _ ->
            channel.sendMessage(request.content).execute(
                SendMessageResponse::class.java.simpleName,
                request.responseId,
                context
            ).whenCompleteAsync { it, _ ->
                context.sendResponse(
                    SendMessageResponse::class.java.simpleName,
                    context.gson.toJson(SendMessageResponse(it.id)), request.responseId
                )
            }
        }
    }

    fun consume(request: EditMessageRequest, context: SocketContext) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received EditMessageRequest for channel ${request.channel} which was not found")
            return
        }

        val editMessage = channel.editMessageById(request.messageId, request.content)
        request.embed?.let { editMessage.setEmbeds(it.toJda()) }
        request.buttons?.let { editMessage.setComponents(it.toJda()) }
        request.menu?.let { editMessage.setActionRow(it.toJda()) }

        editMessage.queue({
            context.sendResponse(EditMessageResponse::class.java.simpleName, context.gson.toJson(EditMessageResponse(
                it.id,
                it.guild.id,
                true,
                null
            )), request.responseId)
        }, {
            context.sendResponse(EditMessageResponse::class.java.simpleName, context.gson.toJson(EditMessageResponse(
                request.messageId,
                channel.guild.id,
                false,
                it.stackTraceToString()
            )), request.responseId)
        })
    }

    fun consume(request: AddReactionRequest) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received AddReactionRequest for channel ${request.channel} which was not found")
            return
        }

        channel.addReactionById(request.messageId, Emoji.fromFormatted(request.emote)).queue()
    }

    fun consume(request: AddReactionsRequest) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received AddReactionsRequest for channel ${request.channel} which was not found")
            return
        }

        for (emote in request.emote) {
            channel.addReactionById(request.messageId, Emoji.fromFormatted(emote)).queue()
        }
    }

    fun consume(request: RemoveReactionRequest) {
        shardManager.retrieveUserById(request.userId).queue {
            val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
                ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

            if (channel == null) {
                log.error("Received RemoveReactionRequest for channel ${request.channel} which was not found")
                return@queue
            }

            channel.removeReactionById(request.messageId, Emoji.fromFormatted(request.emote), it).queue()
        }
    }

    fun consume(request: RemoveReactionsRequest) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received RemoveReactionsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.clearReactionsById(request.messageId).queue()
    }

    fun consume(request: MessageDeleteRequest) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received MessageDeleteRequest for channel ${request.channel} which was not found")
            return
        }

        if (request.messages.size < 2) {
            channel.deleteMessageById(request.messages[0]).queue()
            return
        }

        val list = request.messages.map { toString() }
        channel.deleteMessagesByIds(list).queue()
    }

    fun consume(request: SendTypingRequest) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received SendTypingRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendTyping().queue()
    }

    fun consume(request: SendContextCommandRequest) {
        val dataObject = DataObject.fromJson(request.interaction).getObject("d")
        val guild: Guild? = shardManager.getGuildById(dataObject.getLong("guild_id", 0L))

        if (guild != null) {
            val interaction = CommandInteractionImpl(guild.jda as JDAImpl, dataObject)
            interaction.reply(request.content).setEphemeral(true).queue()
        } else { // DM
            shardManager.retrieveUserById(request.userId).queue {
                val interaction = CommandInteractionImpl(it.jda as JDAImpl, dataObject)
                interaction.reply(request.content).setEphemeral(true).queue()
            }
        }
    }

    fun consume(request: SendSlashCommandRequest, context: SocketContext) {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            val msg = "Received SendSlashCommandRequest for guild ${request.guildId} which was not found"
            log.error(msg)
            context.sendResponse(
                SendMessageResponse::class.java.simpleName, msg, request.responseId, false, false
            )
            return
        }

        val interaction = CommandInteractionImpl(
            guild.jda as JDAImpl, DataObject.fromJson(request.interaction).getObject("d")
        )

        if (request.ephemeral) {
            val reply = interaction.reply(request.content)
            reply.setEphemeral(true)
            request.embed?.let { reply.setEmbeds(it.toJda()) }
            request.buttons?.let { reply.setComponents(it.toJda()) }
            request.menu?.let { reply.setActionRow(it.toJda()) }
            reply.execute(
                SendMessageResponse::class.java.simpleName,
                request.responseId,
                context
            ).whenCompleteAsync { hook, _ ->
                hook.retrieveOriginal().execute(
                    SendMessageResponse::class.java.simpleName,
                    request.responseId,
                    context
                ).whenCompleteAsync { it, _ ->
                    context.sendResponse(
                        SendMessageResponse::class.java.simpleName,
                        context.gson.toJson(SendMessageResponse(it.id)), request.responseId
                    )
                }
            }
        } else {
            val hook = interaction.hook as InteractionHookImpl
            hook.ack()
            hook.ready()
            val edit = hook.editOriginal(request.content)
            request.embed?.let { edit.setEmbeds(it.toJda()) }
            request.buttons?.let { edit.setComponents(it.toJda()) }
            request.menu?.let { edit.setActionRow(it.toJda()) }
            edit.execute(
                SendMessageResponse::class.java.simpleName,
                request.responseId,
                context
            ).whenCompleteAsync { it, _ ->
                context.sendResponse(
                    SendMessageResponse::class.java.simpleName,
                    context.gson.toJson(SendMessageResponse(it.id)), request.responseId
                )
            }
        }
    }

    fun consume(request: EditSlashCommandRequest) {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received EditSlashCommandRequest for guild ${request.guildId} which was not found")
            return
        }

        val interaction = CommandInteractionImpl(
            guild.jda as JDAImpl, DataObject.fromJson(request.interaction).getObject("d")
        )

        val hook = interaction.hook as InteractionHookImpl
        hook.ack()
        hook.ready()
        val edit = hook.editOriginal(request.content)
        request.embed?.let { edit.setEmbeds(it.toJda()) }
        request.buttons?.let { edit.setComponents(it.toJda()) }
        request.menu?.let { edit.setActionRow(it.toJda()) }
        edit.queue()
    }

    fun consume(request: SlashDeferReplyRequest) {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received SlashDeferReplyRequest for guild ${request.guildId} which was not found")
            return
        }

        val interaction = CommandInteractionImpl(
            guild.jda as JDAImpl, DataObject.fromJson(request.interaction).getObject("d")
        )

        interaction.deferReply().queue()
    }

    fun consume(request: SlashAutoCompleteRequest) {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received SlashAutoCompleteRequest for guild ${request.guildId} which was not found")
            return
        }

        val interaction = CommandAutoCompleteInteractionImpl(
            guild.jda as JDAImpl,
            DataObject.fromJson(request.interaction).getObject("d")
        )
        interaction.replyChoices(request.autoCompletion.toJda()).queue()
    }
}
