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
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.events.*
import net.dv8tion.jda.api.events.channel.category.CategoryCreateEvent
import net.dv8tion.jda.api.events.channel.category.CategoryDeleteEvent
import net.dv8tion.jda.api.events.channel.category.GenericCategoryEvent
import net.dv8tion.jda.api.events.channel.category.update.CategoryUpdatePositionEvent
import net.dv8tion.jda.api.events.channel.text.GenericTextChannelEvent
import net.dv8tion.jda.api.events.channel.text.TextChannelCreateEvent
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent
import net.dv8tion.jda.api.events.channel.voice.GenericVoiceChannelEvent
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelCreateEvent
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelDeleteEvent
import net.dv8tion.jda.api.events.channel.voice.update.VoiceChannelUpdatePositionEvent
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
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent
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

class JdaRabbitEventListener(
        private val shardManager: ShardManager,
        val socketClient: SocketIOClient
) : ListenerAdapter() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(JdaRabbitEventListener::class.java)
    }

    init {
        shardManager.addEventListener(this)
    }

    /* Shard lifecycle */

    override fun onStatusChange(event: StatusChangeEvent) = event.run {
        log.info("Shard ${jda.shardInfo}: $oldStatus -> $newStatus")
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

        log.warn("Shard ${event.jda.shardInfo} closed. {} {}", code, frame?.closeReason)
        Counters.shardDisconnects.labels(code).inc()
    }

    override fun onResume(event: ResumedEvent) =
        dispatchSocket("shardLifecycleEvent", ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.RESUMED))

    override fun onReconnect(event: ReconnectedEvent) =
        dispatchSocket("shardLifecycleEvent", ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.RECONNECTED))

    override fun onShutdown(event: ShutdownEvent) =
        dispatchSocket("shardLifecycleEvent", ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.SHUTDOWN))

    /* Guild jda */
    override fun onGuildJoin(event: GuildJoinEvent) =
        dispatchSocket("guildJoinEvent", GuildJoinEvent(
            event.guild.id,
            event.guild.regionRaw
        ))

    override fun onGuildLeave(event: GuildLeaveEvent) =
        dispatchSocket("guildLeaveEvent", GuildLeaveEvent(
            event.guild.id,
            (event.guild.selfMember.timeJoined.toEpochSecond() * 1000).toString()
        ))

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) = onMemberChange(event.member)
    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) = onMemberChange(event.member)
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) = onMemberChange(event.member)
    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) = onMemberChange(event.member!!)

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
        updateGuild(event.guild)
        if (event.channelJoined.type == ChannelType.STAGE && event.member.user.idLong == event.guild.selfMember.user.idLong) {
            event.guild.requestToSpeak()
        }
        dispatchSocket("voiceJoinEvent", VoiceJoinEvent(
            event.guild.id,
            event.channelJoined.id,
            event.member.toEntity()
        ))
    }

    override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
        if (event.member.user.idLong == event.guild.selfMember.user.idLong) {
            SocketServer.voiceServerUpdateCache.onVoiceLeave(event.guild.id)
        }
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return
        updateGuild(event.guild)
        dispatchSocket("voiceLeaveEvent", VoiceLeaveEvent(
            event.guild.id,
            event.channelLeft.id,
            event.member.toEntity()
        ))
    }

    override fun onGuildVoiceMove(event: GuildVoiceMoveEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return
        updateGuild(event.guild)
        if (event.channelJoined.type == ChannelType.STAGE && event.member.user.idLong == event.guild.selfMember.user.idLong) {
            event.guild.requestToSpeak()
        }
        dispatchSocket("voiceMoveEvent", VoiceMoveEvent(
            event.guild.id,
            event.channelLeft.id,
            event.channelJoined.id,
            event.member.toEntity()
        ))
    }

    /* Message jda */
    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) = event.run {
        if (message.type != MessageType.DEFAULT) return
        if (message.isWebhookMessage) return

        if (SocketServer.subscriptionsCache.contains(event.guild.idLong)) {
            updateGuild(event.guild)
        }

        dispatchSocket("messageReceivedEvent", MessageReceivedEvent(
            message.id,
            message.guild.id,
            channel.id,
            PermissionUtil.getEffectivePermission(channel, guild.selfMember).toString(),
            PermissionUtil.getEffectivePermission(channel, event.message.member).toString(),
            message.contentRaw,
            author.id,
            author.isBot,
            message.attachments.map { if (it.isImage) it.proxyUrl else it.url },
            event.message.member!!.toEntity(),
            event.message.mentionedMembers.map { it.toEntity() }
        ))
    }

    override fun onPrivateMessageReceived(event: PrivateMessageReceivedEvent) {
        dispatchSocket("privateMessageReceivedEvent", PrivateMessageReceivedEvent(
            event.message.contentRaw,
            event.author.toEntity()
        ))
    }

    override fun onGuildMessageDelete(event: GuildMessageDeleteEvent) {
        dispatchSocket("messageDeleteEvent", MessageDeleteEvent(
            event.messageId,
            event.guild.id,
            event.channel.id
        ))
    }

    override fun onGuildMessageReactionAdd(event: GuildMessageReactionAddEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return

        updateGuild(event.guild)

        dispatchSocket("messageReactionAddEvent", MessageReactionAddEvent(
            event.messageId,
            event.guild.id,
            event.channel.id,
            PermissionUtil.getEffectivePermission(event.channel, event.guild.selfMember).toString(),
            PermissionUtil.getEffectivePermission(event.channel, event.member).toString(),
            event.member.id,
            event.reactionEmote.asReactionCode,
            event.reactionEmote.isEmoji,
            event.member.toEntity()
        ))
    }

    override fun onRawGateway(event: RawGatewayEvent) {
        log.info("RawGatewayEvent ${event.`package`.toPrettyString()}")
    }

    override fun onSlashCommand(event: SlashCommandEvent) {
        if (event.guild == null) return
        if (event.member == null) return

        if (SocketServer.subscriptionsCache.contains(event.guild!!.idLong)) {
            updateGuild(event.guild!!)
        }

        event.deferReply().queue()
        val channel = event.jda.getGuildChannelById(event.channel.id)
        dispatchSocket("slashCommandsEvent", SlashCommandsEvent(
            event.interaction.id,
            event.interaction.token,
            event.interaction.type.ordinal,
            event.guild!!.id,
            event.channel.id,
            PermissionUtil.getEffectivePermission(channel, event.guild!!.selfMember).toString(),
            PermissionUtil.getEffectivePermission(channel, event.member).toString(),
            event.commandPath,
            event.options.map { it.toEntity() },
            event.member!!.toEntity()
        ))
    }

    override fun onButtonClick(event: ButtonClickEvent) {
        if (event.guild == null) return
        if (event.member == null) return
        if (!SocketServer.subscriptionsCache.contains(event.guild!!.idLong)) return

        updateGuild(event.guild!!)
        event.deferEdit().queue()

        dispatchSocket("buttonEvent", ButtonEvent(
            event.interaction.id,
            event.interaction.token,
            event.interaction.type.ordinal,
            event.componentId,
            event.messageId,
            event.guild!!.id,
            event.channel.id,
            event.member!!.id,
            event.member!!.toEntity()
        ))
    }

    override fun onSelectionMenu(event: SelectionMenuEvent) {
        if (event.guild == null) return
        if (event.member == null) return
        if (!SocketServer.subscriptionsCache.contains(event.guild!!.idLong)) return

        updateGuild(event.guild!!)
        event.deferEdit().queue()

        dispatchSocket("selectionMenuEvent", SelectionMenuEvent(
            event.interaction.id,
            event.interaction.token,
            event.interaction.type.ordinal,
            event.values,
            event.componentId,
            event.messageId,
            event.guild!!.id,
            event.channel.id,
            event.member!!.id,
            event.member!!.toEntity()
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
        }
    }

    override fun onGenericTextChannel(event: GenericTextChannelEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return
        if (event is TextChannelDeleteEvent || event is TextChannelCreateEvent) {
            updateGuild(event.guild)
            return
        } else if (event is RoleUpdatePositionEvent || event is RoleUpdatePermissionsEvent) {
            updateChannelPermissions(event.guild)
        }
        dispatchSocket("textChannelUpdate", TextChannelUpdate(
            event.guild.id,
            event.channel.toEntity()
        ))
    }

    /** Note: voice state updates (join, move, leave, etc.) are not handled as [GenericVoiceChannelEvent] */
    override fun onGenericVoiceChannel(event: GenericVoiceChannelEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return
        if (event is VoiceChannelDeleteEvent || event is VoiceChannelCreateEvent) {
            updateGuild(event.guild)
            return
        } else if (event is VoiceChannelUpdatePositionEvent || event is RoleUpdatePermissionsEvent) {
            updateChannelPermissions(event.guild)
        }
        dispatchSocket("voiceChannelUpdate", VoiceChannelUpdate(
            event.guild.id,
            event.channel.toEntity()
        ))
    }

    override fun onGenericCategory(event: GenericCategoryEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return
        if (event is CategoryDeleteEvent || event is CategoryCreateEvent) {
            updateGuild(event.guild)
            return
        } else if (event is CategoryUpdatePositionEvent || event is GenericPermissionOverrideEvent) {
            updateChannelPermissions(event.guild)
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
        dispatchSocket("guildUpdateEvent", GuildUpdateEvent(guild.toEntity(SocketServer.voiceServerUpdateCache)))
    }

    private fun updateChannelPermissions(guild: Guild) {
        val permissions = mutableMapOf<String, String>()
        val self = guild.selfMember
        val func = { channel: GuildChannel ->
            permissions[channel.id] = PermissionUtil.getEffectivePermission(channel, self).toString()
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
            log.warn("Unsuccessful JDA HTTP Request:\n{}\nResponse:{}\n", event.requestRaw, event.responseRaw)
        }
    }

    /* Util */

    private fun dispatchSocket(eventName: String, event: Any) {
        log.info("Sent $eventName to ${socketClient.sessionId}")
        socketClient.sendEvent(eventName, event)
    }

    fun removeListener() {
        shardManager.removeEventListener(this)
    }
}