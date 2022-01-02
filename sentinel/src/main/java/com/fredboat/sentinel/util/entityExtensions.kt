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

fun JDA.toEntity() = Shard(
        shardInfo.shardId,
        shardInfo.shardTotal,
        status.toEntity()
)

fun JDA.toEntityExtended() = ExtendedShardInfo(
        toEntity(),
        guildCache.size().toInt(),
        userCache.size().toInt()
)

fun net.dv8tion.jda.api.entities.Guild.toEntity(updateCache: VoiceServerUpdateCache) = Guild(
        id,
        name,
        owner?.user?.id,
        members.map { it.toEntity() },
        textChannels.map { it.toEntity() },
        voiceChannels.map { it.toEntity() },
        roles.map { it.toEntity() },
        updateCache[id]
)

fun net.dv8tion.jda.api.entities.User.toEntity() = User(
        id,
        name,
        discriminator,
        isBot)

fun net.dv8tion.jda.api.entities.Member.toEntity() = Member(
        user.id,
        user.name,
        nickname,
        user.discriminator,
        guild.id,
        user.isBot,
        roles.map { it.id },
        PermissionUtil.getEffectivePermission(this).toString(),
        voiceState?.channel?.id
)

fun net.dv8tion.jda.api.entities.VoiceChannel.toEntity() = VoiceChannel(
        id,
        name,
        members.map { it.user.id },
        userLimit,
        PermissionUtil.getExplicitPermission(this, guild.selfMember).toString()
)

fun net.dv8tion.jda.api.entities.TextChannel.toEntity() = TextChannel(
        id,
        name,
        PermissionUtil.getExplicitPermission(this, guild.selfMember).toString()
)

fun net.dv8tion.jda.api.entities.Role.toEntity() = Role(
        id,
        name,
        permissionsRaw.toString()
)

fun JDA.Status.toEntity() = ShardStatus.valueOf(this.toString())

fun net.dv8tion.jda.api.interactions.commands.OptionMapping.toEntity() = Option(
        name,
        asString
)