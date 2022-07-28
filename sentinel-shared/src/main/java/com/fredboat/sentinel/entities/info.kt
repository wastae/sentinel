/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.entities

// Additional info about entities, which may be useful in only a few places

data class GuildInfo(
    val id: String,
    val iconUrl: String?,
    val onlineMembers: Int,
    val verificationLevel: String
)

data class GuildInfoRequest(val id: String, val responseId: String)

data class RoleInfo(
    val id: String,
    val position: Int,
    val colorRgb: Int?,
    val isHoisted: Boolean,
    val isMentionable: Boolean,
    val isManaged: Boolean
)

data class RoleInfoRequest(val id: String, val responseId: String)

data class MemberInfo(
    val id: String,
    val name: String,
    val nickname: String?,
    val discrim: String,
    val guildId: String,
    val avatarUrl: String,
    val colorRgb: Int?,
    val joinDateMillis: String,
    val bot: Boolean,
    val mutualGuilds: List<String>,
    val roles: List<String>,
    val permissions: String,
    val voiceChannel: String?
)

data class MemberInfoRequest(val id: String, val guildId: String, val responseId: String)

data class GetMemberRequest(val id: String, val guildId: String, val responseId: String)

data class MembersByRoleResponse(val members: List<Member>)

data class FindMembersByRoleRequest(val id: String, val guildId: String, val responseId: String)

data class MembersByPrefixResponse(val members: List<Member>)

data class GetMembersByPrefixRequest(val prefix: String, val limit: Int, val guildId: String, val responseId: String)

data class MembersByIdsResponse(val members: List<Member>)

data class GetMembersByIdsRequest(val ids: List<String>, val guildId: String, val responseId: String)

data class UserInfo(
    val id: String,
    val name: String,
    val discrim: String,
    val avatarUrl: String,
    val bot: Boolean,
    val mutualGuilds: List<String>
)

data class UserInfoRequest(val id: String, val responseId: String)

data class GetUserRequest(val id: String, val responseId: String)