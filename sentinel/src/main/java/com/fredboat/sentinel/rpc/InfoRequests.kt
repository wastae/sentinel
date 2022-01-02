/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.corundumstudio.socketio.SocketIOClient
import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.util.toEntity
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.utils.PermissionUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InfoRequests(private val shardManager: ShardManager) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InfoRequests::class.java)
    }

    fun consume(request: GuildsRequest, client: SocketIOClient) {
        val guilds = shardManager.guilds
        guilds.run {
            client.sendEvent("guildsResponse-${request.responseId}", GuildsResponse(
                this.map { it.id }.toList()
            ))
        }
    }

    fun consume(request: GuildInfoRequest, client: SocketIOClient) {
        val guild = shardManager.getGuildById(request.id)
                ?: throw IllegalStateException("Guild ${request.id} not found")
        guild.run {
            client.sendEvent("guildInfo-${request.responseId}", GuildInfo(
                id,
                guild.iconUrl,
                guild.memberCache.count { it.onlineStatus != OnlineStatus.OFFLINE },
                verificationLevel.name
            ))
        }
    }

    fun consume(request: RoleInfoRequest, client: SocketIOClient) {
        val role = shardManager.getRoleById(request.id)
                ?: throw IllegalStateException("Role ${request.id} not found")
        role.run {
            client.sendEvent("roleInfo-${request.responseId}", RoleInfo(
                id,
                position,
                color?.rgb,
                isHoisted,
                isMentionable,
                isManaged
            ))
        }
    }

    fun consume(request: FindMembersByRoleRequest, client: SocketIOClient) {
        val role = shardManager.getRoleById(request.id)
                ?: throw IllegalStateException("Role ${request.id} not found")
        shardManager.getGuildById(request.guildId)!!.findMembersWithRoles(role).onSuccess { it ->
            client.sendEvent("membersByRoleResponse-${request.responseId}", MembersByRoleResponse(
                it.map { it.toEntity() }
            ))
        }
    }

    fun consume(request: GetMembersByPrefixRequest, client: SocketIOClient) {
        shardManager.getGuildById(request.guildId)!!.retrieveMembersByPrefix(request.prefix, request.limit).onSuccess { it ->
            client.sendEvent("membersByPrefixResponse-${request.responseId}", it.map { it.toEntity() })
        }
    }

    fun consume(request: GetMembersByIdsRequest, client: SocketIOClient) {
        shardManager.getGuildById(request.guildId)!!.retrieveMembersByIds(request.ids.map { it.toLong() }).onSuccess { it ->
            client.sendEvent("membersByIdsResponse-${request.responseId}", it.map { it.toEntity() })
        }
    }

    fun consume(request: MemberInfoRequest, client: SocketIOClient) {
        val guild = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received MemberInfoRequest in guild ${request.guildId} which was not found")
            return
        }

        return guild.retrieveMemberById(request.id).queue { it ->
            client.sendEvent("memberInfo-${request.responseId}", MemberInfo(
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
            ))
        }
    }

    fun consume(request: GetMemberRequest, client: SocketIOClient) {
        val guild = shardManager.getGuildById(request.guildId)

        if (guild == null) {
            log.error("Received GetMemberRequest in guild ${request.guildId} which was not found")
            return
        }

        guild.retrieveMemberById(request.id).queue {
            client.sendEvent("member-${request.responseId}", it.toEntity())
        }
    }

    fun consume(request: UserInfoRequest, client: SocketIOClient) {
        shardManager.retrieveUserById(request.id).queue { it ->
            client.sendEvent("userInfo-${request.responseId}", UserInfo(
                it.id,
                it.name,
                it.discriminator,
                it.effectiveAvatarUrl,
                it.isBot,
                it.mutualGuilds.map { it.id }
            ))
        }
    }

    fun consume(request: GetUserRequest, client: SocketIOClient) {
        shardManager.retrieveUserById(request.id).queue {
            client.sendEvent("user-${request.responseId}", it.toEntity())
        }
    }
}