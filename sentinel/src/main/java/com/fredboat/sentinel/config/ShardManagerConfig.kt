/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.config

import com.fredboat.sentinel.jda.RemoteSessionController
import com.fredboat.sentinel.jda.SubscribeCachePolicy
import com.fredboat.sentinel.jda.VoiceInterceptor
import net.dv8tion.jda.api.GatewayEncoding
import net.dv8tion.jda.api.entities.Message.MentionType
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.messages.MessageRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
class ShardManagerConfig(
    subscribeCachePolicy: SubscribeCachePolicy,
    sentinelProperties: SentinelProperties,
    voiceInterceptor: VoiceInterceptor,
    sessionController: RemoteSessionController
) {

    companion object {
        lateinit var INSTANCE: ShardManager
    }

    private val intents = listOf(
        GatewayIntent.MESSAGE_CONTENT,
        GatewayIntent.DIRECT_MESSAGES,
        GatewayIntent.GUILD_MESSAGES,
        GatewayIntent.GUILD_MEMBERS,
        GatewayIntent.GUILD_VOICE_STATES
    )

    private val shardManager = DefaultShardManagerBuilder.create(sentinelProperties.discordToken, intents)
        .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
        .disableCache(CacheFlag.SCHEDULED_EVENTS, CacheFlag.ACTIVITY, CacheFlag.ONLINE_STATUS, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI, CacheFlag.ROLE_TAGS, CacheFlag.STICKER)
        .setBulkDeleteSplittingEnabled(false)
        .setEnableShutdownHook(true)
        .setUseShutdownNow(true)
        .setAutoReconnect(true)
        .setShardsTotal(sentinelProperties.shardCount)
        .setShards(sentinelProperties.shardStart, sentinelProperties.shardEnd)
        .setSessionController(sessionController)
        .setGatewayEncoding(GatewayEncoding.JSON)
        .setCompression(Compression.ZLIB)
        .setMemberCachePolicy(subscribeCachePolicy)
        .setChunkingFilter(ChunkingFilter.include(sentinelProperties.mainGuild))
        .setVoiceDispatchInterceptor(voiceInterceptor)
        .setRawEventsEnabled(false)
        .setEventPassthrough(true)
        .build()

    init {
        MessageRequest.setDefaultMentions(EnumSet.complementOf(EnumSet.of(
            MentionType.EVERYONE,
            MentionType.HERE,
            MentionType.ROLE
        )))
        INSTANCE = shardManager
    }

    @Bean
    fun shardManager() : ShardManager {
        return shardManager
    }
}