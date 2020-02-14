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
import net.dv8tion.jda.bot.sharding.ShardManager
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.exceptions.ErrorResponseException
import net.dv8tion.jda.core.requests.ErrorResponse
import org.springframework.stereotype.Service

@Service
class InfoRequests(private val shardManager: ShardManager) {

    fun consume(request: MemberInfoRequest): MemberInfo {
        val member = shardManager.getGuildById(request.guildId).getMemberById(request.id)
        return member.run {
            MemberInfo(
                    user.idLong,
                    guild.idLong,
                    user.avatarUrl,
                    color?.rgb,
                    joinDate.toInstant().toEpochMilli()
            )
        }
    }

    fun consume(request: GuildInfoRequest): GuildInfo {
        val guild = shardManager.getGuildById(request.id)
        return guild.run {
            GuildInfo(
                    idLong,
                    guild.iconUrl,
                    guild.memberCache.count { it.onlineStatus != OnlineStatus.OFFLINE },
                    verificationLevel.name
            )
        }
    }

    fun consume(request: RoleInfoRequest): RoleInfo {
        val role = shardManager.getRoleById(request.id)
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

    fun consume(request: GetUserRequest): User? {
        val user = shardManager.getUserById(request.id)
        if(user != null) return user.toEntity()

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