/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.util.complete
import com.fredboat.sentinel.util.toEntity
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InfoRequests(private val shardManager: ShardManager) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InfoRequests::class.java)
    }

    fun consume(request: MemberInfoRequest): MemberInfo {
        val member = shardManager.getGuildById(request.guildId)!!.retrieveMemberById(request.id).complete()
        return member.run {
            MemberInfo (
                user.idLong,
                guild.idLong,
                user.avatarUrl,
                color?.rgb,
                timeJoined.toInstant().toEpochMilli()
            )
        }
    }

    fun consume(request: GuildInfoRequest): GuildInfo {
        val guild = shardManager.getGuildById(request.id)
        if (guild == null) { log.error("Received null on guild request for ${request.id}") }
        return guild!!.run {
            GuildInfo (
                idLong,
                guild.iconUrl,
                guild.memberCache.count { it.onlineStatus != OnlineStatus.OFFLINE },
                verificationLevel.name
            )
        }
    }

    fun consume(request: RoleInfoRequest): RoleInfo {
        val role = shardManager.getRoleById(request.id)
        if (role == null) { log.error("Received null on role request for ${request.id}") }
        return role!!.run {
            RoleInfo (
                idLong,
                position,
                color?.rgb,
                isHoisted,
                isMentionable,
                isManaged
            )
        }
    }

    fun consume(request: GetUserRequest): User? {
        val user = shardManager.getUserById(request.id)
        if (user != null) return user.toEntity()

        for (shard in shardManager.shards) {
            if (shard.status != JDA.Status.CONNECTED) continue
            return try {
                shard.retrieveUserById(request.id).complete("fetchUser").toEntity()
            } catch (e: ErrorResponseException) {
                if (e.errorResponse == ErrorResponse.UNKNOWN_USER) return null
                throw e
            }
        }

        throw RuntimeException("No shards connected")
    }

}