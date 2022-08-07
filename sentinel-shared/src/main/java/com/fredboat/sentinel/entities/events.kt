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
    val guild: String,
    val region: String
)

data class GuildLeaveEvent(
    val guild: String,
    /** Millis */
    val joinTime: String
)

/* Voice jda */
data class VoiceJoinEvent(
    val guild: String,
    val channel: String,
    val member: Member
)

data class VoiceLeaveEvent(
    val guild: String,
    val channel: String,
    val member: Member
)

data class VoiceMoveEvent(
    val guild: String,
    val oldChannel: String,
    val newChannel: String,
    val member: Member
)

/* Messages */
data class MessageReceivedEvent(
    val id: String,
    val guild: String,
    val channel: String,
    val channelPermissions: String,
    val memberPermissions: String,
    val content: String,
    val author: String,
    val fromBot: Boolean,
    val attachments: List<String>
)

data class PrivateMessageReceivedEvent(
    val content: String,
    val author: User
)

data class MessageDeleteEvent(
    val id: String,
    val guild: String,
    val channel: String
)

data class MessageReactionAddEvent(
    val messageId: String,
    val guild: String,
    val channel: String,
    val channelPermissions: String,
    val memberPermissions: String,
    val author: String,
    val fromBot: Boolean,
    val reaction: String
)

data class SlashCommandsEvent(
    val interaction: ByteArray,
    val guild: String,
    val channel: String,
    val channelPermissions: String,
    val memberPermissions: String,
    val author: String,
    val fromBot: Boolean,
    val locale: String,
    val command: String,
    val options: List<Option>
)

data class SlashAutoCompleteEvent(
    val interaction: ByteArray,
    val guild: String,
    val channel: String,
    val channelPermissions: String,
    val memberPermissions: String,
    val author: String,
    val fromBot: Boolean,
    val input: String
)

/**
 * Components
 */

data class ButtonEvent(
    val interaction: ByteArray,
    val componentId: String,
    val messageId: String,
    val guild: String,
    val channel: String,
    val author: String,
    val fromBot: Boolean
)

data class SelectionMenuEvent(
    val interaction: ByteArray,
    val selected: List<String>,
    val componentId: String,
    val messageId: String,
    val guild: String,
    val channel: String,
    val author: String,
    val fromBot: Boolean
)