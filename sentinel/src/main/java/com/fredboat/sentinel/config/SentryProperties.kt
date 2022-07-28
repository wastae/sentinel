/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Created by napster on 27.07.18.
 */
@Component
@ConfigurationProperties(prefix = "sentry")
class SentryProperties(
    var dsn: String = "",
    var tags: Map<String, String> = HashMap()
)
