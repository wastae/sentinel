/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.config

import com.fredboat.sentinel.ApplicationState
import com.fredboat.sentinel.jda.JdaRabbitEventListener
import com.fredboat.sentinel.jda.RemoteSessionController
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.SessionController
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.entities.Activity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import java.util.Arrays
import javax.security.auth.login.LoginException
import kotlin.collections.HashSet

@Configuration
class ShardManagerConfig {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ShardManagerConfig::class.java)
    }
    @Bean
    fun buildShardManager(sentinelProperties: SentinelProperties,
                          rabbitEventListener: JdaRabbitEventListener,
                          sessionController: RemoteSessionController
    ): ShardManager {

        val INTENTS = listOf(
            GatewayIntent.DIRECT_MESSAGES,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.GUILD_MESSAGE_REACTIONS,
            GatewayIntent.GUILD_VOICE_STATES
        )

        val builder = DefaultShardManagerBuilder.create(sentinelProperties.discordToken, INTENTS)
                .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOTE)
                .setBulkDeleteSplittingEnabled(false)
                .setEnableShutdownHook(false)
                .setAutoReconnect(true)
                .setShardsTotal(sentinelProperties.shardCount)
                .setShards(sentinelProperties.shardStart, sentinelProperties.shardEnd)
                .setSessionController(sessionController)
                .setChunkingFilter(ChunkingFilter.NONE)
                .addEventListeners(rabbitEventListener)

        val shardManager: ShardManager
        try {
            shardManager = builder.build()
            sessionController.shardManager = shardManager
            rabbitEventListener.shardManager = shardManager
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
    fun guildSubscriptions(): MutableSet<Long> = Collections.synchronizedSet(HashSet<Long>())

}