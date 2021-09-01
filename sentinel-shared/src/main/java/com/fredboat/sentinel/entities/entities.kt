/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.entities

@Suppress("MemberVisibilityCanBePrivate")
data class Shard(
        val id: Int,
        val total: Int,
        val status: ShardStatus
)

// Causes problems if member
@Suppress("unused")
val Shard.shardString: String get() = "[$id/$total]"

data class Guild(
        val id: Long,
        val name: String,
        val owner: Long?, // Discord has a history of having guilds without owners :(
        val members: List<Member>,
        val textChannels: List<TextChannel>,
        val voiceChannels: List<VoiceChannel>,
        val roles: List<Role>,
        val voiceServerUpdate: VoiceServerUpdate?
)

data class User(
        val id: Long,
        val name: String,
        val discrim: String,
        val bot: Boolean
)

data class Member(
        val id: Long,
        val name: String,
        val nickname: String?,
        val discrim: String,
        val guildId: Long,
        val bot: Boolean,
        val roles: List<Long>,
        val permissions: Long,
        val voiceChannel: Long?
)

data class TextChannel(
        val id: Long,
        val name: String,
        val ourEffectivePermissions: Long
)

data class VoiceChannel(
        val id: Long,
        val name: String,
        val members: List<Long>,
        val userLimit: Int,
        val ourEffectivePermissions: Long
)

data class Role(
        val id: Long,
        val name: String,
        val permissions: Long
)