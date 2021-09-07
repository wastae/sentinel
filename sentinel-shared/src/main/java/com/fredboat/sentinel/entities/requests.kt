/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.fredboat.sentinel.entities

data class GuildsRequest(
        val shard: Int
)

data class GuildsResponse(
        val guilds: List<Long>) {

    override fun toString() = "GuildsResponse(guilds.size=${guilds.size})"
}

/** Returns [Guild]*/
data class GuildRequest(
        val id: Long
)

/** Returns [SendMessageResponse]*/
data class SendMessageRequest(
        val channel: Long,
        val message: String
)

/** Returns [SendMessageResponse]*/
data class SendEmbedRequest(
        val channel: Long,
        val embed: Embed
)

/** Returns [SendMessageResponse]*/
data class SendPrivateMessageRequest(
        val recipient: Long,
        val message: String
)

data class SendMessageResponse(
        val messageId: Long
)

/** Returns [Unit]*/
data class EditMessageRequest(
        val channel: Long,
        val messageId: Long,
        val message: String
)

/** Returns [EditEmbedResponse]*/
data class EditEmbedRequest(
        val channel: Long,
        val messageId: Long,
        val embed: Embed
)

data class EditEmbedResponse(
        val messageId: Long,
        val guildId: Long
)

/** Returns [Unit]*/
data class AddReactionRequest(
        val channel: Long,
        val messageId: Long,
        val emote: String
)

/** Returns [Unit]*/
data class AddReactionsRequest(
        val channel: Long,
        val messageId: Long,
        val emote: ArrayList<String>
)

/** Returns [Unit]*/
data class RemoveReactionRequest(
        val channel: Long,
        val messageId: Long,
        val userId: Long,
        val emote: String
)

/** Returns [Unit]*/
data class RemoveReactionsRequest(
        val channel: Long,
        val messageId: Long
)

/** Returns [Unit]*/
data class MessageDeleteRequest(
        val channel: Long,
        val messages: List<Long>
)

/** Returns [Unit]*/
data class SendTypingRequest(
        val channel: Long
)

/** Returns [Unit]*/
data class SelectMenuRequest(
        val channel: Long,
        val options: SelectOpt
)

/** Returns [PermissionCheckResponse]*/
data class GuildPermissionRequest(
        val guild: Long,
        val role: Long? = null,  // If present, the role to check (mutually exclusive)
        val member: Long? = null,// If present, the member to check (mutually exclusive)
        val rawPermissions: Long
){
    init {
        if (role != null && member != null) throw RuntimeException("Role and member are mutually exclusive")
    }
}

/** Returns [PermissionCheckResponse]*/
data class ChannelPermissionRequest(
        val channel: Long, // The channel to check
        val role: Long? = null,  // If present, the role to check (mutually exclusive)
        val member: Long? = null,// If present, the member to check (mutually exclusive)
        val rawPermissions: Long
){
    init {
        if (role != null && member != null) throw RuntimeException("Role and member are mutually exclusive")
    }
}

data class PermissionCheckResponse(
        val effective: Long,
        val missing: Long,
        val missingEntityFault: Boolean
)

/* Extension because of serialization problems */
@Suppress("unused")
val PermissionCheckResponse.passed: Boolean
    get() = !missingEntityFault && missing == 0L

/** Returns [BulkGuildPermissionRequest]*/
data class BulkGuildPermissionRequest(
        val guild: Long,
        val members: List<Long>
)

data class BulkGuildPermissionResponse(
        val effectivePermissions: List<Long?>
)

/** Returns [SentinelInfoResponse] */
data class SentinelInfoRequest(val includeShards: Boolean)

/** Data about all shards */
data class SentinelInfoResponse(
        val guilds: Long,
        val users: Long,
        val roles: Long,
        val categories: Long,
        val textChannels: Long,
        val voiceChannels: Long,
        val shards: List<ExtendedShardInfo>?
)

/** For the ;;shards command */
data class ExtendedShardInfo(
        val shard: Shard,
        val guilds: Int,
        val users: Int
)

/** Dump all user IDs to a [List] with [Long]s */
class UserListRequest