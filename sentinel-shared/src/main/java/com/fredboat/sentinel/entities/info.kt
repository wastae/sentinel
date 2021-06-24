/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.entities

// Additional info about entities, which may be useful in only a few places

data class GuildInfo(
        val id: Long,
        val iconUrl: String?,
        val onlineMembers: Int,
        val verificationLevel: String
)

data class GuildInfoRequest(val id: Long)

data class MemberInfo(
        val id: Long,
        val guildId: Long,
        val iconUrl: String?,
        val colorRgb: Int?,
        val joinDateMillis: Long
)

data class MemberInfoRequest(val id: Long, val guildId: Long)

data class RoleInfo(
        val id: Long,
        val position: Int,
        val colorRgb: Int?,
        val isHoisted: Boolean,
        val isMentionable: Boolean,
        val isManaged: Boolean
)

data class RoleInfoRequest(val id: Long)

/** Returns a [User] if found */
data class GetUserRequest(val id: Long)