/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.AddReactionRequest
import com.fredboat.sentinel.entities.AddReactionsRequest
import com.fredboat.sentinel.entities.EditButtonsRequest
import com.fredboat.sentinel.entities.EditEmbedRequest
import com.fredboat.sentinel.entities.EditEmbedResponse
import com.fredboat.sentinel.entities.EditMessageRequest
import com.fredboat.sentinel.entities.EditSelectionMenuRequest
import com.fredboat.sentinel.entities.EditSlashCommandRequest
import com.fredboat.sentinel.entities.MessageDeleteRequest
import com.fredboat.sentinel.entities.RemoveComponentsRequest
import com.fredboat.sentinel.entities.RemoveReactionRequest
import com.fredboat.sentinel.entities.RemoveReactionsRequest
import com.fredboat.sentinel.entities.SendContextCommandRequest
import com.fredboat.sentinel.entities.SendEmbedRequest
import com.fredboat.sentinel.entities.SendMessageButtonsRequest
import com.fredboat.sentinel.entities.SendMessageRequest
import com.fredboat.sentinel.entities.SendMessageResponse
import com.fredboat.sentinel.entities.SendMessageSelectionMenuRequest
import com.fredboat.sentinel.entities.SendPrivateMessageRequest
import com.fredboat.sentinel.entities.SendSlashCommandRequest
import com.fredboat.sentinel.entities.SendSlashEmbedCommandRequest
import com.fredboat.sentinel.entities.SendSlashMenuCommandRequest
import com.fredboat.sentinel.entities.SendTypingRequest
import com.fredboat.sentinel.entities.SlashAutoCompleteRequest
import com.fredboat.sentinel.entities.SlashDeferReplyRequest
import com.fredboat.sentinel.io.SocketContext
import com.fredboat.sentinel.util.execute
import com.fredboat.sentinel.util.toJda
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.ActionComponent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.LayoutComponent
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.UserImpl
import net.dv8tion.jda.internal.interactions.InteractionHookImpl
import net.dv8tion.jda.internal.interactions.command.CommandAutoCompleteInteractionImpl
import net.dv8tion.jda.internal.interactions.command.CommandInteractionImpl
import net.dv8tion.jda.internal.interactions.component.StringSelectInteractionImpl
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
            context.sendResponse(SendMessageResponse::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        channel.sendMessage(request.message).execute(
            SendMessageResponse::class.java.simpleName,
            request.responseId,
            context
        ).whenCompleteAsync { it, _ ->
            context.sendResponse(SendMessageResponse::class.java.simpleName, context.gson.toJson(SendMessageResponse(it.id)), request.responseId)
        }
    }

    fun consume(request: SendEmbedRequest, context: SocketContext) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            val msg = "Received SendEmbedRequest for channel ${request.channel} which was not found"
            log.error(msg)
            context.sendResponse(SendMessageResponse::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        channel.sendMessageEmbeds(request.embed.toJda()).execute(
            SendMessageResponse::class.java.simpleName,
            request.responseId,
            context
        ).whenCompleteAsync { it, _ ->
            context.sendResponse(SendMessageResponse::class.java.simpleName, context.gson.toJson(SendMessageResponse(it.id)), request.responseId)
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
            channel.sendMessage(request.message).execute(
                SendMessageResponse::class.java.simpleName,
                request.responseId,
                context
            ).whenCompleteAsync { it, _ ->
                context.sendResponse(SendMessageResponse::class.java.simpleName, context.gson.toJson(SendMessageResponse(it.id)), request.responseId)
            }
        }
    }

    fun consume(request: EditMessageRequest) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received EditMessageRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageById(request.messageId, request.message).queue()
    }

    fun consume(request: EditEmbedRequest, context: SocketContext) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            val msg = "Received EditEmbedRequest for channel ${request.channel} which was not found"
            log.error(msg)
            context.sendResponse(SendMessageResponse::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        channel.editMessageEmbedsById(request.messageId, request.embed.toJda()).queue({
            context.sendResponse(EditEmbedResponse::class.java.simpleName, context.gson.toJson(EditEmbedResponse(
                it.id,
                it.guild.id,
                true,
                null
            )), request.responseId)
        }, {
            context.sendResponse(EditEmbedResponse::class.java.simpleName, context.gson.toJson(EditEmbedResponse(
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
            interaction.reply(request.message).setEphemeral(true).queue()
        } else { // DM
            shardManager.retrieveUserById(request.userId).queue {
                val interaction = CommandInteractionImpl(it.jda as JDAImpl, dataObject)
                interaction.reply(request.message).setEphemeral(true).queue()
            }
        }
    }

    fun consume(request: SendSlashCommandRequest, context: SocketContext) {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            val msg = "Received SendSlashCommandRequest for guild ${request.guildId} which was not found"
            log.error(msg)
            context.sendResponse(SendMessageResponse::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        val interaction = CommandInteractionImpl(
            guild.jda as JDAImpl, DataObject.fromJson(request.interaction).getObject("d")
        )

        if (request.ephemeral) {
            interaction.reply(request.message).setEphemeral(true).execute(
                SendMessageResponse::class.java.simpleName,
                request.responseId,
                context
            ).whenCompleteAsync { hook, _ ->
                hook.retrieveOriginal().execute(
                    SendMessageResponse::class.java.simpleName,
                    request.responseId,
                    context
                ).whenCompleteAsync { it, _ ->
                    context.sendResponse(SendMessageResponse::class.java.simpleName, context.gson.toJson(SendMessageResponse(it.id)), request.responseId)
                }
            }
        } else {
            val hook = interaction.hook as InteractionHookImpl
            hook.ack()
            hook.ready()
            hook.editOriginal(request.message).execute(
                SendMessageResponse::class.java.simpleName,
                request.responseId,
                context
            ).whenCompleteAsync { it, _ ->
                context.sendResponse(SendMessageResponse::class.java.simpleName, context.gson.toJson(SendMessageResponse(it.id)), request.responseId)
            }
        }
    }

    fun consume(request: SendSlashEmbedCommandRequest, context: SocketContext) {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            val msg = "Received SendSlashEmbedCommandRequest for guild ${request.guildId} which was not found"
            log.error(msg)
            context.sendResponse(SendMessageResponse::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        val interaction = CommandInteractionImpl(
            guild.jda as JDAImpl, DataObject.fromJson(request.interaction).getObject("d")
        )

        if (request.ephemeral) {
            interaction.replyEmbeds(request.message.toJda()).setEphemeral(true).execute(
                SendMessageResponse::class.java.simpleName,
                request.responseId,
                context
            ).whenCompleteAsync { hook, _ ->
                hook.retrieveOriginal().execute(
                    SendMessageResponse::class.java.simpleName,
                    request.responseId,
                    context
                ).whenCompleteAsync { it, _ ->
                    context.sendResponse(SendMessageResponse::class.java.simpleName, context.gson.toJson(SendMessageResponse(it.id)), request.responseId)
                }
            }
        } else {
            val hook = interaction.hook as InteractionHookImpl
            hook.ack()
            hook.ready()
            hook.editOriginalEmbeds(request.message.toJda()).execute(
                SendMessageResponse::class.java.simpleName,
                request.responseId,
                context
            ).whenCompleteAsync { it, _ ->
                context.sendResponse(SendMessageResponse::class.java.simpleName, context.gson.toJson(SendMessageResponse(it.id)), request.responseId)
            }
        }
    }

    fun consume(request: SendSlashMenuCommandRequest, context: SocketContext) {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            val msg = "Received SendSlashMenuCommandRequest for guild ${request.guildId} which was not found"
            log.error(msg)
            context.sendResponse(SendMessageResponse::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        val interaction = StringSelectInteractionImpl(
            guild.jda as JDAImpl, DataObject.fromJson(request.interaction).getObject("d")
        )

        if (request.ephemeral) {
            interaction.reply(request.message).setEphemeral(true).execute(
                SendMessageResponse::class.java.simpleName,
                request.responseId,
                context
            ).whenCompleteAsync { hook, _ ->
                hook.retrieveOriginal().execute(
                    SendMessageResponse::class.java.simpleName,
                    request.responseId,
                    context
                ).whenCompleteAsync { it, _ ->
                    context.sendResponse(SendMessageResponse::class.java.simpleName, context.gson.toJson(SendMessageResponse(it.id)), request.responseId)
                }
            }
        } else {
            val hook = interaction.hook as InteractionHookImpl
            hook.ack()
            hook.ready()
            hook.editOriginal(request.message).execute(
                SendMessageResponse::class.java.simpleName,
                request.responseId,
                context
            ).whenCompleteAsync { it, _ ->
                context.sendResponse(SendMessageResponse::class.java.simpleName, context.gson.toJson(SendMessageResponse(it.id)), request.responseId)
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
        hook.editOriginal(request.message).queue()
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

        val interaction = CommandAutoCompleteInteractionImpl((guild.jda as JDAImpl), DataObject.fromJson(request.interaction).getObject("d"))
        interaction.replyChoices(request.autoCompletion.toJda()).queue()
    }

    /**
     * Components
     */

    fun consume(request: SendMessageButtonsRequest) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received SendMessageButtonsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendMessage(request.message).setComponents(request.buttons.toJda()).queue()
    }

    fun consume(request: SendMessageSelectionMenuRequest) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received SendMessageSelectionMenuRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendMessage(request.message).setActionRow(request.menu.toJda()).queue()
    }

    fun consume(request: EditButtonsRequest) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received EditButtonsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageComponentsById(request.messageId, request.buttons.toJda()).queue()
    }

    fun consume(request: EditSelectionMenuRequest) {
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received EditSelectionMenuRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageComponentsById(request.messageId, ActionRow.of(request.menu.toJda())).queue()
    }

    fun consume(request: RemoveComponentsRequest) {
        if (request.messageId == "0") return
        val channel = shardManager.getChannelById(TextChannel::class.java, request.channel)
            ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)

        if (channel == null) {
            log.error("Received RemoveComponentsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.retrieveMessageById(request.messageId).queue { message ->
            val components: List<ActionRow> = ArrayList(message.actionRows)
            val ids = ArrayList<String>()
            message.actionRows.forEach { actionRow -> actionRow.components.forEach { ids.add((it as ActionComponent).id!!) } }
            ids.forEach { LayoutComponent.updateComponent(components, it, null) }
            channel.editMessageComponentsById(request.messageId, components).queue()
        }
    }
}
