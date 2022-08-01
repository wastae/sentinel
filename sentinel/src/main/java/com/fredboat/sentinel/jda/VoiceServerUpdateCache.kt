/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.jda

import com.fredboat.sentinel.entities.VoiceServerUpdate
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * When FredBoat restarts and Sentinel remains, something needs to tell FredBoat about our existing voice connections
 * This service takes care of sending updates when receiving them, and when subscribing
 */
@Service
class VoiceServerUpdateCache {

    private val map = ConcurrentHashMap<String, VoiceServerUpdate>()

    operator fun set(guildId: String, update: VoiceServerUpdate) = map.put(guildId, update)
    operator fun get(guildId: String): VoiceServerUpdate? = map[guildId]

    /** Invalidate */
    fun onVoiceLeave(guildId: String) = map.remove(guildId)
}