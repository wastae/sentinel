/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.corundumstudio.socketio.SocketIOServer
import com.fredboat.sentinel.SocketServer
import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.jda.RemoteSessionController
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration

@Configuration
class DirectConsumer(
    private val audio: AudioRequests,
    private val info: InfoRequests,
    private val management: ManagementRequests,
    private val message: MessageRequests,
    private val permission: PermissionRequests,
    private val subscription: SubscriptionHandler,
    private val sessionController: RemoteSessionController,
    key: RoutingKey,
    socketIOServer: SocketIOServer
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DirectConsumer::class.java)
    }

    init {
        log.info("Event audioQueueRequest-${key.key} registered")
        socketIOServer.addEventListener("audioQueueRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            audio.consume(SocketServer.gson.fromJson(request.toString(), AudioQueueRequest::class.java))
        }

        //

        socketIOServer.addEventListener("guildsRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            info.consume(SocketServer.gson.fromJson(request.toString(), GuildsRequest::class.java), client)
        }
        socketIOServer.addEventListener("guildInfoRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            info.consume(SocketServer.gson.fromJson(request.toString(), GuildInfoRequest::class.java), client)
        }
        socketIOServer.addEventListener("roleInfoRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            info.consume(SocketServer.gson.fromJson(request.toString(), RoleInfoRequest::class.java), client)
        }
        socketIOServer.addEventListener("findMembersByRoleRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            info.consume(SocketServer.gson.fromJson(request.toString(), FindMembersByRoleRequest::class.java), client)
        }
        socketIOServer.addEventListener("getMembersByPrefixRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            info.consume(SocketServer.gson.fromJson(request.toString(), GetMembersByPrefixRequest::class.java), client)
        }
        socketIOServer.addEventListener("getMembersByIdsRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            info.consume(SocketServer.gson.fromJson(request.toString(), GetMembersByIdsRequest::class.java), client)
        }
        socketIOServer.addEventListener("memberInfoRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            info.consume(SocketServer.gson.fromJson(request.toString(), MemberInfoRequest::class.java), client)
        }
        socketIOServer.addEventListener("getMemberRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            info.consume(SocketServer.gson.fromJson(request.toString(), GetMemberRequest::class.java), client)
        }
        socketIOServer.addEventListener("userInfoRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            info.consume(SocketServer.gson.fromJson(request.toString(), UserInfoRequest::class.java), client)
        }
        socketIOServer.addEventListener("getUserRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            info.consume(SocketServer.gson.fromJson(request.toString(), GetUserRequest::class.java), client)
        }

        //

        socketIOServer.addEventListener("modRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            management.consume(SocketServer.gson.fromJson(request.toString(), ModRequest::class.java), client)
        }
        socketIOServer.addEventListener("setAvatarRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            management.consume(SocketServer.gson.fromJson(request.toString(), SetAvatarRequest::class.java))
        }
        socketIOServer.addEventListener("reviveShardRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            management.consume(SocketServer.gson.fromJson(request.toString(), ReviveShardRequest::class.java), client)
        }
        socketIOServer.addEventListener("leaveGuildRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            management.consume(SocketServer.gson.fromJson(request.toString(), LeaveGuildRequest::class.java))
        }
        socketIOServer.addEventListener("getPingRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            management.consume(SocketServer.gson.fromJson(request.toString(), GetPingRequest::class.java), client)
        }
        socketIOServer.addEventListener("sentinelInfoRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            management.consume(SocketServer.gson.fromJson(request.toString(), SentinelInfoRequest::class.java), client)
        }
        socketIOServer.addEventListener("userListRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            management.consume(SocketServer.gson.fromJson(request.toString(), UserListRequest::class.java), client)
        }
        socketIOServer.addEventListener("banListRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            management.consume(SocketServer.gson.fromJson(request.toString(), BanListRequest::class.java), client)
        }
        socketIOServer.addEventListener("evalRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            management.consume(SocketServer.gson.fromJson(request.toString(), EvalRequest::class.java), client)
        }

        //

        socketIOServer.addEventListener("sendMessageRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), SendMessageRequest::class.java), client)
        }
        socketIOServer.addEventListener("sendEmbedRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), SendEmbedRequest::class.java), client)
        }
        socketIOServer.addEventListener("sendPrivateMessageRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), SendPrivateMessageRequest::class.java), client)
        }
        socketIOServer.addEventListener("editMessageRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), EditMessageRequest::class.java))
        }
        socketIOServer.addEventListener("editEmbedRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), EditEmbedRequest::class.java), client)
        }
        socketIOServer.addEventListener("addReactionRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), AddReactionRequest::class.java))
        }
        socketIOServer.addEventListener("addReactionsRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), AddReactionsRequest::class.java))
        }
        socketIOServer.addEventListener("removeReactionRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), RemoveReactionRequest::class.java))
        }
        socketIOServer.addEventListener("removeReactionsRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), RemoveReactionsRequest::class.java))
        }
        socketIOServer.addEventListener("messageDeleteRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), MessageDeleteRequest::class.java))
        }
        socketIOServer.addEventListener("sendTypingRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), SendTypingRequest::class.java))
        }

        /**
         * Slash commands
         */

        socketIOServer.addEventListener("sendSlashCommandRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), SendSlashCommandRequest::class.java), client)
        }
        socketIOServer.addEventListener("sendSlashEmbedCommandRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), SendSlashEmbedCommandRequest::class.java), client)
        }
        socketIOServer.addEventListener("editSlashCommandRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), EditSlashCommandRequest::class.java))
        }
        socketIOServer.addEventListener("slashDeferReplyRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), SlashDeferReplyRequest::class.java))
        }
        socketIOServer.addEventListener("slashAutoCompleteRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), SlashAutoCompleteRequest::class.java))
        }
        socketIOServer.addEventListener("registerSlashCommandRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            management.consume(SocketServer.gson.fromJson(request.toString(), RegisterSlashCommandRequest::class.java))
        }

        /**
         * Components
         */

        socketIOServer.addEventListener("sendMessageButtonsRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), SendMessageButtonsRequest::class.java))
        }
        socketIOServer.addEventListener("sendMessageSelectionMenuRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), SendMessageSelectionMenuRequest::class.java))
        }
        socketIOServer.addEventListener("editButtonsRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), EditButtonsRequest::class.java))
        }
        socketIOServer.addEventListener("editSelectionMenuRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), EditSelectionMenuRequest::class.java))
        }
        socketIOServer.addEventListener("removeComponentsRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            message.consume(SocketServer.gson.fromJson(request.toString(), RemoveComponentsRequest::class.java))
        }

        //

        socketIOServer.addEventListener("guildPermissionRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            permission.consume(SocketServer.gson.fromJson(request.toString(), GuildPermissionRequest::class.java), client)
        }
        socketIOServer.addEventListener("channelPermissionRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            permission.consume(SocketServer.gson.fromJson(request.toString(), ChannelPermissionRequest::class.java), client)
        }
        socketIOServer.addEventListener("bulkGuildPermissionRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            permission.consume(SocketServer.gson.fromJson(request.toString(), BulkGuildPermissionRequest::class.java), client)
        }

        //

        socketIOServer.addEventListener("guildSubscribeRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            subscription.consume(SocketServer.gson.fromJson(request.toString(), GuildSubscribeRequest::class.java), client)
        }
        socketIOServer.addEventListener("guildUnsubscribeRequest-${key.key}", JSONObject::class.java) { _, request, _ ->
            subscription.consume(SocketServer.gson.fromJson(request.toString(), GuildUnsubscribeRequest::class.java))
        }

        //

        socketIOServer.addEventListener("runSessionRequest-${key.key}", JSONObject::class.java) { client, request, _ ->
            sessionController.onRunRequest(SocketServer.gson.fromJson(request.toString(), RunSessionRequest::class.java), client)
        }

        log.info("All events in DirectConsumer registered")
    } // 45 events total
}