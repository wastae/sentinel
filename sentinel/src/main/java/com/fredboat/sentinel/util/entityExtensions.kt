/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.util

import com.fredboat.sentinel.entities.*
import com.fredboat.sentinel.jda.VoiceServerUpdateCache
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.internal.utils.PermissionUtil

fun net.dv8tion.jda.api.JDA.toEntity() = Shard(
        shardInfo.shardId,
        shardInfo.shardTotal,
        status.toEntity()
)

fun net.dv8tion.jda.api.JDA.toEntityExtended() = ExtendedShardInfo(
        toEntity(),
        guildCache.size().toInt(),
        userCache.size().toInt()
)

fun net.dv8tion.jda.api.entities.Guild.toEntity(updateCache: VoiceServerUpdateCache) = Guild(
        idLong,
        name,
        owner?.user?.idLong,
        members.map { it.toEntity() },
        textChannels.map { it.toEntity() },
        voiceChannels.map { it.toEntity() },
        roles.map { it.toEntity() },
        updateCache[idLong])

fun net.dv8tion.jda.api.entities.User.toEntity() = User(
        idLong,
        name,
        discriminator,
        isBot)

fun net.dv8tion.jda.api.entities.Member.toEntity(): Member {
    return Member(
            user.idLong,
            user.name,
            nickname,
            user.discriminator,
            guild.idLong,
            user.isBot,
            roles.map { it.idLong },
            PermissionUtil.getEffectivePermission(this),
            voiceState?.channel?.idLong)
}

fun net.dv8tion.jda.api.entities.VoiceChannel.toEntity() = VoiceChannel(
        idLong,
        name,
        members.map { it.user.idLong },
        userLimit,
        PermissionUtil.getExplicitPermission(this, guild.selfMember))

fun net.dv8tion.jda.api.entities.TextChannel.toEntity() = TextChannel(
        idLong,
        name,
        PermissionUtil.getExplicitPermission(this, guild.selfMember))

fun net.dv8tion.jda.api.entities.Role.toEntity() = Role(
        idLong,
        name,
        permissionsRaw
)

fun JDA.Status.toEntity() = ShardStatus.valueOf(this.toString())