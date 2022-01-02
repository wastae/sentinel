/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.fredboat.sentinel.entities

data class GuildsRequest(
        val shard: Int,
        val responseId: String
)

data class GuildsResponse(
        val guilds: List<String>) {

    override fun toString() = "GuildsResponse(guilds.size=${guilds.size})"
}

/** Returns [Guild]*/
data class GuildRequest(
        val id: String
)

/** Returns [SendMessageResponse]*/
data class SendMessageRequest(
        val channel: String,
        val message: String,
        val responseId: String
)

/** Returns [SendMessageResponse]*/
data class SendEmbedRequest(
        val channel: String,
        val embed: Embed,
        val responseId: String
)

/** Returns [SendMessageResponse]*/
data class SendPrivateMessageRequest(
        val recipient: String,
        val message: String,
        val responseId: String
)

data class SendMessageResponse(
        val messageId: String
)

/** Returns [Unit]*/
data class EditMessageRequest(
        val channel: String,
        val messageId: String,
        val message: String
)

/** Returns [EditEmbedResponse]*/
data class EditEmbedRequest(
        val channel: String,
        val messageId: String,
        val embed: Embed,
        val responseId: String
)

data class EditEmbedResponse(
        val messageId: String,
        val guildId: String
)

/** Returns [Unit]*/
data class AddReactionRequest(
        val channel: String,
        val messageId: String,
        val emote: String
)

/** Returns [Unit]*/
data class AddReactionsRequest(
        val channel: String,
        val messageId: String,
        val emote: ArrayList<String>
)

/** Returns [Unit]*/
data class RemoveReactionRequest(
        val channel: String,
        val messageId: String,
        val userId: String,
        val emote: String
)

/** Returns [Unit]*/
data class RemoveReactionsRequest(
        val channel: String,
        val messageId: String
)

/** Returns [Unit]*/
data class MessageDeleteRequest(
        val channel: String,
        val messages: List<String>
)

/** Returns [Unit]*/
data class SendTypingRequest(
        val channel: String
)

/** Returns [SendMessageResponse]*/
data class SendSlashCommandRequest(
        val interactionId: String,
        val interactionToken: String,
        val interactionType: Int,
        val guildId: String,
        val channelId: String,
        val userId: String,
        val message: String,
        val ephemeral: Boolean,
        val responseId: String
)

/**
 * Components
 */

/** Returns [Unit]*/
data class SendMessageButtonsRequest(
        val channel: String,
        val message: String,
        val buttons: Buttons
)

/** Returns [Unit]*/
data class SendMessageSelectionMenuRequest(
        val channel: String,
        val message: String,
        val menu: SelectMenu
)

/** Returns [Unit]*/
data class EditButtonsRequest(
        val channel: String,
        val messageId: String,
        val buttons: Buttons
)

/** Returns [Unit]*/
data class EditSelectionMenuRequest(
        val channel: String,
        val messageId: String,
        val menu: SelectMenu
)

/** Returns [Unit]*/
data class RemoveComponentsRequest(
        val channel: String,
        val messageId: String
)

/** Returns [PermissionCheckResponse]*/
data class GuildPermissionRequest(
        val guild: String,
        val role: String? = null,  // If present, the role to check (mutually exclusive)
        val member: String? = null,// If present, the member to check (mutually exclusive)
        val rawPermissions: String,
        val responseId: String
){
    init {
        if (role != null && member != null) throw RuntimeException("Role and member are mutually exclusive")
    }
}

/** Returns [PermissionCheckResponse]*/
data class ChannelPermissionRequest(
        val channel: String, // The channel to check
        val role: String? = null,  // If present, the role to check (mutually exclusive)
        val member: String? = null,// If present, the member to check (mutually exclusive)
        val rawPermissions: String,
        val responseId: String
){
    init {
        if (role != null && member != null) throw RuntimeException("Role and member are mutually exclusive")
    }
}

data class PermissionCheckResponse(
        val effective: String,
        val missing: String,
        val missingEntityFault: Boolean
)

/* Extension because of serialization problems */
@Suppress("unused")
val PermissionCheckResponse.passed: Boolean
    get() = !missingEntityFault && missing == "0"

/** Returns [BulkGuildPermissionRequest]*/
data class BulkGuildPermissionRequest(
        val guild: String,
        val members: List<String>,
        val responseId: String
)

data class BulkGuildPermissionResponse(
        val effectivePermissions: List<String?>
)

/** Returns [SentinelInfoResponse] */
data class SentinelInfoRequest(val includeShards: Boolean, val responseId: String)

/** Data about all shards */
data class SentinelInfoResponse(
        val guilds: String,
        val users: String,
        val roles: String,
        val categories: String,
        val textChannels: String,
        val voiceChannels: String,
        val shards: List<ExtendedShardInfo>?
)

/** For the ;;shards command */
data class ExtendedShardInfo(
        val shard: Shard,
        val guilds: Int,
        val users: Int
)

/** Dump all user IDs to a [List] with [String]s */
class UserListRequest(val responseId: String)