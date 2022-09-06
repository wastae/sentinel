/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.io.SocketContext
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.VoiceChannel
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.utils.PermissionUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PermissionRequests {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(PermissionRequests::class.java)
    }

    lateinit var shardManager: ShardManager

    /**
     * Returns true if the Role and/or Member has the given permissions in a Guild
     */
    fun consume(request: GuildPermissionRequest, context: SocketContext) {
        val guild = shardManager.getGuildById(request.guild)

        if (guild == null) {
            val msg = "Guild ${request.guild} not found"
            log.error(msg)
            context.sendResponse(PermissionCheckResponse::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        request.member?.apply {
            val member = guild.getMemberById(this)
            if (member == null) {
                context.sendResponse(PermissionCheckResponse::class.java.simpleName, context.gson.toJson(PermissionCheckResponse(
                    "0",
                    "0",
                    true
                )), request.responseId)
                return
            } else {
                val effective = PermissionUtil.getEffectivePermission(member)
                context.sendResponse(PermissionCheckResponse::class.java.simpleName, context.gson.toJson(PermissionCheckResponse(
                    effective.toString(),
                    getMissing(request.rawPermissions.toLong(), effective),
                    false
                )), request.responseId)
                return
            }
        }

        // Role must be specified then
        request.role?.apply {
            val role = guild.getRoleById(this)
            if (role == null) {
                context.sendResponse(PermissionCheckResponse::class.java.simpleName, context.gson.toJson(PermissionCheckResponse(
                    "0",
                    "0",
                    true
                )), request.responseId)
                return
            } else {
                context.sendResponse(PermissionCheckResponse::class.java.simpleName, context.gson.toJson(PermissionCheckResponse(
                    role.permissionsRaw.toString(),
                    getMissing(request.rawPermissions.toLong(), role.permissionsRaw),
                    false
                )), request.responseId)
                return
            }
        }
    }

    /**
     * Returns true if the Role and/or Member has the given permissions in a Channel
     */
    fun consume(request: ChannelPermissionRequest, context: SocketContext) {
        var channel: GuildChannel? = shardManager.getChannelById(TextChannel::class.java, request.channel)
                ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)
                ?: shardManager.getChannelById(VoiceChannel::class.java, request.channel)
                ?: shardManager.getChannelById(StageChannel::class.java, request.channel)
        channel = channel ?: shardManager.getCategoryById(request.channel)

        if (channel == null) {
            val msg = "Channel ${request.channel} not found"
            log.error(msg)
            context.sendResponse(PermissionCheckResponse::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        val guild = channel.guild

        request.member?.apply {
            val member = guild.getMemberById(this)
            if (member == null) {
                context.sendResponse(PermissionCheckResponse::class.java.simpleName, context.gson.toJson(PermissionCheckResponse(
                    "0",
                    "0",
                    true
                )), request.responseId)
            } else {
                val effective = PermissionUtil.getEffectivePermission(channel.permissionContainer, member)
                context.sendResponse(PermissionCheckResponse::class.java.simpleName, context.gson.toJson(PermissionCheckResponse(
                    effective.toString(),
                    getMissing(request.rawPermissions.toLong(), effective),
                    false
                )), request.responseId)
            }
        }

        // Role must be specified then
        request.role?.apply {
            val role = guild.getRoleById(this)
            if (role == null) {
                context.sendResponse(PermissionCheckResponse::class.java.simpleName, context.gson.toJson(PermissionCheckResponse(
                    "0",
                    "0",
                    true
                )), request.responseId)
            } else {
                val effective = PermissionUtil.getEffectivePermission(channel.permissionContainer, role)
                context.sendResponse(PermissionCheckResponse::class.java.simpleName, context.gson.toJson(PermissionCheckResponse(
                    effective.toString(),
                    getMissing(request.rawPermissions.toLong(), effective),
                    false
                )), request.responseId)
            }
        }
    }

    fun consume(request: BulkGuildPermissionRequest, context: SocketContext) {
        val guild = shardManager.getGuildById(request.guild)

        if (guild == null) {
            val msg = "Guild ${request.guild} not found"
            log.error(msg)
            context.sendResponse(BulkGuildPermissionResponse::class.java.simpleName, msg, request.responseId, false, false)
            return
        }

        val bulkGuildPermissionResponse = BulkGuildPermissionResponse(request.members.map {
            val member = guild.getMemberById(it) ?: return@map null
            PermissionUtil.getEffectivePermission(member).toString()
        })
        context.sendResponse(BulkGuildPermissionResponse::class.java.simpleName, context.gson.toJson(bulkGuildPermissionResponse), request.responseId)
    }

    /** Performs converse nonimplication */
    private fun getMissing(expected: Long, actual: Long): String {
        return (expected.inv() or actual).inv().toString()
    }
}