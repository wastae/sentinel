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
import com.fredboat.sentinel.redis.repositories.GuildsRepository
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
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.utils.PermissionUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue

class JdaWebsocketEventListener(
    private val shardManager: ShardManager,
    private val voiceServerUpdateCache: VoiceServerUpdateCache,
    private val guildsRepository: GuildsRepository,
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
            event.guild.locale.locale
        ))
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        dispatchSocket("guildLeaveEvent", GuildLeaveEvent(
            event.guild.id,
            (event.guild.selfMember.timeJoined.toEpochSecond() * 1000).toString()
        ))
    }

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) = onMemberChange(event.member)
    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) = onMemberChange(event.member)
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) = onMemberChange(event.member, event.rawData)
    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) = onMemberChange(event.member, event.rawData)

    private fun onMemberChange(member: net.dv8tion.jda.api.entities.Member?, data: DataObject? = null) {
        CompletableFuture.runAsync {
            if (data != null) {
                val guildId = data.getObject("d").getString("guild_id")
                if (!SocketServer.subscriptionsCache.contains(guildId.toLong())) return@runAsync

                try {
                    val type = data.getString("t")
                    val memberObject = data.getObject("d")
                    val userId = memberObject.getObject("user").getString("id")
                    val guildCache = guildsRepository.findById(guildId).get()

                    when (type) {
                        "GUILD_MEMBER_ADD" -> {
                            guildCache.memberList.add(RedisMember(memberObject.toETF()))
                        }
                        "GUILD_MEMBER_REMOVE" -> {
                            guildCache.memberList.remove(RedisMember(memberObject.toETF()))
                        }
                        else -> {
                            log.warn("Unknown type $type")
                            return@runAsync
                        }
                    }

                    log.info("Saving new guild cache for $guildId because of $type event for $userId")
                    guildsRepository.save(guildCache)
                } catch (e: NoSuchElementException) {
                    // ignore
                }
            }
        }

        if (member != null) {
            if (!SocketServer.subscriptionsCache.contains(member.guild.idLong)) return

            dispatchSocket("guildMemberUpdate", GuildMemberUpdate(
                member.guild.id,
                member.toEntity()
            ))
        }
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
            event.emoji.asReactionCode
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
        if (event.rawData == null) return

        dispatchSocket("slashCommandsEvent", SlashCommandsEvent(
            event.rawData!!.toJson(),
            event.guild!!.id,
            event.channel.id,
            PermissionUtil.getEffectivePermission((event.channel as TextChannel).permissionContainer, event.guild!!.selfMember).toString(),
            PermissionUtil.getEffectivePermission((event.channel as TextChannel).permissionContainer, event.member).toString(),
            event.member!!.id,
            event.member!!.user.isBot,
            event.userLocale.locale,
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
        if (event.rawData == null) return

        if (event.focusedOption.value.isEmpty()) {
            event.replyChoice("Empty request", "https://www.youtube.com/watch?v=dQw4w9WgXcQ").queue()
            return
        }

        dispatchSocket("autoCompleteEvent", SlashAutoCompleteEvent(
            event.rawData!!.toJson(),
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
        if (event.rawData == null) return
        if (!SocketServer.subscriptionsCache.contains(event.guild!!.idLong)) return

        event.deferEdit().queue()
        dispatchSocket("buttonEvent", ButtonEvent(
            event.rawData!!.toJson(),
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
        if (event.rawData == null) return
        if (!SocketServer.subscriptionsCache.contains(event.guild!!.idLong)) return

        event.deferEdit().queue()
        dispatchSocket("selectionMenuEvent", SelectionMenuEvent(
            event.rawData!!.toJson(),
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
        if (event is GuildUpdateNameEvent || event is GuildUpdateOwnerEvent) { // TODO Rework handling of this events
            //updateGuild(event.guild)
        } else if (event is GenericPermissionOverrideEvent && event.channel is TextChannel) {
            updateChannelPermissions(event.guild)
        } else if (event is GenericPermissionOverrideEvent && event.channel is VoiceChannel) {
            updateChannelPermissions(event.guild)
        }
    }

    override fun onGenericChannel(event: GenericChannelEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return
        if (event is ChannelUpdatePositionEvent) {
            updateChannelPermissions(event.guild)
        } else if (event.channel is TextChannel) {
            when (event) {
                is ChannelCreateEvent -> {
                    dispatchSocket("textChannelCreate", TextChannelCreate(
                        event.guild.id,
                        (event.channel as TextChannel).toEntity()
                    ))
                }
                is ChannelDeleteEvent -> {
                    dispatchSocket("textChannelDelete", TextChannelDelete(
                        event.guild.id,
                        (event.channel as TextChannel).toEntity()
                    ))
                }
                else -> {
                    dispatchSocket("textChannelUpdate", TextChannelUpdate(
                        event.guild.id,
                        (event.channel as TextChannel).toEntity()
                    ))
                }
            }
        } else if (event.channel is VoiceChannel) {
            when (event) {
                is ChannelCreateEvent -> {
                    dispatchSocket("voiceChannelCreate", VoiceChannelCreate(
                        event.guild.id,
                        (event.channel as VoiceChannel).toEntity()
                    ))
                }
                is ChannelDeleteEvent -> {
                    dispatchSocket("voiceChannelDelete", VoiceChannelDelete(
                        event.guild.id,
                        (event.channel as VoiceChannel).toEntity()
                    ))
                }
                else -> {
                    dispatchSocket("voiceChannelUpdate", VoiceChannelUpdate(
                        event.guild.id,
                        (event.channel as VoiceChannel).toEntity()
                    ))
                }
            }
        }
    }

    override fun onGenericRole(event: GenericRoleEvent) {
        if (!SocketServer.subscriptionsCache.contains(event.guild.idLong)) return

        when (event) {
            is RoleCreateEvent -> {
                dispatchSocket("roleCreate", RoleCreate(
                    event.guild.id,
                    event.role.toEntity()
                )); return
            }
            is RoleDeleteEvent -> {
                dispatchSocket("roleDelete", RoleDelete(
                    event.guild.id,
                    event.role.toEntity()
                )); return
            }
            is RoleUpdatePositionEvent, is RoleUpdatePermissionsEvent -> {
                updateChannelPermissions(event.guild)
            }
        }

        dispatchSocket("roleUpdate", RoleUpdate(
            event.guild.id,
            event.role.toEntity()
        ))
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