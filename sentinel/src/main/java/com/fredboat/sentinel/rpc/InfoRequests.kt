/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.io.SocketContext
import com.fredboat.sentinel.util.toEntity
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.utils.PermissionUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InfoRequests {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InfoRequests::class.java)
    }

    lateinit var shardManager: ShardManager

    fun consume(request: GuildsRequest, context: SocketContext) {
        val guilds = shardManager.guildCache
        guilds.run {
            context.sendResponse(GuildsResponse::class.java.simpleName, context.gson.toJson(GuildsResponse(
                this.map { it.id }.toList()
            )), request.responseId)
        }
    }

    fun consume(request: GuildInfoRequest, context: SocketContext) {
        val guild = shardManager.getGuildById(request.id)
                ?: throw IllegalStateException("Guild ${request.id} not found")
        guild.run {
            context.sendResponse(GuildInfo::class.java.simpleName, context.gson.toJson(GuildInfo(
                id,
                guild.iconUrl,
                guild.memberCache.count { it.onlineStatus != OnlineStatus.OFFLINE },
                verificationLevel.name
            )), request.responseId)
        }
    }

    fun consume(request: RoleInfoRequest, context: SocketContext) {
        val role = shardManager.getRoleById(request.id)
                ?: throw IllegalStateException("Role ${request.id} not found")
        role.run {
            context.sendResponse(RoleInfo::class.java.simpleName, context.gson.toJson(RoleInfo(
                id,
                position,
                color?.rgb,
                isHoisted,
                isMentionable,
                isManaged
            )), request.responseId)
        }
    }

    fun consume(request: FindMembersByRoleRequest, context: SocketContext) {
        val role = shardManager.getRoleById(request.id)
                ?: throw IllegalStateException("Role ${request.id} not found")
        shardManager.getGuildById(request.guildId)!!.findMembersWithRoles(role).onSuccess { it ->
            context.sendResponse(MembersByRoleResponse::class.java.simpleName, context.gson.toJson(MembersByRoleResponse(
                it.map { it.toEntity() }
            )), request.responseId)
        }
    }

    fun consume(request: GetMembersByPrefixRequest, context: SocketContext) {
        shardManager.getGuildById(request.guildId)!!.retrieveMembersByPrefix(request.prefix, request.limit).onSuccess { it ->
            context.sendResponse(MembersByPrefixResponse::class.java.simpleName, context.gson.toJson(it.map { it.toEntity() }), request.responseId)
        }
    }

    fun consume(request: GetMembersByIdsRequest, context: SocketContext) {
        shardManager.getGuildById(request.guildId)!!.retrieveMembersByIds(request.ids.map { it.toLong() }).onSuccess { it ->
            context.sendResponse(MembersByIdsResponse::class.java.simpleName, context.gson.toJson(it.map { it.toEntity() }), request.responseId)
        }
    }

    fun consume(request: MemberInfoRequest, context: SocketContext) {
        val guild = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received MemberInfoRequest in guild ${request.guildId} which was not found")
            return
        }

        guild.retrieveMemberById(request.id).queue { it ->
            context.sendResponse(MemberInfo::class.java.simpleName, context.gson.toJson(MemberInfo(
                it.user.id,
                it.user.name,
                it.nickname,
                it.user.discriminator,
                it.guild.id,
                it.user.effectiveAvatarUrl,
                it.color?.rgb,
                it.timeJoined.toInstant().toEpochMilli().toString(),
                it.user.isBot,
                it.user.mutualGuilds.map { it.id },
                it.roles.map { it.id },
                PermissionUtil.getEffectivePermission(it).toString(),
                it.voiceState?.channel?.id
            )), request.responseId)
        }
    }

    fun consume(request: GetMemberRequest, context: SocketContext) {
        val guild = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received GetMemberRequest in guild ${request.guildId} which was not found")
            return
        }

        guild.retrieveMemberById(request.id).queue {
            context.sendResponse(Member::class.java.simpleName, context.gson.toJson(it.toEntity()), request.responseId)
        }
    }

    fun consume(request: UserInfoRequest, context: SocketContext) {
        shardManager.retrieveUserById(request.id).queue { it ->
            context.sendResponse(UserInfo::class.java.simpleName, context.gson.toJson(UserInfo(
                it.id,
                it.name,
                it.discriminator,
                it.effectiveAvatarUrl,
                it.isBot,
                it.mutualGuilds.map { it.id }
            )), request.responseId)
        }
    }

    fun consume(request: GetUserRequest, context: SocketContext) {
        shardManager.retrieveUserById(request.id).queue {
            context.sendResponse(User::class.java.simpleName, context.gson.toJson(it.toEntity()), request.responseId)
        }
    }
}