/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.entities

data class AudioQueueRequest(
    val type: AudioQueueRequestEnum,
    val guild: String,
    val channel: String? = null // Only used with QUEUE_CONNECT or QUEUE_RECONNECT
)

enum class AudioQueueRequestEnum {
    REMOVE,
    QUEUE_DISCONNECT,
    QUEUE_CONNECT,
    QUEUE_RECONNECT
}

data class VoiceServerUpdate(
    val sessionId: String?,
    val raw: String // The raw JSON from Discord
)