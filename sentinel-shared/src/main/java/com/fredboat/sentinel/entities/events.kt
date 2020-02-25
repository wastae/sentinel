/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.entities

/* Shard lifecycle */
data class ShardStatusChange(
        val shard: Shard
)

data class ShardLifecycleEvent(
        val shard: Shard,
        val change: LifecycleEventEnum
)

enum class LifecycleEventEnum {
    READIED,
    DISCONNECTED,
    RESUMED,
    RECONNECTED,
    SHUTDOWN
}

/* Guild leave/join */
data class GuildJoinEvent(
        val guild: Long
)

data class GuildLeaveEvent(
        val guild: Long,
        /** Millis */
        val joinTime: Long
)

/* Guild member jda  */
data class GuildMemberJoinEvent(
        val guild: Long,
        val member: Member
)

data class GuildMemberLeaveEvent(
        val guild: Long,
        val member: Long
)

/* Voice jda */
data class VoiceJoinEvent(
        val guild: Long,
        val channel: Long,
        val member: Long
)

data class VoiceLeaveEvent(
        val guild: Long,
        val channel: Long,
        val member: Long
)

data class VoiceMoveEvent(
        val guild: Long,
        val oldChannel: Long,
        val newChannel: Long,
        val member: Long
)

/* Messages */
data class MessageReceivedEvent(
        val id: Long,
        val guild: Long,
        val channel: Long,
        val channelPermissions: Long,
        val content: String,
        val author: Long,
        val fromBot: Boolean,
        val attachments: List<String>
)

data class PrivateMessageReceivedEvent(
        val content: String,
        val author: User
)

data class MessageDeleteEvent(
        val id: Long,
        val guild: Long,
        val channel: Long
)

data class MessageReactionAddEvent(
        val id: Long,
        val guild: Long,
        val channel: Long,
        val emote: Long,
        val isBot: Boolean
)