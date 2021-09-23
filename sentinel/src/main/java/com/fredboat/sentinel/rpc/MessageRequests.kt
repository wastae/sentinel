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
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ComponentLayout
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.UserImpl
import net.dv8tion.jda.internal.interactions.InteractionImpl
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

        return channel.sendMessageEmbeds(request.embed.toJda())
                .complete("sendEmbed")
                .let { SendMessageResponse(it.idLong) }
    }

    fun consume(request: SendPrivateMessageRequest): SendMessageResponse? {
        val shard = shardManager.shards.find { it.status == JDA.Status.CONNECTED } as JDAImpl
        val user = UserImpl(request.recipient, shard)
        val channel = user.openPrivateChannel().complete(true)!!

        return channel.sendMessage(request.message)
                .complete("sendPrivate")
                .let { SendMessageResponse(it.idLong) }
    }

    fun consume(request: EditMessageRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditMessageRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageById(request.messageId, request.message).queue("editMessage")
    }

    fun consume(request: EditEmbedRequest): EditEmbedResponse? {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditEmbedRequest for channel ${request.channel} which was not found")
            return null
        }

        return channel.editMessageEmbedsById(request.messageId, request.embed.toJda())
                .complete("editEmbedMessage")
                .let { EditEmbedResponse(it.idLong, it.guild.idLong) }
    }

    fun consume(request: AddReactionRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received AddReactionRequest for channel ${request.channel} which was not found")
            return
        }

        channel.addReactionById(request.messageId, request.emote).queue("addReaction")
    }

    fun consume(request: AddReactionsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received AddReactionsRequest for channel ${request.channel} which was not found")
            return
        }

        for (emote in request.emote) {
            channel.addReactionById(request.messageId, emote).queue("addReactions")
        }
    }

    fun consume(request: RemoveReactionRequest) {
        shardManager.retrieveUserById(request.userId)
                .complete("retrieveUser")
                .let {
                    val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

                    if (channel == null) {
                        log.error("Received RemoveReactionRequest for channel ${request.channel} which was not found")
                        return
                    }

                    channel.removeReactionById(request.messageId, request.emote, it).queue("removeReaction")
                }
    }

    fun consume(request: RemoveReactionsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received RemoveReactionsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.clearReactionsById(request.messageId).queue("removeReactions")
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

    fun consume(request: SendSlashCommandRequest): SendMessageResponse? {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received SendSlashCommandRequest for guild ${request.guildId} which was not found")
            return null
        }

        val channel: TextChannel? = shardManager.getTextChannelById(request.channelId)

        if (channel == null) {
            log.error("Can't find channel in guild ${request.guildId} with ${request.channelId} id")
            return null
        }

        val member: Member? = guild.getMemberById(request.userId)

        if (member == null) {
            log.error("Can't find member in guild ${request.guildId} with ${request.userId} id")
            return null
        }

        val hook = InteractionImpl(
                request.interactionId,
                request.interactionType,
                request.interactionToken,
                guild,
                member,
                member.user,
                channel
        ).hook
        if (!hook.interaction.isAcknowledged) {
            hook.interaction.deferReply().queue("deferReply")
        }
        return hook.editOriginal(request.message)
                .complete("sendSlashCommand")
                .let { SendMessageResponse(it.idLong) }
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

        channel.sendMessage(request.message).setActionRows(request.buttons.toJda()).queue("sendMessageButtons")
    }

    fun consume(request: SendMessageSelectionMenuRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received SendMessageSelectionMenuRequest for channel ${request.channel} which was not found")
            return
        }

        channel.sendMessage(request.message).setActionRow(request.menu.toJda()).queue("sendMessageSelectionMenu")
    }

    fun consume(request: EditButtonsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditButtonsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageComponentsById(request.messageId, request.buttons.toJda()).queue("editSelectionMenu")
    }

    fun consume(request: EditSelectionMenuRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received EditSelectionMenuRequest for channel ${request.channel} which was not found")
            return
        }

        channel.editMessageComponentsById(request.messageId, ActionRow.of(request.menu.toJda())).queue("editSelectionMenu")
    }

    fun consume(request: RemoveComponentsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received RemoveComponentsRequest for channel ${request.channel} which was not found")
            return
        }

        channel.retrieveMessageById(request.messageId).complete("retrieveMessage").let { message ->
            val components: List<ActionRow> = ArrayList<ActionRow>(message.actionRows)
            val ids = ArrayList<String>()
            message.actionRows.forEach { actionRow ->
                actionRow.components.forEach {
                    ids.add(it.id!!)
                }
            }
            ids.forEach { ComponentLayout.updateComponent(components, it, null) }
            channel.editMessageComponentsById(request.messageId, components).queue("removeComponents")
        }
    }
}