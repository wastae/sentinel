/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.io

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.jda.RemoteSessionController
import com.fredboat.sentinel.rpc.*
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class WebSocketHandlers(
    private val audio: AudioRequests,
    private val info: InfoRequests,
    private val management: ManagementRequests,
    private val message: MessageRequests,
    private val permission: PermissionRequests,
    private val subscription: SubscriptionHandler,
    private val sessionController: RemoteSessionController,
    private val fanoutConsumer: FanoutConsumer
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(WebSocketHandlers::class.java)
    }

    fun consume(context: SocketContext, request: JSONObject) {
        val `object` = request.getJSONObject("object").toString()

        when (request.getString("eventType")) {
            // Audio
            AudioQueueRequest::class.java.simpleName -> audio.consume(context.gson.fromJson(`object`, AudioQueueRequest::class.java))

            // Info
            GuildsRequest::class.java.simpleName -> info.consume(context.gson.fromJson(`object`, GuildsRequest::class.java), context)
            GuildInfoRequest::class.java.simpleName -> info.consume(context.gson.fromJson(`object`, GuildInfoRequest::class.java), context)
            RoleInfoRequest::class.java.simpleName -> info.consume(context.gson.fromJson(`object`, RoleInfoRequest::class.java), context)
            FindMembersByRoleRequest::class.java.simpleName -> info.consume(context.gson.fromJson(`object`, FindMembersByRoleRequest::class.java), context)
            GetMembersByPrefixRequest::class.java.simpleName -> info.consume(context.gson.fromJson(`object`, GetMembersByPrefixRequest::class.java), context)
            GetMembersByIdsRequest::class.java.simpleName -> info.consume(context.gson.fromJson(`object`, GetMembersByIdsRequest::class.java), context)
            MemberInfoRequest::class.java.simpleName -> info.consume(context.gson.fromJson(`object`, MemberInfoRequest::class.java), context)
            GetMemberRequest::class.java.simpleName -> info.consume(context.gson.fromJson(`object`, GetMemberRequest::class.java), context)
            UserInfoRequest::class.java.simpleName -> info.consume(context.gson.fromJson(`object`, UserInfoRequest::class.java), context)
            GetUserRequest::class.java.simpleName -> info.consume(context.gson.fromJson(`object`, GetUserRequest::class.java), context)

            // Management
            //ModRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, ModRequest::class.java))
            SetAvatarRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, SetAvatarRequest::class.java))
            ReviveShardRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, ReviveShardRequest::class.java))
            LeaveGuildRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, LeaveGuildRequest::class.java))
            GetPingRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, GetPingRequest::class.java), context)
            SentinelInfoRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, SentinelInfoRequest::class.java), context)
            //UserListRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, UserListRequest::class.java), context)
            //BanListRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, BanListRequest::class.java), context)
            RemoveSlashCommandsRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, RemoveSlashCommandsRequest::class.java))
            RemoveSlashCommandRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, RemoveSlashCommandRequest::class.java))
            RegisterSlashCommandRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, RegisterSlashCommandRequest::class.java))
            RegisterContextCommandRequest::class.java.simpleName -> management.consume(context.gson.fromJson(`object`, RegisterContextCommandRequest::class.java))

            // Message
            SendMessageRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SendMessageRequest::class.java), context)
            //SendEmbedRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SendEmbedRequest::class.java), context)
            SendPrivateMessageRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SendPrivateMessageRequest::class.java), context)
            EditMessageRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, EditMessageRequest::class.java), context)
            //EditEmbedRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, EditEmbedRequest::class.java), context)
            AddReactionRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, AddReactionRequest::class.java))
            AddReactionsRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, AddReactionsRequest::class.java))
            RemoveReactionRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, RemoveReactionRequest::class.java))
            RemoveReactionsRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, RemoveReactionsRequest::class.java))
            MessageDeleteRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, MessageDeleteRequest::class.java))
            SendTypingRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SendTypingRequest::class.java))
            SendContextCommandRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SendContextCommandRequest::class.java))
            SendSlashCommandRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SendSlashCommandRequest::class.java), context)
            //SendSlashEmbedCommandRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SendSlashEmbedCommandRequest::class.java), context)
            //SendSlashMenuCommandRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SendSlashMenuCommandRequest::class.java), context)
            EditSlashCommandRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, EditSlashCommandRequest::class.java))
            SlashDeferReplyRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SlashDeferReplyRequest::class.java))
            SlashAutoCompleteRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SlashAutoCompleteRequest::class.java))
            //SendMessageButtonsRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SendMessageButtonsRequest::class.java))
            //SendMessageSelectionMenuRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, SendMessageSelectionMenuRequest::class.java))
            //EditButtonsRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, EditButtonsRequest::class.java))
            //EditSelectionMenuRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, EditSelectionMenuRequest::class.java))
            //RemoveComponentsRequest::class.java.simpleName -> message.consume(context.gson.fromJson(`object`, RemoveComponentsRequest::class.java))

            // Permission
            GuildPermissionRequest::class.java.simpleName -> permission.consume(context.gson.fromJson(`object`, GuildPermissionRequest::class.java), context)
            ChannelPermissionRequest::class.java.simpleName -> permission.consume(context.gson.fromJson(`object`, ChannelPermissionRequest::class.java), context)
            BulkGuildPermissionRequest::class.java.simpleName -> permission.consume(context.gson.fromJson(`object`, BulkGuildPermissionRequest::class.java), context)

            // Subscription
            GuildSubscribeRequest::class.java.simpleName -> subscription.consume(context.gson.fromJson(`object`, GuildSubscribeRequest::class.java), context)
            GuildUnsubscribeRequest::class.java.simpleName -> subscription.consume(context.gson.fromJson(`object`, GuildUnsubscribeRequest::class.java))

            // SessionController
            RunSessionRequest::class.java.simpleName -> sessionController.onRunRequest(context.gson.fromJson(`object`, RunSessionRequest::class.java), context)

            // Fanout
            FredBoatHello::class.java.simpleName -> fanoutConsumer.onHello(context.gson.fromJson(`object`, FredBoatHello::class.java), context)
            SyncSessionQueueRequest::class.java.simpleName -> fanoutConsumer.consume(context.gson.fromJson(`object`, SyncSessionQueueRequest::class.java))
            else -> log.warn("Unexpected request type: " + request.getString("eventType"))
        }
    } // 53 events total

    fun configureResuming(context: SocketContext, json: JSONObject) {
        context.resumeKey = json.optString("key", null)
        if (json.has("timeout")) context.resumeTimeout = json.getLong("timeout")
    }
}
