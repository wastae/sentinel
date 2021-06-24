/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

@Service
@RabbitListener(queues = ["#{requestQueue.name}"], errorHandler = "rabbitListenerErrorHandler", concurrency = "200")
class DirectConsumer(
    private val audio: AudioRequests,
    private val info: InfoRequests,
    private val management: ManagementRequests,
    private val message: MessageRequests,
    private val permission: PermissionRequests,
    private val subscription: SubscriptionHandler
) {

    @RabbitHandler fun consume(request: AudioQueueRequest) = audio.consume(request)

    @RabbitHandler fun consume(request: GuildsRequest) = info.consume(request)
    @RabbitHandler fun consume(request: GuildInfoRequest) = info.consume(request)
    @RabbitHandler fun consume(request: RoleInfoRequest) = info.consume(request)
    @RabbitHandler fun consume(request: GetMembersByPrefixRequest) = info.consume(request)
    @RabbitHandler fun consume(request: GetMembersByIdsRequest) = info.consume(request)
    @RabbitHandler fun consume(request: MemberInfoRequest) = info.consume(request)
    @RabbitHandler fun consume(request: GetMemberRequest) = info.consume(request)
    @RabbitHandler fun consume(request: UserInfoRequest) = info.consume(request)
    @RabbitHandler fun consume(request: GetUserRequest) = info.consume(request)

    @RabbitHandler fun consume(request: ModRequest) = management.consume(request)
    @RabbitHandler fun consume(request: SetAvatarRequest) = management.consume(request)
    @RabbitHandler fun consume(request: ReviveShardRequest) = management.consume(request)
    @RabbitHandler fun consume(request: LeaveGuildRequest) = management.consume(request)
    @RabbitHandler fun consume(request: GetPingRequest) = management.consume(request)
    @RabbitHandler fun consume(request: SentinelInfoRequest) = management.consume(request)
    @RabbitHandler fun consume(request: RunSessionRequest) = management.consume(request)
    @RabbitHandler fun consume(request: UserListRequest) = management.consume(request)
    @RabbitHandler fun consume(request: BanListRequest) = management.consume(request)
    @RabbitHandler fun consume(request: EvalRequest) = management.consume(request)

    @RabbitHandler fun consume(request: SendMessageRequest) = message.consume(request)
    @RabbitHandler fun consume(request: SendEmbedRequest) = message.consume(request)
    @RabbitHandler fun consume(request: SendPrivateMessageRequest) = message.consume(request)
    @RabbitHandler fun consume(request: EditMessageRequest) = message.consume(request)
    @RabbitHandler fun consume(request: EditEmbedRequest) = message.consume(request)
    @RabbitHandler fun consume(request: AddReactionRequest) = message.consume(request)
    @RabbitHandler fun consume(request: AddReactionsRequest) = message.consume(request)
    @RabbitHandler fun consume(request: RemoveReactionRequest) = message.consume(request)
    @RabbitHandler fun consume(request: RemoveReactionsRequest) = message.consume(request)
    @RabbitHandler fun consume(request: MessageDeleteRequest) = message.consume(request)
    @RabbitHandler fun consume(request: SendTypingRequest) = message.consume(request)

    @RabbitHandler fun consume(request: GuildPermissionRequest) = permission.consume(request)
    @RabbitHandler fun consume(request: ChannelPermissionRequest) = permission.consume(request)
    @RabbitHandler fun consume(request: BulkGuildPermissionRequest) = permission.consume(request)

    @RabbitHandler fun consume(request: GuildSubscribeRequest) = subscription.consume(request)
    @RabbitHandler fun consume(request: GuildUnsubscribeRequest) = subscription.consume(request)
}