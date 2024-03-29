/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.entities.AudioQueueRequest
import com.fredboat.sentinel.entities.AudioQueueRequestEnum.*
import com.fredboat.sentinel.rpc.meta.SentinelRequest
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.internal.JDAImpl
import org.springframework.stereotype.Service

/**
 * Requests related to audio
 */
@Service
@SentinelRequest
class AudioRequests(private val shardManager: ShardManager) {

    @SentinelRequest
    fun consume(request: AudioQueueRequest) {
        val guild: Guild = shardManager.getGuildById(request.guild)
                ?: throw RuntimeException("Guild ${request.guild} not found")

        val jda = guild.jda as JDAImpl

        when (request.type) {
            REMOVE -> jda.client.removeAudioConnection(request.guild)
            QUEUE_DISCONNECT -> jda.client.queueAudioDisconnect(guild)
            QUEUE_CONNECT -> {
                val vc = guild.getVoiceChannelById(request.channel!!)
                        ?: throw RuntimeException("Channel ${request.channel} not found in guild $guild")

                jda.client.queueAudioConnect(vc)
            }
        }
    }

}