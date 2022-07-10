/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.config

import com.fredboat.sentinel.ApplicationState
import com.fredboat.sentinel.SocketServer
import com.fredboat.sentinel.jda.RemoteSessionController
import com.fredboat.sentinel.jda.VoiceInterceptor
import net.dv8tion.jda.api.entities.Message.MentionType
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.requests.restaction.MessageAction
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import javax.security.auth.login.LoginException

@Configuration
class ShardManagerConfig {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ShardManagerConfig::class.java)
    }
    @Bean
    fun buildShardManager(
        sentinelProperties: SentinelProperties,
        socketServer: SocketServer,
        sessionController: RemoteSessionController,
        voiceInterceptor: VoiceInterceptor
    ): ShardManager {

        val intents = listOf(
            GatewayIntent.DIRECT_MESSAGES,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.GUILD_VOICE_STATES,
            GatewayIntent.GUILD_MEMBERS
        )

        val builder = DefaultShardManagerBuilder.create(sentinelProperties.discordToken, intents)
            .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
            .disableCache(CacheFlag.ACTIVITY, CacheFlag.ONLINE_STATUS, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI, CacheFlag.ROLE_TAGS, CacheFlag.STICKER)
            .setBulkDeleteSplittingEnabled(false)
            .setEnableShutdownHook(false)
            .setUseShutdownNow(true)
            .setAutoReconnect(true)
            .setShardsTotal(sentinelProperties.shardCount)
            .setShards(sentinelProperties.shardStart, sentinelProperties.shardEnd)
            .setSessionController(sessionController)
            .setCompression(Compression.NONE)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setChunkingFilter(ChunkingFilter.include(sentinelProperties.mainGuild))
            .setVoiceDispatchInterceptor(voiceInterceptor)
            .setRawEventsEnabled(false)
            .setEventPassthrough(true)

        val shardManager: ShardManager
        try {
            MessageAction.setDefaultMentions(EnumSet.complementOf(EnumSet.of(
                MentionType.EVERYONE,
                MentionType.HERE,
                MentionType.ROLE
            )))
            shardManager = builder.build()
            sessionController.shardManager = shardManager
            socketServer.shardManager = shardManager
            if (ApplicationState.isTesting) {
                log.info("Shutting down JDA because we are running tests")
                try {
                    shardManager.shutdown()
                } catch (npe: NullPointerException) {
                    // Race condition
                    Thread.sleep(500)
                    shardManager.shutdown()
                }
            }
        } catch (e: LoginException) {
            throw RuntimeException("Failed to log in to Discord! Is your token invalid?", e)
        }

        return shardManager
    }

    @Bean
    fun guildSubscriptions(): MutableSet<Long> = Collections.synchronizedSet(HashSet())
}