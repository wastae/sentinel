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

data class RoleInfo(
        val id: Long,
        val position: Int,
        val colorRgb: Int?,
        val isHoisted: Boolean,
        val isMentionable: Boolean,
        val isManaged: Boolean
)

data class RoleInfoRequest(val id: Long)

data class MemberInfo(
        val id: Long,
        val name: String,
        val nickname: String?,
        val discrim: String,
        val guildId: Long,
        val avatarUrl: String,
        val colorRgb: Int?,
        val joinDateMillis: Long,
        val bot: Boolean,
        val mutualGuilds: List<Long>,
        val roles: List<Long>,
        val permissions: Long,
        val voiceChannel: Long?
)

data class MemberInfoRequest(val id: Long, val guildId: Long)

data class GetMemberRequest(val id: Long, val guildId: Long)

data class MembersByRoleResponse(val members: List<Member>)

data class FindMembersByRoleRequest(val id: Long, val guildId: Long)

data class MembersByPrefixResponse(val members: List<Member>)

data class GetMembersByPrefixRequest(val prefix: String, val limit: Int, val guildId: Long)

data class MembersByIdsResponse(val members: List<Member>)

data class GetMembersByIdsRequest(val ids: List<Long>, val guildId: Long)

data class UserInfo(
        val id: Long,
        val name: String,
        val discrim: String,
        val avatarUrl: String,
        val bot: Boolean,
        val mutualGuilds: List<Long>
)

data class UserInfoRequest(val id: Long)

data class GetUserRequest(val id: Long)