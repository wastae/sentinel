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
import net.dv8tion.jda.api.entities.TextChannel
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
import net.dv8tion.jda.internal.interactions.component.SelectMenuInteractionImpl
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

        channel.editMessageEmbedsById(request.messageId, request.embed.toJda()).queue({
            client.sendEvent("editEmbedResponse-${request.responseId}",
                EditEmbedResponse(it.id, it.guild.id, true, null)
            )
        }, {
            client.sendEvent("editEmbedResponse-${request.responseId}",
                EditEmbedResponse(request.messageId, channel.guild.id, false, it.stackTraceToString())
            )
        })
    }

    fun consume(request: AddReactionRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

        if (channel == null) {
            log.error("Received AddReactionRequest for channel ${request.channel} which was not found")
            return
        }

        channel.addReactionById(request.messageId, Emoji.fromFormatted(request.emote)).queue()
    }

    fun consume(request: AddReactionsRequest) {
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

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
            val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

            if (channel == null) {
                log.error("Received RemoveReactionRequest for channel ${request.channel} which was not found")
                return@queue
            }

            channel.removeReactionById(request.messageId, Emoji.fromFormatted(request.emote), it).queue()
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

        val interaction = CommandInteractionImpl(
            guild.jda as JDAImpl, DataObject.fromJson(request.interaction).getObject("d")
        )

        if (request.ephemeral) {
            interaction.reply(request.message).setEphemeral(true).queue { it ->
                it.retrieveOriginal().queue {
                    client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
                }
            }
        } else {
            val hook = interaction.hook as InteractionHookImpl
            hook.ack()
            hook.ready()
            hook.editOriginal(request.message).queue {
                client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
            }
        }
    }

    fun consume(request: SendSlashEmbedCommandRequest, client: SocketIOClient) {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received SendSlashEmbedCommandRequest for guild ${request.guildId} which was not found")
            return
        }

        val interaction = CommandInteractionImpl(
            guild.jda as JDAImpl, DataObject.fromJson(request.interaction).getObject("d")
        )

        if (request.ephemeral) {
            interaction.replyEmbeds(request.message.toJda()).setEphemeral(true).queue { it ->
                it.retrieveOriginal().queue {
                    client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
                }
            }
        } else {
            val hook = interaction.hook as InteractionHookImpl
            hook.ack()
            hook.ready()
            hook.editOriginalEmbeds(request.message.toJda()).queue {
                client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
            }
        }
    }

    fun consume(request: SendSlashMenuCommandRequest, client: SocketIOClient) {
        val guild: Guild? = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received SendSlashMenuCommandRequest for guild ${request.guildId} which was not found")
            return
        }

        val interaction = SelectMenuInteractionImpl(
            guild.jda as JDAImpl, DataObject.fromJson(request.interaction).getObject("d")
        )

        if (request.ephemeral) {
            interaction.reply(request.message).setEphemeral(true).queue { it ->
                it.retrieveOriginal().queue {
                    client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
                }
            }
        } else {
            val hook = interaction.hook as InteractionHookImpl
            hook.ack()
            hook.ready()
            hook.editOriginal(request.message).queue {
                client.sendEvent("sendMessageResponse-${request.responseId}", SendMessageResponse(it.id))
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
        if (request.messageId == "0") return
        val channel: TextChannel? = shardManager.getTextChannelById(request.channel)

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
