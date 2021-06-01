/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.entities

/** Sent by FredBoat to fanout. We will respond with [SentinelHello] */
class FredBoatHello(
        /** Distinguishes between sessions.
         * We must clear our subscriptions and requeue sessions when a new one is started. */
        val id: String,
        /** Discord status */
        val game: String
)

/** Sent when Sentinel starts or [FredBoatHello] is received.
 *  Used for mapping what Sentinels we have in FredBoat */
data class SentinelHello(
        val shards: Set<Int>,
        val shardCount: Int,
        val key: String,
        val time: Long = System.currentTimeMillis()
)