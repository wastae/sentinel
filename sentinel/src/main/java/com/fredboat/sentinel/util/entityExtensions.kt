/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.util

import com.fredboat.sentinel.entities.ExtendedShardInfo
import com.fredboat.sentinel.entities.Guild
import com.fredboat.sentinel.entities.GuildLite
import com.fredboat.sentinel.entities.Member
import com.fredboat.sentinel.entities.Option
import com.fredboat.sentinel.entities.Role
import com.fredboat.sentinel.entities.Shard
import com.fredboat.sentinel.entities.ShardStatus
import com.fredboat.sentinel.entities.TextChannel
import com.fredboat.sentinel.entities.User
import com.fredboat.sentinel.entities.VoiceChannel
import com.fredboat.sentinel.jda.VoiceServerUpdateCache
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.internal.utils.PermissionUtil

fun JDA.toEntity() = Shard(
    shardInfo.shardId,
    shardInfo.shardTotal,
    status.toEntity()
)

fun JDA.toEntityExtended() = ExtendedShardInfo(
    toEntity(),
    guildCache.size().toString(),
    userCache.size().toString()
)

fun net.dv8tion.jda.api.entities.Guild.toEntity(updateCache: VoiceServerUpdateCache) = Guild(
    id,
    name,
    iconUrl,
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
    iconUrl,
    owner?.user?.id,
    channels.filter { it.type == ChannelType.TEXT || it.type == ChannelType.NEWS }.map { it.toTextEntity() },
    channels.filter { it.type == ChannelType.VOICE || it.type == ChannelType.STAGE }.map { (it as AudioChannel).toVoiceEntity() },
    roles.map { it.toEntity() }
)

fun net.dv8tion.jda.api.entities.User.toEntity() = User(
    id,
    name,
    discriminator,
    effectiveAvatarUrl,
    isBot
)

fun net.dv8tion.jda.api.entities.Member.toEntity() = Member(
    user.id,
    user.name,
    nickname,
    user.discriminator,
    effectiveAvatarUrl,
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
    if (this is StageChannel) 0 else (this as net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel).userLimit,
    PermissionUtil.getEffectivePermission(this.permissionContainer, guild.selfMember).toString()
)

fun GuildChannel.toTextEntity() = TextChannel(
    id,
    name,
    PermissionUtil.getEffectivePermission(this.permissionContainer, guild.selfMember).toString()
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
