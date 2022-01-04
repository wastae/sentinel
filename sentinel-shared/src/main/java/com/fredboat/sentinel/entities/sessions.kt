/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.entities

/** Tells FredBoat about a queued session */
data class AppendSessionEvent(
        val shardId: Int,
        val totalShards: Int,
        val routingKey: String
)

/** Tells FredBoat that a shard no-longer needs running */
data class RemoveSessionEvent(
        val shardId: Int,
        val totalShards: Int,
        val routingKey: String
)

/** Tells Sentinel that a shard should run */
data class RunSessionRequest(val shardId: Int, val responseId: String)

/** Tells Sentinel to send [AppendSessionEvent] for each queued node */
class SyncSessionQueueRequest