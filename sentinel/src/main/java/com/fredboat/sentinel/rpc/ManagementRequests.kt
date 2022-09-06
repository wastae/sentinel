/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.entities.ModRequestType.*
import com.fredboat.sentinel.io.SocketContext
import com.fredboat.sentinel.util.toEntityExtended
import com.fredboat.sentinel.util.toJda
import com.fredboat.sentinel.util.toJdaExt
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Service
import java.util.*

@Service
class ManagementRequests {

    lateinit var shardManager: ShardManager

    fun consume(request: ModRequest) {
        val guild = shardManager.getGuildById(request.guildId)
                ?: throw RuntimeException("Guild ${request.guildId} not found")

        val action = when (request.type) {
            KICK -> guild.kick(UserSnowflake.fromId(request.userId), request.reason)
            BAN -> guild.ban(UserSnowflake.fromId(request.userId), request.banDeleteDays, request.reason)
            UNBAN -> guild.unban(UserSnowflake.fromId(request.userId))
        }
        action.queue()
    }

    fun consume(request: SetAvatarRequest) {
        val decoded = Base64.getDecoder().decode(request.base64)
        shardManager.shards[0].selfUser.manager.setAvatar(Icon.from(decoded)).queue()
    }

    fun consume(request: ReviveShardRequest) {
        shardManager.restart(request.shardId)
    }

    fun consume(request: LeaveGuildRequest) {
        val guild = shardManager.getGuildById(request.guildId)
                ?: throw RuntimeException("Guild ${request.guildId} not found")
        guild.leave().queue()
    }

    fun consume(request: GetPingRequest, context: SocketContext) {
        val shard = shardManager.getShardById(request.shardId)
        context.sendResponse(GetPingResponse::class.java.simpleName, context.gson.toJson(GetPingResponse(
            shard?.gatewayPing.toString(),
            shardManager.averageGatewayPing
        )), request.responseId)
    }

    fun consume(request: SentinelInfoRequest, context: SocketContext) {
        context.sendResponse(SentinelInfoResponse::class.java.simpleName, context.gson.toJson(SentinelInfoResponse(
            shardManager.guildCache.size().toString(),
            shardManager.userCache.size().toString(),
            shardManager.roleCache.size().toString(),
            shardManager.categoryCache.size().toString(),
            shardManager.textChannelCache.size().toString(),
            shardManager.voiceChannelCache.size().toString(),
            if (request.includeShards) shardManager.shards.map { it.toEntityExtended() } else null
        )), request.responseId)
    }

    //fun consume(request: UserListRequest, context: SocketContext) {
    //    context.sendResponse("userListResponse-${request.responseId}", context.gson.toJson(shardManager.userCache.map { it.idLong }))
    //}

    //fun consume(request: BanListRequest, context: SocketContext) {
    //    val guild = shardManager.getGuildById(request.guildId)
    //            ?: throw RuntimeException("Guild ${request.guildId} not found")
    //    guild.retrieveBanList().queue { it ->
    //        context.sendResponse("banListResponse-${request.responseId}", context.gson.toJson(it.map { Ban(it.user.toEntity(), it.reason) }.toTypedArray()))
    //    }
    //}

    fun consume(request: RemoveSlashCommandsRequest) {
        if (request.guildId != null) {
            val guild = shardManager.getGuildById(request.guildId!!)!!
            guild.updateCommands().queue()
        } else {
            shardManager.shards[0].updateCommands().queue()
        }
    }

    fun consume(request: RemoveSlashCommandRequest) {
        if (request.guildId != null) {
            val guild = shardManager.getGuildById(request.guildId!!)!!
            guild.deleteCommandById(request.commandId).queue()
        } else {
            shardManager.shards[0].deleteCommandById(request.commandId).queue()
        }
    }

    fun consume(request: RegisterSlashCommandRequest) {
        if (request.guildId != null) {
            val guild = shardManager.getGuildById(request.guildId!!)!!
            guild.upsertCommand(buildSlashCommand(request)).queue()
        } else {
            shardManager.shards[0].upsertCommand(buildSlashCommand(request)).queue()
        }
    }

    fun consume(request: RegisterContextCommandRequest) {
        if (request.guildId != null) {
            val guild = shardManager.getGuildById(request.guildId!!)!!
            guild.upsertCommand(buildContextCommand(request)).queue()
        } else {
            shardManager.shards[0].upsertCommand(buildContextCommand(request)).queue()
        }
    }

    private fun buildContextCommand(request: RegisterContextCommandRequest): CommandData {
        return Commands.context(Command.Type.fromId(request.type.toInt()), request.commandName)
    }

    private fun buildSlashCommand(request: RegisterSlashCommandRequest): SlashCommandData {
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

        return cmd
    }
}