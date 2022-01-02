/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.entities

/* Shard lifecycle */
data class ShardStatusChange (
        val shard: Shard
)

data class ShardLifecycleEvent (
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

/* Guild member jda  */
data class GuildMemberJoinEvent(
        val guild: String,
        val member: Member
)

data class GuildMemberLeaveEvent(
        val guild: String,
        val member: String
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
        val attachments: List<String>,
        val member: Member,
        val mentionedMembers: List<Member>
)

data class PrivateMessageReceivedEvent (
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
        val reaction: String,
        val isEmoji: Boolean,
        val member: Member
)

data class SlashCommandsEvent(
        val interactionId: String,
        val interactionToken: String,
        val interactionType: Int,
        val guild: String,
        val channel: String,
        val channelPermissions: String,
        val memberPermissions: String,
        val command: String,
        val options: List<Option>,
        val member: Member
)

/**
 * Components
 */

data class ButtonEvent(
        val interactionId: String,
        val interactionToken: String,
        val interactionType: Int,
        val componentId: String,
        val messageId: String,
        val guild: String,
        val channel: String,
        val author: String,
        val member: Member
)

data class SelectionMenuEvent(
        val interactionId: String,
        val interactionToken: String,
        val interactionType: Int,
        val selected: List<String>,
        val componentId: String,
        val messageId: String,
        val guild: String,
        val channel: String,
        val author: String,
        val member: Member
)