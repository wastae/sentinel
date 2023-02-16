package com.fredboat.sentinel.io

import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.config.SentinelProperties
import com.fredboat.sentinel.entities.ButtonEvent
import com.fredboat.sentinel.entities.ChannelPermissionsUpdate
import com.fredboat.sentinel.entities.ContextCommandsEvent
import com.fredboat.sentinel.entities.GuildMemberCreate
import com.fredboat.sentinel.entities.GuildMemberDelete
import com.fredboat.sentinel.entities.GuildMemberUpdate
import com.fredboat.sentinel.entities.GuildUpdateLiteEvent
import com.fredboat.sentinel.entities.LifecycleEventEnum
import com.fredboat.sentinel.entities.PrivateMessageReceivedEvent
import com.fredboat.sentinel.entities.RoleCreate
import com.fredboat.sentinel.entities.RoleDelete
import com.fredboat.sentinel.entities.RoleUpdate
import com.fredboat.sentinel.entities.SelectionMenuEvent
import com.fredboat.sentinel.entities.ShardLifecycleEvent
import com.fredboat.sentinel.entities.ShardStatusChange
import com.fredboat.sentinel.entities.SlashAutoCompleteEvent
import com.fredboat.sentinel.entities.SlashCommandsEvent
import com.fredboat.sentinel.entities.TextChannelCreate
import com.fredboat.sentinel.entities.TextChannelDelete
import com.fredboat.sentinel.entities.TextChannelUpdate
import com.fredboat.sentinel.entities.VoiceChannelCreate
import com.fredboat.sentinel.entities.VoiceChannelDelete
import com.fredboat.sentinel.entities.VoiceChannelUpdate
import com.fredboat.sentinel.entities.VoiceJoinEvent
import com.fredboat.sentinel.entities.VoiceLeaveEvent
import com.fredboat.sentinel.entities.VoiceMoveEvent
import com.fredboat.sentinel.jda.SubscriptionCache
import com.fredboat.sentinel.jda.VoiceServerUpdateCache
import com.fredboat.sentinel.metrics.Counters
import com.fredboat.sentinel.rpc.FanoutConsumer
import com.fredboat.sentinel.util.toEntity
import com.fredboat.sentinel.util.toEntityLite
import com.fredboat.sentinel.util.toTextEntity
import com.fredboat.sentinel.util.toVoiceEntity
import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFrame
import io.undertow.websockets.core.WebSocketCallback
import io.undertow.websockets.core.WebSocketChannel
import io.undertow.websockets.core.WebSockets
import io.undertow.websockets.jsr.UndertowSession
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.RawGatewayEvent
import net.dv8tion.jda.api.events.StatusChangeEvent
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent
import net.dv8tion.jda.api.events.channel.GenericChannelEvent
import net.dv8tion.jda.api.events.channel.update.ChannelUpdatePositionEvent
import net.dv8tion.jda.api.events.guild.GenericGuildEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.guild.override.GenericPermissionOverrideEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateOwnerEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.http.HttpRequestEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.role.GenericRoleEvent
import net.dv8tion.jda.api.events.role.RoleCreateEvent
import net.dv8tion.jda.api.events.role.RoleDeleteEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdatePositionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent
import net.dv8tion.jda.api.events.session.SessionRecreateEvent
import net.dv8tion.jda.api.events.session.SessionResumeEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.utils.PermissionUtil
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession

class SocketContext internal constructor(
    private val sentinelProperties: SentinelProperties,
    private val key: RoutingKey,
    val gson: Gson,
    subscriptionCache: SubscriptionCache,
    private val shardManager: ShardManager,
    private val socketServer: SocketServer,
    private var session: WebSocketSession,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SocketContext::class.java)
    }

    @Volatile
    var sessionPaused = false
    private val resumeEventQueue = ConcurrentLinkedQueue<String>()

    var resumeKey: String? = null
    var resumeTimeout = 60L // Seconds
    private var sessionTimeoutFuture: ScheduledFuture<Unit>? = null
    private var jdaWebsocketEventListener: JdaWebsocketEventListener?
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    init {
        jdaWebsocketEventListener = JdaWebsocketEventListener(gson, subscriptionCache)
        shardManager.addEventListener(jdaWebsocketEventListener)

        send(JSONObject().put("op", "initial").put("routingKey", key))
        FanoutConsumer.sendHello(this, sentinelProperties, key)
    }

    fun pause() {
        sessionPaused = true
        sessionTimeoutFuture = executor.schedule<Unit>({
            socketServer.onSessionResumeTimeout(this)
        }, resumeTimeout, TimeUnit.SECONDS)
    }

    fun sendResponse(
        eventType: String,
        eventObject: String,
        responseId: String = "0",
        jsonResponse: Boolean = true,
        successful: Boolean = true
    ) {
        val out = JSONObject()
        out.put("op", "response")
        out.put("routingKey", key)
        out.put("responseId", responseId)
        out.put("jsonObject", jsonResponse)
        out.put("successful", successful)
        out.put("type", eventType)
        out.put("object", if (jsonResponse) JSONObject(eventObject) else eventObject)

        send(out)
    }

    private fun sendEvent(eventType: String, eventObject: String) {
        val out = JSONObject()
        out.put("op", "event")
        out.put("routingKey", key)
        out.put("type", eventType)
        out.put("object", JSONObject(eventObject))

        send(out)
    }

    /**
     * Either sends the payload now or queues it up
     */
    private fun send(payload: JSONObject) = send(payload.toString())

    private fun send(payload: String) {
        if (sessionPaused) {
            resumeEventQueue.add(payload)
            return
        }

        if (!session.isOpen) return

        val undertowSession = (session as StandardWebSocketSession).nativeSession as UndertowSession
        WebSockets.sendText(payload, undertowSession.webSocketChannel,
            object : WebSocketCallback<Void> {
                override fun complete(channel: WebSocketChannel, context: Void?) {
                    log.debug("Sent {}", payload)
                }

                override fun onError(channel: WebSocketChannel, context: Void?, throwable: Throwable) {
                    log.error("Error", throwable)
                }
            }
        )
    }

    /**
     * @return true if we can resume, false otherwise
     */
    fun stopResumeTimeout() = sessionTimeoutFuture?.cancel(false) ?: false

    fun resume(session: WebSocketSession) {
        sessionPaused = false
        this.session = session
        send(JSONObject().put("op", "initial").put("routingKey", key))
        FanoutConsumer.sendHello(this, sentinelProperties, key)
        log.info("Replaying ${resumeEventQueue.size} events")

        // Bulk actions are not guaranteed to be atomic, so we need to do this imperatively
        while (resumeEventQueue.isNotEmpty()) {
            send(resumeEventQueue.remove())
        }
    }

    internal fun shutdown() {
        log.info("Shutting down connection for session ${session.id}")
        shardManager.removeEventListener(jdaWebsocketEventListener)
        jdaWebsocketEventListener = null
    }

    private inner class JdaWebsocketEventListener(
        private val gson: Gson,
        private val subscriptionCache: SubscriptionCache
    ) : ListenerAdapter() {

        /* Shard lifecycle */

        override fun onStatusChange(event: StatusChangeEvent) = event.run {
            log.info("Shard ${jda.shardInfo.shardId}: $oldStatus -> $newStatus")
            sendEvent(ShardStatusChange::class.java.simpleName, gson.toJson(ShardStatusChange(jda.toEntity())))
        }

        override fun onReady(event: ReadyEvent) {
            sendEvent(ShardLifecycleEvent::class.java.simpleName, gson.toJson(ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.READIED)))
        }

        override fun onSessionDisconnect(event: SessionDisconnectEvent) {
            sendEvent(ShardLifecycleEvent::class.java.simpleName, gson.toJson(ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.DISCONNECTED)))

            val frame: WebSocketFrame? = if (event.isClosedByServer)
                event.serviceCloseFrame else event.clientCloseFrame

            val prefix = if (event.isClosedByServer) "s" else "c"
            val code = "$prefix${frame?.closeCode}"

            log.warn("Shard ${event.jda.shardInfo.shardId} closed. {} {}", code, frame?.closeReason)
            Counters.shardDisconnects.labels(code).inc()
        }

        override fun onSessionResume(event: SessionResumeEvent) =
            sendEvent(ShardLifecycleEvent::class.java.simpleName, gson.toJson(ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.RESUMED)))


        override fun onSessionRecreate(event: SessionRecreateEvent) =
            sendEvent(ShardLifecycleEvent::class.java.simpleName, gson.toJson(ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.RECONNECTED)))


        override fun onShutdown(event: ShutdownEvent) =
            sendEvent(ShardLifecycleEvent::class.java.simpleName, gson.toJson(ShardLifecycleEvent(event.jda.toEntity(), LifecycleEventEnum.SHUTDOWN)))


        /* Guild jda */

        override fun onGuildJoin(event: GuildJoinEvent) {
            sendEvent(com.fredboat.sentinel.entities.GuildJoinEvent::class.java.simpleName, gson.toJson(com.fredboat.sentinel.entities.GuildJoinEvent(
                event.guild.id,
                event.guild.locale.locale
            )))
        }

        override fun onGuildLeave(event: GuildLeaveEvent) {
            sendEvent(com.fredboat.sentinel.entities.GuildLeaveEvent::class.java.simpleName, gson.toJson(com.fredboat.sentinel.entities.GuildLeaveEvent(
                event.guild.id,
                (event.guild.selfMember.timeJoined.toEpochSecond() * 1000).toString()
            )))
        }

        override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
            sendEvent(GuildMemberCreate::class.java.simpleName, gson.toJson(GuildMemberCreate(
                event.guild.id,
                event.member.toEntity()
            )))
        }

        override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
            if (event.member == null) return

            sendEvent(GuildMemberDelete::class.java.simpleName, gson.toJson(GuildMemberDelete(
                event.guild.id,
                event.member!!.toEntity()
            )))
        }

        override fun onGuildMemberUpdateAvatar(event: GuildMemberUpdateAvatarEvent) = onMemberChange(event.member)
        override fun onGuildMemberUpdateNickname(event: GuildMemberUpdateNicknameEvent) = onMemberChange(event.member)
        override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) = onMemberChange(event.member)
        override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) = onMemberChange(event.member)

        private fun onMemberChange(member: net.dv8tion.jda.api.entities.Member?) {
            if (member != null) {
                if (!subscriptionCache.contains(member.guild.idLong)) return

                updateChannelPermissions(member.guild)
                sendEvent(GuildMemberUpdate::class.java.simpleName, gson.toJson(GuildMemberUpdate(
                    member.guild.id,
                    member.toEntity()
                )))
            }
        }

        /* Voice jda */

        override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
            if (!subscriptionCache.contains(event.guild.idLong)) return

            if (event.channelJoined == null && event.channelLeft != null) {
                sendEvent(VoiceLeaveEvent::class.java.simpleName, gson.toJson(VoiceLeaveEvent(
                    event.guild.id,
                    event.channelLeft!!.id,
                    event.member.toEntity()
                )))
            } else if (event.channelJoined != null && event.channelLeft == null) {
                requestSpeak(event)
                sendEvent(VoiceJoinEvent::class.java.simpleName, gson.toJson(VoiceJoinEvent(
                    event.guild.id,
                    event.channelJoined!!.id,
                    event.member.toEntity()
                )))
            } else if (event.channelJoined != null && event.channelLeft != null) {
                requestSpeak(event)
                sendEvent(VoiceMoveEvent::class.java.simpleName, gson.toJson(VoiceMoveEvent(
                    event.guild.id,
                    event.channelLeft!!.id,
                    event.channelJoined!!.id,
                    event.member.toEntity()
                )))
            }
        }

        private fun requestSpeak(event: GuildVoiceUpdateEvent) {
            if (event.channelJoined!!.type == ChannelType.STAGE && event.member.user.idLong == event.guild.selfMember.user.idLong) {
                event.guild.requestToSpeak()
            }
        }

        /* Message jda */

        override fun onMessageReceived(event: MessageReceivedEvent) {
            if (event.message.type != MessageType.DEFAULT) return
            if (event.isWebhookMessage) return
            if (event.isFromGuild && !event.channelType.isThread && !event.channelType.isAudio) {
                sendEvent(com.fredboat.sentinel.entities.MessageReceivedEvent::class.java.simpleName, gson.toJson(com.fredboat.sentinel.entities.MessageReceivedEvent(
                    event.message.id,
                    event.message.guild.id,
                    event.channel.id,
                    PermissionUtil.getEffectivePermission((event.channel as GuildChannel).permissionContainer, event.guild.selfMember).toString(),
                    PermissionUtil.getEffectivePermission((event.channel as GuildChannel).permissionContainer, event.message.member).toString(),
                    event.message.contentRaw,
                    event.author.id,
                    event.author.isBot,
                    event.message.attachments.map { if (it.isImage) it.proxyUrl else it.url }
                )))
            } else if (!event.isFromGuild) {
                sendEvent(PrivateMessageReceivedEvent::class.java.simpleName, gson.toJson(PrivateMessageReceivedEvent(
                    event.message.contentRaw,
                    event.author.toEntity()
                )))
            }
        }

        override fun onMessageDelete(event: MessageDeleteEvent) {
            sendEvent(com.fredboat.sentinel.entities.MessageDeleteEvent::class.java.simpleName, gson.toJson(com.fredboat.sentinel.entities.MessageDeleteEvent(
                event.messageId,
                event.guild.id,
                event.channel.id
            )))
        }

        override fun onMessageReactionAdd(event: MessageReactionAddEvent) {
            if (event.isFromGuild && (event.channelType.isThread || event.channelType.isAudio)) return
            if (event.member == null) return
            if (!subscriptionCache.contains(event.guild.idLong)) return

            sendEvent(com.fredboat.sentinel.entities.MessageReactionAddEvent::class.java.simpleName, gson.toJson(com.fredboat.sentinel.entities.MessageReactionAddEvent(
                event.messageId,
                event.guild.id,
                event.channel.id,
                PermissionUtil.getEffectivePermission((event.channel as GuildChannel).permissionContainer, event.guild.selfMember).toString(),
                PermissionUtil.getEffectivePermission((event.channel as GuildChannel).permissionContainer, event.member).toString(),
                event.member!!.id,
                event.member!!.user.isBot,
                event.emoji.asReactionCode
            )))
        }

        override fun onRawGateway(event: RawGatewayEvent) {
            log.info("RawGatewayEvent ${event.`package`.toPrettyString()}")
        }

        override fun onMessageContextInteraction(event: MessageContextInteractionEvent) {
            if (event.user.isBot) return
            if (event.rawData == null) return

            sendEvent(ContextCommandsEvent::class.java.simpleName, gson.toJson(ContextCommandsEvent(
                event.rawData!!.toJson(),
                event.target.contentDisplay,
                event.user.id,
                event.userLocale.locale,
                key.key
            )))
        }

        override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
            if (!event.isFromGuild) {
                event.reply("Slash commands not supported in DM").setEphemeral(true).queue()
                return
            }
            if (event.isFromGuild && (event.channelType.isThread || event.channelType.isAudio)) {
                event.reply("Slash commands not supported in threads").setEphemeral(true).queue()
                return
            }
            if (event.guild == null) return
            if (event.member == null) return
            if (event.rawData == null) return

            sendEvent(SlashCommandsEvent::class.java.simpleName, gson.toJson(SlashCommandsEvent(
                event.rawData!!.toJson(),
                event.guild!!.id,
                event.channel.id,
                PermissionUtil.getEffectivePermission((event.channel as GuildChannel).permissionContainer, event.guild!!.selfMember).toString(),
                PermissionUtil.getEffectivePermission((event.channel as GuildChannel).permissionContainer, event.member).toString(),
                event.member!!.id,
                event.member!!.user.isBot,
                event.userLocale.locale,
                event.fullCommandName.replace(" ", "/"),
                event.options.map { it.toEntity() }
            )))
        }

        override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
            if (!event.isFromGuild) {
                event.replyChoiceStrings("Slash commands not supported in DM").queue()
                return
            }
            if (event.isFromGuild && (event.channelType.isThread || event.channelType.isAudio)) {
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

            sendEvent(SlashAutoCompleteEvent::class.java.simpleName, gson.toJson(SlashAutoCompleteEvent(
                event.rawData!!.toJson(),
                event.guild!!.id,
                event.channel!!.id,
                PermissionUtil.getEffectivePermission((event.channel as GuildChannel).permissionContainer, event.guild!!.selfMember).toString(),
                PermissionUtil.getEffectivePermission((event.channel as GuildChannel).permissionContainer, event.member).toString(),
                event.member!!.id,
                event.member!!.user.isBot,
                event.focusedOption.value
            )))
        }

        override fun onButtonInteraction(event: ButtonInteractionEvent) {
            if (event.guild == null) return
            if (event.member == null) return
            if (event.rawData == null) return
            if (!subscriptionCache.contains(event.guild!!.idLong)) return

            event.deferEdit().queue()
            sendEvent(ButtonEvent::class.java.simpleName, gson.toJson(ButtonEvent(
                event.rawData!!.toJson(),
                event.componentId,
                event.messageId,
                event.guild!!.id,
                event.channel.id,
                event.member!!.id,
                event.member!!.user.isBot
            )))
        }

        override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
            if (event.guild == null) return
            if (event.member == null) return
            if (event.rawData == null) return
            if (!subscriptionCache.contains(event.guild!!.idLong)) return

            event.deferEdit().queue()
            sendEvent(SelectionMenuEvent::class.java.simpleName, gson.toJson(SelectionMenuEvent(
                event.rawData!!.toJson(),
                event.values,
                event.componentId,
                event.messageId,
                event.guild!!.id,
                event.channel.id,
                event.member!!.id,
                event.member!!.user.isBot
            )))
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
            if (!subscriptionCache.contains(event.guild.idLong)) return
            if (event is GuildUpdateNameEvent || event is GuildUpdateOwnerEvent) {
                updateGuildLite(event.guild)
            } else if (event is GenericPermissionOverrideEvent && event.channel is TextChannel) {
                updateChannelPermissions(event.guild)
            } else if (event is GenericPermissionOverrideEvent && event.channel is VoiceChannel) {
                updateChannelPermissions(event.guild)
            }
        }

        override fun onGenericChannel(event: GenericChannelEvent) {
            if (!subscriptionCache.contains(event.guild.idLong)) return
            if (event is ChannelUpdatePositionEvent) {
                updateChannelPermissions(event.guild)
            } else if (event.channel is TextChannel) {
                when (event) {
                    is ChannelCreateEvent -> {
                        sendEvent(TextChannelCreate::class.java.simpleName, gson.toJson(TextChannelCreate(
                            event.guild.id,
                            (event.channel as TextChannel).toTextEntity()
                        )))
                    }
                    is ChannelDeleteEvent -> {
                        sendEvent(TextChannelDelete::class.java.simpleName, gson.toJson(TextChannelDelete(
                            event.guild.id,
                            (event.channel as TextChannel).toTextEntity()
                        )))
                    }
                    else -> {
                        sendEvent(TextChannelUpdate::class.java.simpleName, gson.toJson(TextChannelUpdate(
                            event.guild.id,
                            (event.channel as TextChannel).toTextEntity()
                        )))
                    }
                }
            } else if (event.channel is AudioChannel) {
                when (event) {
                    is ChannelCreateEvent -> {
                        sendEvent(
                            VoiceChannelCreate::class.java.simpleName, gson.toJson(VoiceChannelCreate(
                            event.guild.id,
                            (event.channel as AudioChannel).toVoiceEntity()
                        )))
                    }
                    is ChannelDeleteEvent -> {
                        sendEvent(VoiceChannelDelete::class.java.simpleName, gson.toJson(VoiceChannelDelete(
                            event.guild.id,
                            (event.channel as AudioChannel).toVoiceEntity()
                        )))
                    }
                    else -> {
                        sendEvent(VoiceChannelUpdate::class.java.simpleName, gson.toJson(VoiceChannelUpdate(
                            event.guild.id,
                            (event.channel as AudioChannel).toVoiceEntity()
                        )))
                    }
                }
            }
        }

        override fun onGenericRole(event: GenericRoleEvent) {
            if (!subscriptionCache.contains(event.guild.idLong)) return

            when (event) {
                is RoleCreateEvent -> {
                    sendEvent(RoleCreate::class.java.simpleName, gson.toJson(RoleCreate(
                        event.guild.id,
                        event.role.toEntity()
                    ))); return
                }
                is RoleDeleteEvent -> {
                    sendEvent(RoleDelete::class.java.simpleName, gson.toJson(RoleDelete(
                        event.guild.id,
                        event.role.toEntity()
                    ))); return
                }
                is RoleUpdatePositionEvent, is RoleUpdatePermissionsEvent -> {
                    updateChannelPermissions(event.guild)
                }
            }

            sendEvent(RoleUpdate::class.java.simpleName, gson.toJson(RoleUpdate(
                event.guild.id,
                event.role.toEntity()
            )))
        }

        private fun updateGuildLite(guild: Guild) {
            sendEvent(GuildUpdateLiteEvent::class.java.simpleName, gson.toJson(GuildUpdateLiteEvent(guild.toEntityLite())))
        }

        private fun updateChannelPermissions(guild: Guild) {
            val permissions = mutableMapOf<String, String>()
            val self = guild.selfMember
            val func = { channel: GuildChannel ->
                permissions[channel.id] = PermissionUtil.getEffectivePermission(channel.permissionContainer, self).toString()
            }

            guild.textChannels.forEach(func)
            guild.voiceChannels.forEach(func)

            sendEvent(ChannelPermissionsUpdate::class.java.simpleName, gson.toJson(ChannelPermissionsUpdate(
                guild.id,
                permissions
            )))
        }

        override fun onHttpRequest(event: HttpRequestEvent) {
            if (event.response!!.code >= 300) {
                log.warn("Unsuccessful JDA HTTP\nMethod: {} URL: {}\n{}", event.requestRaw?.method, event.requestRaw?.url, event.responseRaw)
            }
        }
    }
}