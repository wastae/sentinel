/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel

object SentinelExchanges {
    const val JDA = "sentinel.jda"
    const val REQUESTS = "sentinel.requests"
    const val FANOUT = "sentinel.fanout"
    const val SESSIONS = "sentinel.sessions"
}
