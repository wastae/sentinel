/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.entities

/** Returns [Guild] as well as jda */
data class GuildSubscribeRequest(
    val id: String,
    val requestTime: String,
    val shardId: String,
    /** Optionally a channel to post a warning in, if the guild is very large */
    val channelInvoked: String?,
    val responseId: String
)

/** Sent when the [Guild] gets uncached */
data class GuildUnsubscribeRequest(
    val id: String
)

data class GuildUpdateLiteEvent(
    val guild: GuildLite
)

/* Updates */

data class GuildMemberCreate(
    val guild: String,
    val member: Member
)

data class GuildMemberDelete(
    val guild: String,
    val member: Member
)

data class GuildMemberUpdate(
    val guild: String,
    val member: Member
)

data class RoleCreate(
    val guild: String,
    val role: Role
)

data class RoleDelete(
    val guild: String,
    val role: Role
)

data class RoleUpdate(
    val guild: String,
    val role: Role
)

data class TextChannelCreate(
    val guild: String,
    val channel: TextChannel
)

data class TextChannelDelete(
    val guild: String,
    val channel: TextChannel
)

data class TextChannelUpdate(
    val guild: String,
    val channel: TextChannel
)

data class VoiceChannelCreate(
    val guild: String,
    val channel: VoiceChannel
)

data class VoiceChannelDelete(
    val guild: String,
    val channel: VoiceChannel
)

data class VoiceChannelUpdate(
    val guild: String,
    val channel: VoiceChannel
)

data class ChannelPermissionsUpdate(
    val guild: String,
    val changes: Map<String, String>
)