/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.corundumstudio.socketio.SocketIOClient
import com.fredboat.sentinel.entities.*
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.NewsChannel
import net.dv8tion.jda.api.entities.StageChannel
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.VoiceChannel
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.utils.PermissionUtil
import org.springframework.stereotype.Service

@Service
class PermissionRequests(private val shardManager: ShardManager) {

    /**
     * Returns true if the Role and/or Member has the given permissions in a Guild
     */
    fun consume(request: GuildPermissionRequest, client: SocketIOClient) {
        val guild = shardManager.getGuildById(request.guild)
                ?: throw RuntimeException("Got request for guild which isn't found")

        request.member?.apply {
            val member = guild.getMemberById(this)
            if (member == null) {
                client.sendEvent("permissionCheckResponse-${request.responseId}", PermissionCheckResponse("0", "0", true))
                return
            } else {
                val effective = PermissionUtil.getEffectivePermission(member)
                client.sendEvent("permissionCheckResponse-${request.responseId}", PermissionCheckResponse(effective.toString(), getMissing(request.rawPermissions.toLong(), effective), false))
                return
            }
        }

        // Role must be specified then
        request.role?.apply {
            val role = guild.getRoleById(this)
            if (role == null) {
                client.sendEvent("permissionCheckResponse-${request.responseId}", PermissionCheckResponse("0", "0", true))
                return
            } else {
                client.sendEvent("permissionCheckResponse-${request.responseId}", PermissionCheckResponse(role.permissionsRaw.toString(), getMissing(request.rawPermissions.toLong(), role.permissionsRaw), false))
                return
            }
        }
    }

    /**
     * Returns true if the Role and/or Member has the given permissions in a Channel
     */
    fun consume(request: ChannelPermissionRequest, client: SocketIOClient) {
        var channel: GuildChannel? = shardManager.getChannelById(TextChannel::class.java, request.channel)
                ?: shardManager.getChannelById(NewsChannel::class.java, request.channel)
                ?: shardManager.getChannelById(VoiceChannel::class.java, request.channel)
                ?: shardManager.getChannelById(StageChannel::class.java, request.channel)
        channel = channel ?: shardManager.getCategoryById(request.channel)
        channel ?: throw RuntimeException("Got request for channel which isn't found")

        val guild = channel.guild

        request.member?.apply {
            val member = guild.getMemberById(this)
            if (member == null) {
                client.sendEvent("permissionCheckResponse-${request.responseId}", PermissionCheckResponse("0", "0", true))
            } else {
                val effective = PermissionUtil.getEffectivePermission(channel.permissionContainer, member)
                client.sendEvent("permissionCheckResponse-${request.responseId}", PermissionCheckResponse(effective.toString(), getMissing(request.rawPermissions.toLong(), effective), false))
            }
        }

        // Role must be specified then
        request.role?.apply {
            val role = guild.getRoleById(this)
            if (role == null) {
                client.sendEvent("permissionCheckResponse-${request.responseId}", PermissionCheckResponse("0", "0", true))
            } else {
                val effective = PermissionUtil.getEffectivePermission(channel.permissionContainer, role)
                client.sendEvent("permissionCheckResponse-${request.responseId}", PermissionCheckResponse(effective.toString(), getMissing(request.rawPermissions.toLong(), effective), false))
            }
        }
    }

    fun consume(request: BulkGuildPermissionRequest, client: SocketIOClient) {
        val guild = shardManager.getGuildById(request.guild)
                ?: throw RuntimeException("Got request for guild which isn't found")

        val bulkGuildPermissionResponse = BulkGuildPermissionResponse(request.members.map {
            val member = guild.getMemberById(it) ?: return@map null
            PermissionUtil.getEffectivePermission(member).toString()
        })
        client.sendEvent("bulkGuildPermissionResponse-${request.responseId}", bulkGuildPermissionResponse)
    }

    /** Performs converse nonimplication */
    private fun getMissing(expected: Long, actual: Long): String {
        return (expected.inv() or actual).inv().toString()
    }
}