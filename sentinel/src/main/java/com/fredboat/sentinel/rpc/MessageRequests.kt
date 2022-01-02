/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.corundumstudio.socketio.SocketIOClient
import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.util.toJda
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ComponentLayout
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.UserImpl
import net.dv8tion.jda.internal.interactions.InteractionHookImpl
import net.dv8tion.jda.internal.interactions.InteractionImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MessageRequests(private val shardManager: ShardManager) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MessageRequests::class.java)
    }

    fun consume(request: SendMessageRequest, client: SocketIOClient) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendMessageRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendMessage(request.message).queue {
            client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
        }
    }

    fun consume(request: SendEmbedRequest, client: SocketIOClient) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendEmbedRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendMessageEmbeds(request.embed.toJda()).queue {
            client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
        }
    }

    fun consume(request: SendPrivateMessageRequest, client: SocketIOClient) {
        val shard = shardManager.shards.find { it.status == JDA.Status.CONNECTED } as JDAImpl
        val user = UserImpl(request.recipient.toLong(), shard)
        val channel = user.openPrivateChannel().complete(true)!!

        channel.sendMessage(request.message).queue {
            client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
        }
    }

    fun consume(request: EditMessageRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditMessageRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageById(request.messageId, request.message).queue()
    }

    fun consume(request: EditEmbedRequest, client: SocketIOClient) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditEmbedRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageEmbedsById(request.messageId, request.embed.toJda()).queue {
            client.sendEvent("editEmbedResponse-${request.responseId}", EditEmbedResponse(it.id, it.guild.id))
        }
    }

    fun consume(request: AddReactionRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received AddReactionRequest for channel ${request.channel} which was not found")
            return
        }

        channel.addReactionById(request.messageId, request.emote).queue()
    }

    fun consume(request: AddReactionsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received AddReactionsRequest for channel ${request.channel} which was not found")
            return
        }

        for (emote in request.emote) {
            channel.addReactionById(request.messageId, emote).queue()
        }

        return
    }

    fun consume(request: RemoveReactionRequest) {
        shardManager.retrieveUserById(request.userId).queue {
            val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

            if (channel == null) {
                log.error("Received RemoveReactionRequest for channel ${request.channel} which was not found")
                return@queue
            }

            channel.removeReactionById(request.messageId, request.emote, it).queue()
        }
    }

    fun consume(request: RemoveReactionsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received RemoveReactionsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.clearReactionsById(request.messageId).queue()
    }

    fun consume(request: MessageDeleteRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

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
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendTypingRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendTyping().queue()
    }

    fun consume(request: SendSlashCommandRequest, client: SocketIOClient) {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received SendSlashCommandRequest for guild ${request.guildId} which was not found")
            return
        }

        val channel: TextChannel? = shardManager.getTextChannelById(request.channelId)

        if (channel == null) {
            log.error("Can't find channel in guild ${request.guildId} with ${request.channelId} id")
            return
        }

        val member: Member? = guild.getMemberById(request.userId)

        if (member == null) {
            log.error("Can't find member in guild ${request.guildId} with ${request.userId} id")
            return
        }

        val interaction = InteractionImpl(
            request.interactionId.toLong(),
            request.interactionType,
            request.interactionToken,
            guild,
            member,
            member.user,
            channel
        )
        val hook = InteractionHookImpl(interaction, guild.jda)
        hook.ack()
        hook.ready()
        if (request.ephemeral) {
            hook.setEphemeral(request.ephemeral).sendMessage(request.message).queue {
                client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
            }
        } else {
            hook.editOriginal(request.message).queue {
                client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
            }
        }
    }

    /**
     * Components
     */

    fun consume(request: SendMessageButtonsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendMessageButtonsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendMessage(request.message).setActionRows(request.buttons.toJda()).queue()
    }

    fun consume(request: SendMessageSelectionMenuRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendMessageSelectionMenuRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendMessage(request.message).setActionRow(request.menu.toJda()).queue()
    }

    fun consume(request: EditButtonsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditButtonsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageComponentsById(request.messageId, request.buttons.toJda()).queue()
    }

    fun consume(request: EditSelectionMenuRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditSelectionMenuRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageComponentsById(request.messageId, ActionRow.of(request.menu.toJda())).queue()
    }

    fun consume(request: RemoveComponentsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received RemoveComponentsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.retrieveMessageById(request.messageId).queue { message ->
            val components: List<ActionRow> = ArrayList<ActionRow>(message.actionRows)
            val ids = ArrayList<String>()
            message.actionRows.forEach { actionRow -> actionRow.components.forEach { ids.add(it.id!!) } }
            ids.forEach { ComponentLayout.updateComponent(components, it, null) }
            channel.editMessageComponentsById(request.messageId, components).queue()
        }
    }
}
