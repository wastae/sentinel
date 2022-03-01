/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.corundumstudio.socketio.SocketIOClient
import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.entities.ModRequestType.*
import com.fredboat.sentinel.util.*
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.sharding.ShardManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class ManagementRequests(
    private val shardManager: ShardManager,
    private val eval: EvalService
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(ManagementRequests::class.java)
    }

    fun consume(request: ModRequest, client: SocketIOClient) {
        val guild = shardManager.getGuildById(request.guildId)
                ?: throw RuntimeException("Guild ${request.guildId} not found")

        val action = when (request.type) {
            KICK -> guild.kick(request.userId, request.reason)
            BAN -> guild.ban(request.userId, request.banDeleteDays, request.reason)
            UNBAN -> guild.unban(request.userId)
        }
        action.queue()

        client.sendEvent("modResponse-${request.responseId}", "")
    }

    fun consume(request: SetAvatarRequest) {
        val decoded = Base64.getDecoder().decode(request.base64)
        shardManager.shards[0].selfUser.manager.setAvatar(Icon.from(decoded)).queue()
    }

    fun consume(request: ReviveShardRequest, client: SocketIOClient) {
        shardManager.restart(request.shardId)
        client.sendEvent("reviveShardResponse-${request.responseId}", "") // Generates a reply
    }

    fun consume(request: LeaveGuildRequest) {
        val guild = shardManager.getGuildById(request.guildId)
                ?: throw RuntimeException("Guild ${request.guildId} not found")
        guild.leave().queue()
    }

    fun consume(request: GetPingRequest, client: SocketIOClient) {
        val shard = shardManager.getShardById(request.shardId)
        client.sendEvent("getPingResponse-${request.responseId}", GetPingResponse(shard?.gatewayPing.toString(), shardManager.averageGatewayPing))
    }

    fun consume(request: SentinelInfoRequest, client: SocketIOClient) {
        client.sendEvent("sentinelInfoResponse-${request.responseId}", SentinelInfoResponse(
            shardManager.guildCache.size().toString(),
            shardManager.userCache.size().toString(),
            shardManager.roleCache.size().toString(),
            shardManager.categoryCache.size().toString(),
            shardManager.textChannelCache.size().toString(),
            shardManager.voiceChannelCache.size().toString(),
            if (request.includeShards) shardManager.shards.map { it.toEntityExtended() } else null
        ))
    }

    fun consume(request: UserListRequest, client: SocketIOClient) {
        client.sendEvent("userListResponse-${request.responseId}", shardManager.userCache.map { it.idLong })
    }

    fun consume(request: BanListRequest, client: SocketIOClient) {
        val guild = shardManager.getGuildById(request.guildId)
                ?: throw RuntimeException("Guild ${request.guildId} not found")
        guild.retrieveBanList().queue { it ->
            client.sendEvent("banListResponse-${request.responseId}", it.map { Ban(it.user.toEntity(), it.reason) }.toTypedArray())
        }
    }

    fun consume(request: RegisterSlashCommandRequest) {
        if (request.guildId != null) {
            val guild = shardManager.getGuildById(request.guildId!!)!!
            val cmd = Commands.slash(request.commandName, request.commandDescription)
            when {
                request.group != null -> {
                    if (request.group!!.name != null && request.group!!.description != null) cmd.addSubcommandGroups(request.group!!.toJdaExt())
                    else cmd.addSubcommands(request.group!!.toJda())
                }
                request.options != null -> {
                    cmd.addOptions(request.options!!.toJda())
                }
            }
            log.info("Registering slash command ${cmd.toData().toPrettyString()}")
            guild.upsertCommand(cmd).queue()
        } else {
            shardManager.shards.forEach {
                if (request.options != null) {
                    it.upsertCommand(
                        Commands.slash(
                            request.commandName,
                            request.commandDescription
                        ).addOptions(request.options!!.toJda()).addSubcommandGroups()
                    ).queue()
                } else {
                    it.upsertCommand(
                        Commands.slash(
                            request.commandName,
                            request.commandDescription
                        )
                    ).queue()
                }
            }
        }
    }

    @Volatile
    var blockingEvalThread: Thread? = null

    fun consume(request: EvalRequest, client: SocketIOClient) {
        if (request.kill) {
            blockingEvalThread ?: client.sendEvent("evalResponse-${request.responseId}", "No task is running")
            blockingEvalThread?.interrupt()
            client.sendEvent("evalResponse-${request.responseId}", "Task killed")
        } else {
            val mono = eval.evalScript(request.script!!, request.timeout)
            blockingEvalThread = Thread.currentThread()
            client.sendEvent("evalResponse-${request.responseId}", try {
                mono.block()!!
            } catch (ex: Exception) {
                "${ex.javaClass.simpleName}: ${ex.message ?: "null"}"
            } finally {
                blockingEvalThread = null
            })
        }
    }
}