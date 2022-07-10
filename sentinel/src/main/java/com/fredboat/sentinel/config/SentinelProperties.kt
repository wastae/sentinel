/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "sentinel")
class SentinelProperties(
    var address: String = "",
    var port: Int = 8989,
    var password: String = "youshallnotpass",
    var discordToken: String = "",
    var shardStart: Int = 0,
    var shardEnd: Int = 0,
    var shardCount: Int = 1,
    var gatewayProxy: String = "",
    var instance: String = "unknown",
    var mainGuild: Long = 0L
) {
    override fun toString() = "$instance: [$shardStart..$shardEnd/$shardCount]"
}