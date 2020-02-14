/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.*
import net.dv8tion.jda.bot.sharding.ShardManager
import net.dv8tion.jda.core.entities.Channel
import net.dv8tion.jda.core.utils.PermissionUtil
import org.springframework.stereotype.Service

@Service
class PermissionRequests(private val shardManager: ShardManager) {

    /**
     * Returns true if the Role and/or Member has the given permissions in a Guild
     */
    fun consume(request: GuildPermissionRequest): PermissionCheckResponse {
        val guild = shardManager.getGuildById(request.guild)
                ?: throw RuntimeException("Got request for guild which isn't found")

        request.member?.apply {
            val member = guild.getMemberById(this) ?: return PermissionCheckResponse(0, 0, true)
            val effective = PermissionUtil.getEffectivePermission(member)
            return PermissionCheckResponse(effective, getMissing(request.rawPermissions, effective), false)
        }

        // Role must be specified then
        val role = guild.getRoleById(request.role!!) ?: return PermissionCheckResponse(0, 0, true)
        return PermissionCheckResponse(role.permissionsRaw, getMissing(request.rawPermissions, role.permissionsRaw), false)
    }

    /**
     * Returns true if the Role and/or Member has the given permissions in a Channel
     */
    fun consume(request: ChannelPermissionRequest): PermissionCheckResponse {
        var channel: Channel? = shardManager.getTextChannelById(request.channel)
                ?: shardManager.getVoiceChannelById(request.channel)
        channel = channel ?: shardManager.getCategoryById(request.channel)
        channel ?: throw RuntimeException("Got request for channel which isn't found")

        val guild = channel.guild

        request.member?.apply {
            val member = guild.getMemberById(this) ?: return PermissionCheckResponse(0, 0, true)
            val effective = PermissionUtil.getEffectivePermission(channel, member)
            return PermissionCheckResponse(
                    effective,
                    getMissing(request.rawPermissions, effective),
                    false
            )
        }

        // Role must be specified then
        val role = guild.getRoleById(request.role!!) ?: return PermissionCheckResponse(0, 0, true)
        val effective = PermissionUtil.getEffectivePermission(channel, role)
        return PermissionCheckResponse(effective, getMissing(request.rawPermissions, effective), false)
    }

    fun consume(request: BulkGuildPermissionRequest): BulkGuildPermissionResponse {
        val guild = shardManager.getGuildById(request.guild)
                ?: throw RuntimeException("Got request for guild which isn't found")

        return BulkGuildPermissionResponse(request.members.map {
            val member = guild.getMemberById(it) ?: return@map null
            PermissionUtil.getEffectivePermission(member)
        })
    }

    /** Performs converse nonimplication */
    private fun getMissing(expected: Long, actual: Long) = (expected.inv() or actual).inv()

}