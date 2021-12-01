/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.entities.ModRequestType.*
import com.fredboat.sentinel.util.*
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Service
import java.util.*

@Service
class ManagementRequests(
        private val shardManager: ShardManager,
        private val eval: EvalService
) {

    fun consume(modRequest: ModRequest): String = modRequest.run {
        val guild = shardManager.getGuildById(guildId)
                ?: throw RuntimeException("Guild $guildId not found")

        val action = when (type) {
            KICK -> guild.kick(userId.toString(), reason)
            BAN -> guild.ban(userId.toString(), banDeleteDays, reason)
            UNBAN -> guild.unban(userId.toString())
        }
        action.complete(type.name.toLowerCase())
        return ""
    }

    fun consume(request: SetAvatarRequest) {
        val decoded = Base64.getDecoder().decode(request.base64)
        shardManager.shards[0].selfUser.manager.setAvatar(Icon.from(decoded)).queue("setAvatar")
    }

    fun consume(request: ReviveShardRequest): String {
        shardManager.restart(request.shardId)
        return "" // Generates a reply
    }

    fun consume(request: LeaveGuildRequest) {
        val guild = shardManager.getGuildById(request.guildId)
                ?: throw RuntimeException("Guild ${request.guildId} not found")
        guild.leave().queue("leaveGuild")
    }

    fun consume(request: GetPingRequest): GetPingResponse {
        val shard = shardManager.getShardById(request.shardId)
        return GetPingResponse(shard?.gatewayPing ?: -1, shardManager.averageGatewayPing)
    }

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

    fun consume(request: UserListRequest) = shardManager.userCache.map { it.idLong }

    fun consume(request: BanListRequest): Array<Ban> {
        val guild = shardManager.getGuildById(request.guildId)
                ?: throw RuntimeException("Guild ${request.guildId} not found")
        return guild.retrieveBanList().complete("getBanList").map {
            Ban(it.user.toEntity(), it.reason)
        }.toTypedArray()
    }

    fun consume(request: RegisterSlashCommandRequest) {
        if (request.guildId != null) {
            val guild = shardManager.getGuildById(request.guildId!!)!!
            if (request.options != null) {
                val cmd = CommandData(request.commandName, request.commandDescription)
                if (request.group != null) {
                    cmd.addSubcommandGroups(request.group!!.toJda())
                } else if (request.subcommands != null) {
                    cmd.addSubcommands(request.subcommands!!.toJda())
                }
                guild.upsertCommand(cmd).queue("registerSlashCommand")
            }
        } else {
            shardManager.shards.forEach {
                if (request.options != null) {
                    it.upsertCommand(
                            CommandData(
                                    request.commandName,
                                    request.commandDescription
                            ).addOptions(request.options!!.toJda()).addSubcommandGroups()
                    ).queue("registerSlashCommand")
                } else {
                    it.upsertCommand(
                            CommandData(
                                    request.commandName,
                                    request.commandDescription
                            )
                    ).queue("registerSlashCommand")
                }
            }
        }
    }

    @Volatile
    var blockingEvalThread: Thread? = null

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