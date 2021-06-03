/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.rpc.meta.SentinelRequest
import com.fredboat.sentinel.util.mono
import com.fredboat.sentinel.util.toEntity
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.utils.PermissionUtil
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
@SentinelRequest
class InfoRequests(private val shardManager: ShardManager) {

    @SentinelRequest
    fun consume(request: GuildsRequest): GuildsResponse {
        val guilds = shardManager.guilds
        return guilds.run {
            GuildsResponse(
                this.map { it.idLong }.toList()
            )
        }
    }

    @SentinelRequest
    fun consume(request: GuildInfoRequest): GuildInfo {
        val guild = shardManager.getGuildById(request.id)
            ?: throw IllegalStateException("Guild ${request.id} not found")
        return guild.run {
            GuildInfo(
                idLong,
                guild.iconUrl,
                guild.memberCache.count { it.onlineStatus != OnlineStatus.OFFLINE },
                verificationLevel.name
            )
        }
    }

    @SentinelRequest
    fun consume(request: RoleInfoRequest): RoleInfo {
        val role = shardManager.getRoleById(request.id)
            ?: throw IllegalStateException("Role ${request.id} not found")
        return role.run {
            RoleInfo(
                idLong,
                position,
                color?.rgb,
                isHoisted,
                isMentionable,
                isManaged
            )
        }
    }

    @SentinelRequest
    fun consume(request: GetMembersByPrefixRequest): MembersByPrefixResponse {
        val members = shardManager.getGuildById(request.guildId)!!.retrieveMembersByPrefix(request.prefix, request.limit)
        return MembersByPrefixResponse(members.get().map { it.toEntity() })
    }

    @SentinelRequest
    fun consume(request: GetMembersByIdsRequest): MembersByIdsResponse {
        val members = shardManager.getGuildById(request.guildId)!!.retrieveMembersByIds(request.ids)
        return MembersByIdsResponse(members.get().map { it.toEntity() })
    }

    @SentinelRequest
    fun consume(request: MemberInfoRequest): Mono<MemberInfo>? {
        return shardManager.getGuildById(request.guildId)!!.retrieveMemberById(request.id)
            .mono("fetchMemberInfo")
            .onErrorContinue { t, _ ->
                t is ErrorResponseException && t.errorResponse == ErrorResponse.UNKNOWN_USER
                        && t.errorResponse == ErrorResponse.UNKNOWN_MEMBER
            }.map {
                MemberInfo(
                    it.user.idLong,
                    it.user.name,
                    it.nickname,
                    it.user.discriminator,
                    it.guild.idLong,
                    it.user.effectiveAvatarUrl,
                    it.color?.rgb,
                    it.timeJoined.toInstant().toEpochMilli(),
                    it.user.isBot,
                    it.user.mutualGuilds.map { it.idLong },
                    it.roles.map { it.idLong },
                    PermissionUtil.getEffectivePermission(it),
                    it.voiceState?.channel?.idLong
                )
            }
    }

    @SentinelRequest
    fun consume(request: GetMemberRequest): Mono<Member> {
        return shardManager.getGuildById(request.guildId)!!.retrieveMemberById(request.id)
            .mono("fetchMember")
            .onErrorContinue { t, _ ->
                t is ErrorResponseException && t.errorResponse == ErrorResponse.UNKNOWN_USER
                        && t.errorResponse == ErrorResponse.UNKNOWN_MEMBER
            }.map { it.toEntity() }
    }

    @SentinelRequest
    fun consume(request: UserInfoRequest): Mono<UserInfo> {
        return shardManager.retrieveUserById(request.id)
            .mono("fetchUserInfo")
            .onErrorContinue { t, _ ->
                t is ErrorResponseException && t.errorResponse == ErrorResponse.UNKNOWN_USER
            }.map {
                UserInfo(
                    it.idLong,
                    it.name,
                    it.discriminator,
                    it.effectiveAvatarUrl,
                    it.isBot,
                    it.mutualGuilds.map { it.idLong }
                )
            }
    }

    @SentinelRequest
    fun consume(request: GetUserRequest): Mono<User> {
        return shardManager.retrieveUserById(request.id)
            .mono("fetchUser")
            .onErrorContinue { t, _ ->
                t is ErrorResponseException && t.errorResponse == ErrorResponse.UNKNOWN_USER
            }.map { it.toEntity() }
    }
}