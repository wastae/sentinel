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
import net.dv8tion.jda.api.entities.AudioChannel
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.StageChannel
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
    channels.filter { it.type == ChannelType.TEXT || it.type == ChannelType.NEWS }.map { it.toTextEntity() },
    channels.filter { it.type == ChannelType.VOICE || it.type == ChannelType.STAGE }.map { (it as AudioChannel).toVoiceEntity() },
    roles.map { it.toEntity() },
    updateCache[id]
)

fun net.dv8tion.jda.api.entities.Guild.toEntityLite() = GuildLite(
    id,
    name,
    owner?.user?.id,
    channels.filter { it.type == ChannelType.TEXT || it.type == ChannelType.NEWS }.map { it.toTextEntity() },
    channels.filter { it.type == ChannelType.VOICE || it.type == ChannelType.STAGE }.map { (it as AudioChannel).toVoiceEntity() },
    roles.map { it.toEntity() }
)

fun net.dv8tion.jda.api.entities.User.toEntity() = User(
    id,
    name,
    discriminator,
    isBot
)

fun Member.toEntity() = Member(
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

fun AudioChannel.toVoiceEntity() = VoiceChannel(
    id,
    name,
    members.map { it.user.id },
    if (this is StageChannel) 0 else (this as net.dv8tion.jda.api.entities.VoiceChannel).userLimit,
    PermissionUtil.getExplicitPermission(this, guild.selfMember).toString()
)

fun GuildChannel.toTextEntity() = TextChannel(
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