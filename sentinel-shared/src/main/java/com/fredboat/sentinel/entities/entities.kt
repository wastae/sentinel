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
    val id: String,
    val name: String,
    val owner: String?, // Discord has a history of having guilds without owners :(
    val members: List<Member>,
    val textChannels: List<TextChannel>,
    val voiceChannels: List<VoiceChannel>,
    val roles: List<Role>,
    val voiceServerUpdate: VoiceServerUpdate?
)

data class User(
    val id: String,
    val name: String,
    val discrim: String,
    val bot: Boolean
)

data class Member(
    val id: String,
    val name: String,
    val nickname: String?,
    val discrim: String,
    val guildId: String,
    val bot: Boolean,
    val roles: List<String>,
    val permissions: String,
    val voiceChannel: String?
)

data class RedisMember(
    val byteArray: ByteArray
)

data class TextChannel(
    val id: String,
    val name: String,
    val ourEffectivePermissions: String
)

data class VoiceChannel(
    val id: String,
    val name: String,
    val members: List<String>,
    val userLimit: Int,
    val ourEffectivePermissions: String
)

data class Role(
    val id: String,
    val name: String,
    val permissions: String
)