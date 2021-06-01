/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.entities.ModRequestType.*
import com.fredboat.sentinel.jda.RemoteSessionController
import com.fredboat.sentinel.rpc.meta.SentinelRequest
import com.fredboat.sentinel.util.EvalService
import com.fredboat.sentinel.util.mono
import com.fredboat.sentinel.util.toEntity
import com.fredboat.sentinel.util.toEntityExtended
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.entities.Icon
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*

@Service
@SentinelRequest
class ManagementRequests(
        private val shardManager: ShardManager,
        private val eval: EvalService,
        private val sessionsController: RemoteSessionController
) {

    @SentinelRequest
    fun consume(modRequest: ModRequest): Mono<String> = modRequest.run {
        val guild = shardManager.getGuildById(guildId)
                ?: throw RuntimeException("Guild $guildId not found")

        val action = when (type) {
            KICK -> guild.kick(userId.toString(), reason)
            BAN -> guild.ban(userId.toString(), banDeleteDays, reason)
            UNBAN -> guild.unban(userId.toString())
        }
        return action.mono(type.name.toLowerCase()).thenReturn("")
    }

    @SentinelRequest
    fun consume(request: SetAvatarRequest) {
        val decoded = Base64.getDecoder().decode(request.base64)
        shardManager.shards[0].selfUser.manager.setAvatar(Icon.from(decoded))
            .mono("setAvatar")
            .subscribe()
    }

    @SentinelRequest
    fun consume(request: ReviveShardRequest): String {
        shardManager.restart(request.shardId)
        return "" // Generates a reply
    }

    @SentinelRequest
    fun consume(request: LeaveGuildRequest) {
        val guild = shardManager.getGuildById(request.guildId)
                ?: throw RuntimeException("Guild ${request.guildId} not found")
        guild.leave().mono("leaveGuild").subscribe()
    }

    @SentinelRequest
    fun consume(request: GetPingRequest): GetPingResponse {
        val shard = shardManager.getShardById(request.shardId)
        return GetPingResponse(shard?.gatewayPing ?: -1, shardManager.averageGatewayPing)
    }

    @SentinelRequest
    fun consume(request: SentinelInfoRequest) = shardManager.run {
        SentinelInfoResponse(
            guildCache.size(),
            userCache.size(),
            roleCache.size(),
            categoryCache.size(),
            textChannelCache.size(),
            voiceChannelCache.size(),
            if (request.includeShards) shards.map { it.toEntityExtended() } else null
        )
    }

    @SentinelRequest
    fun consume(request: RunSessionRequest) = sessionsController.onRunRequest(request.shardId)

    @SentinelRequest
    fun consume(request: UserListRequest) = shardManager.userCache.map { it.idLong }

    @SentinelRequest
    fun consume(request: BanListRequest): Mono<Array<Ban>> {
        val guild = shardManager.getGuildById(request.guildId)
                ?: throw RuntimeException("Guild ${request.guildId} not found")
        return guild.retrieveBanList().mono("getBanList").map { list ->
            list.map { Ban(it.user.toEntity(), it.reason) }
                .toTypedArray()
        }
    }

    @Volatile
    var blockingEvalThread: Thread? = null

    @SentinelRequest
    fun consume(request: EvalRequest): String {
        if (request.kill) {
            blockingEvalThread ?: return "No task is running"
            blockingEvalThread?.interrupt()
            return "Task killed"
        } else {
            val mono = eval.evalScript(request.script!!, request.timeout)
            blockingEvalThread = Thread.currentThread()
            return try {
                mono.block()!!
            } catch (ex: Exception) {
                "${ex.javaClass.simpleName}: ${ex.message ?: "null"}"
            } finally {
                blockingEvalThread = null
            }
        }
    }

}