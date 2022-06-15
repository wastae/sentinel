/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.jda

import com.corundumstudio.socketio.SocketIOClient
import com.fredboat.sentinel.SocketServer
import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.metrics.Counters
import com.fredboat.sentinel.util.toEntity
import com.neovisionaries.ws.client.WebSocketFrame
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.VoiceChannel
import net.dv8tion.jda.api.events.*
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent
import net.dv8tion.jda.api.events.channel.GenericChannelEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdatePositionEvent
import net.dv8tion.jda.api.events.guild.GenericGuildEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.member.*
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.override.GenericPermissionOverrideEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateOwnerEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent
import net.dv8tion.jda.api.events.http.HttpRequestEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.role.GenericRoleEvent
import net.dv8tion.jda.api.events.role.RoleCreateEvent
import net.dv8tion.jda.api.events.role.RoleDeleteEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdatePositionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.utils.PermissionUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

class JdaWebsocketEventListener(
    private val shardManager: ShardManager,
    private val voiceServerUpdateCache: VoiceServerUpdateCache,
    var socketClient: SocketIOClient
) : ListenerAdapter() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(JdaWebsocketEventListener::class.java)
    }

    init {
        shardManager.addEventListener(this)
    }

    @Volatile
    var sessionPaused = false
    private val resumeEventQueue = ConcurrentLinkedQueue<Pair<String, Any>>()

    /* Shard lifecycle */

    override fun onStatusChange(event: StatusChangeEvent) = event.run {
        log.info("${jda.shardInfo}: $oldStatus -> $newStatus")
        dispatchSocket("shardStatusChange", ShardStatusChange(jda.toEntity()))
    }

    override fun onReady(event: ReadyEvent) {
        dispatchSocket("shardLifecycleEvent", ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.READIED))
    }

    override fun onDisconnect(event: DisconnectEvent) {
        dispatchSocket("shardLifecycleEvent", ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.DISCONNECTED))

        val frame: WebSocketFrame? = if (event.isClosedByServer)
            event.serviceCloseFrame else event.clientCloseFrame

        val prefix = if (event.isClosedByServer) "s" else "c"
        val code = "$prefix${frame?.closeCode}"

        log.warn("${event.jda.shardInfo} closed. {} {}", code, frame?.closeReason)
        Counters.shardDisconnects.labels(code).inc()
    }

    override fun onResumed(event: ResumedEvent) =
        dispatchSocket("shardLifecycleEvent", ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.RESUMED))


    override fun onReconnected(event: ReconnectedEvent) =
        dispatchSocket("shardLifecycleEvent", ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.RECONNECTED))


    override fun onShutdown(event: ShutdownEvent) =
        dispatchSocket("shardLifecycleEvent", ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.SHUTDOWN))


    /* Guild jda */
    override fun onGuildJoin(event: GuildJoinEvent) {
        dispatchSocket("guildJoinEvent", GuildJoinEvent(
            event.guild.id,
            event.guild.locale.toLanguageTag()
        ))
    }

    override fun onGuildLeave(event: GuildLeaveEvent) =
        dispatchSocket("guildLeaveEvent", GuildLeaveEvent(
            event.guild.id,
            (event.guild.selfMember.timeJoined.toEpochSecond() * 1000).toString()
        ))

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) = onMemberChange(event.member)
    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) = onMemberChange(event.member)
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) = onMemberChange(event.member)
    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        if (event.member != null) {
            onMemberChange(event.member!!)
        }
    }

    private fun onMemberChange(member: net.dv8tion.jda.api.entities.Member) {
        if (!SocketServer.subscriptionsCache.contains(member.guild.idLong)) return
        dispatchSocket("guildMemberUpdate", GuildMemberUpdate(
            member.guild.id,
            member.toEntity()
        ))
    }

    /* Voice jda */
    override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return

        if (event.channelJoined.type == ChannelType.STAGE && event.member.user.idLong == event.guild.selfMember.user.idLong) {
            event.guild.requestToSpeak()
        }
        dispatchSocket("voiceJoinEvent", VoiceJoinEvent(
            event.guild.id,
            event.channelJoined.id,
            event.member.user.id
        ))
    }

    override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
        if (event.member.user.idLong == event.guild.selfMember.user.idLong) {
            voiceServerUpdateCache.onVoiceLeave(event.guild.id)
        }
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return

        dispatchSocket("voiceLeaveEvent", VoiceLeaveEvent(
            event.guild.id,
            event.channelLeft.id,
            event.member.user.id
        ))
    }

    override fun onGuildVoiceMove(event: GuildVoiceMoveEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return

        if (event.channelJoined.type == ChannelType.STAGE && event.member.user.idLong == event.guild.selfMember.user.idLong) {
            event.guild.requestToSpeak()
        }

        dispatchSocket("voiceMoveEvent", VoiceMoveEvent(
            event.guild.id,
            event.channelLeft.id,
            event.channelJoined.id,
            event.member.user.id
        ))
    }

    /* Message jda */
    override fun onMessageReceived(event: net.dv8tion.jda.api.events.message.MessageReceivedEvent) {
        if (event.message.type != MessageType.DEFAULT) return
        if (event.isWebhookMessage) return
        if (event.isFromGuild && !event.channelType.isThread) {
            dispatchSocket("messageReceivedEvent", MessageReceivedEvent(
                event.message.id,
                event.message.guild.id,
                event.channel.id,
                PermissionUtil.getEffectivePermission((event.channel as TextChannel).permissionContainer, event.guild.selfMember).toString(),
                PermissionUtil.getEffectivePermission((event.channel as TextChannel).permissionContainer, event.message.member).toString(),
                event.message.contentRaw,
                event.author.id,
                event.author.isBot,
                event.message.attachments.map { if (it.isImage) it.proxyUrl else it.url }
            ))
        } else if (!event.isFromGuild && !event.channelType.isThread) {
            dispatchSocket("privateMessageReceivedEvent", PrivateMessageReceivedEvent(
                event.message.contentRaw,
                event.author.toEntity()
            ))
        }
    }

    override fun onMessageDelete(event: net.dv8tion.jda.api.events.message.MessageDeleteEvent) {
        dispatchSocket("messageDeleteEvent", MessageDeleteEvent(
            event.messageId,
            event.guild.id,
            event.channel.id
        ))
    }

    override fun onMessageReactionAdd(event: net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent) {
        if (event.isFromGuild && event.channelType.isThread) return
        if (event.member == null) return
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return

        dispatchSocket("messageReactionAddEvent", MessageReactionAddEvent(
            event.messageId,
            event.guild.id,
            event.channel.id,
            PermissionUtil.getEffectivePermission((event.channel as TextChannel).permissionContainer, event.guild.selfMember).toString(),
            PermissionUtil.getEffectivePermission((event.channel as TextChannel).permissionContainer, event.member).toString(),
            event.member!!.id,
            event.member!!.user.isBot,
            event.reactionEmote.asReactionCode,
            event.reactionEmote.isEmoji
        ))
    }

    override fun onRawGateway(event: RawGatewayEvent) {
        log.info("RawGatewayEvent ${event.`package`.toPrettyString()}")
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (!event.isFromGuild) {
            event.reply("Slash commands not supported in DM").setEphemeral(true).queue()
            return
        }
        if (event.isFromGuild && event.channelType.isThread) {
            event.reply("Slash commands not supported in threads").setEphemeral(true).queue()
            return
        }
        if (event.guild == null) return
        if (event.member == null) return

        dispatchSocket("slashCommandsEvent", SlashCommandsEvent(
            event.rawData.toJson(),
            event.guild!!.id,
            event.channel.id,
            PermissionUtil.getEffectivePermission((event.channel as TextChannel).permissionContainer, event.guild!!.selfMember).toString(),
            PermissionUtil.getEffectivePermission((event.channel as TextChannel).permissionContainer, event.member).toString(),
            event.member!!.id,
            event.member!!.user.isBot,
            event.userLocale.toLanguageTag(),
            event.commandPath,
            event.options.map { it.toEntity() }
        ))
    }

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        if (!event.isFromGuild) {
            event.replyChoiceStrings("Slash commands not supported in DM").queue()
            return
        }
        if (event.isFromGuild && event.channelType.isThread) {
            event.replyChoiceStrings("Slash commands not supported in threads").queue()
            return
        }
        if (event.guild == null) return
        if (event.channel == null) return

        if (event.focusedOption.value.isEmpty()) {
            event.replyChoice("Empty request", "dQw4w9WgXcQ").queue()
            return
        }

        dispatchSocket("autoCompleteEvent", SlashAutoCompleteEvent(
            event.rawData.toJson(),
            event.guild!!.id,
            event.channel!!.id,
            PermissionUtil.getEffectivePermission((event.channel as TextChannel).permissionContainer, event.guild!!.selfMember).toString(),
            PermissionUtil.getEffectivePermission((event.channel as TextChannel).permissionContainer, event.member).toString(),
            event.member!!.id,
            event.member!!.user.isBot,
            event.focusedOption.value
        ))
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (event.guild == null) return
        if (event.member == null) return
        if (!SocketServer.subscriptionsCache.contains(event.guild!!.idLong)) return

        event.deferEdit().queue()
        dispatchSocket("buttonEvent", ButtonEvent(
            event.rawData.toJson(),
            event.componentId,
            event.messageId,
            event.guild!!.id,
            event.channel.id,
            event.member!!.id,
            event.member!!.user.isBot
        ))
    }

    override fun onSelectMenuInteraction(event: SelectMenuInteractionEvent) {
        if (event.guild == null) return
        if (event.member == null) return
        if (!SocketServer.subscriptionsCache.contains(event.guild!!.idLong)) return

        event.deferEdit().queue()
        dispatchSocket("selectionMenuEvent", SelectionMenuEvent(
            event.rawData.toJson(),
            event.values,
            event.componentId,
            event.messageId,
            event.guild!!.id,
            event.channel.id,
            event.member!!.id,
            event.member!!.user.isBot
        ))
    }

    /*
    *** Guild invalidation ***

    Things that we don't explicitly handle, but that we cache:

    Guild name and owner
    Roles
    Channels
    Channel names (text, voice, categories)
    Our permissions in channels

    We can improve performance by handling more of these
     */

    override fun onGenericGuild(event: GenericGuildEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return
        if (event is GuildUpdateNameEvent || event is GuildUpdateOwnerEvent) {
            updateGuild(event.guild)
        } else if (event is GenericPermissionOverrideEvent && event.channel is TextChannel) {
            updateChannelPermissions(event.guild)
        } else if (event is GenericPermissionOverrideEvent && event.channel is VoiceChannel) {
            updateChannelPermissions(event.guild)
        }
    }

    override fun onGenericChannel(event: GenericChannelEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return
        if (event is ChannelDeleteEvent || event is ChannelCreateEvent) {
            updateGuild(event.guild)
            return
        } else if (event is ChannelUpdatePositionEvent) {
            updateChannelPermissions(event.guild)
        } else if (event.channel is TextChannel) {
            dispatchSocket("textChannelUpdate", TextChannelUpdate(
                event.guild.id,
                (event.channel as TextChannel).toEntity()
            ))
        } else if (event.channel is VoiceChannel) {
            dispatchSocket("voiceChannelUpdate", VoiceChannelUpdate(
                event.guild.id,
                (event.channel as VoiceChannel).toEntity()
            ))
        }
    }

    override fun onGenericRole(event: GenericRoleEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return
        if (event is RoleDeleteEvent || event is RoleCreateEvent) {
            updateGuild(event.guild)
            return
        } else if (event is RoleUpdatePositionEvent || event is RoleUpdatePermissionsEvent) {
            updateChannelPermissions(event.guild)
        }
        dispatchSocket("roleUpdate", RoleUpdate(
            event.guild.id,
            event.role.toEntity()
        ))
    }

    override fun onGenericGuildMember(event: GenericGuildMemberEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return
        if (event is GuildMemberRoleAddEvent || event is GuildMemberRoleRemoveEvent) {
            updateGuild(event.guild)
            return
        }
    }

    private fun updateGuild(guild: Guild) {
        dispatchSocket("guildUpdateEvent", GuildUpdateEvent(guild.toEntity(voiceServerUpdateCache)))
    }

    private fun updateChannelPermissions(guild: Guild) {
        val permissions = mutableMapOf<String, String>()
        val self = guild.selfMember
        val func = { channel: GuildChannel ->
            permissions[channel.id] = PermissionUtil.getEffectivePermission(channel.permissionContainer, self).toString()
        }

        guild.textChannels.forEach(func)
        guild.voiceChannels.forEach(func)

        dispatchSocket("channelPermissionsUpdate", ChannelPermissionsUpdate(
            guild.id,
            permissions
        ))
    }

    override fun onHttpRequest(event: HttpRequestEvent) {
        if (event.response!!.code >= 300) {
            log.warn("Unsuccessful JDA HTTP\n{}\n{}", event.requestRaw, event.responseRaw)
        }
    }

    /* Util */

    private fun dispatchSocket(eventName: String, event: Any) {
        if (sessionPaused) {
            resumeEventQueue.add(Pair(eventName, event))
            log.info("Saved $eventName to queue, total size ${resumeEventQueue.size}")
            return
        }

        socketClient.sendEvent(eventName, event)
    }

    fun pause() {
        sessionPaused = true
    }

    fun resume(socketClient: SocketIOClient) {
        sessionPaused = false
        this.socketClient = socketClient
        log.info("Replaying ${resumeEventQueue.size} events")

        while (resumeEventQueue.isNotEmpty()) {
            val event = resumeEventQueue.remove()
            dispatchSocket(event.first, event.second)
        }
    }

    fun removeListener() {
        shardManager.removeEventListener(this)
    }
}