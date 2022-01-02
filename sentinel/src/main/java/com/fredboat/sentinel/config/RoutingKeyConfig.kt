/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*


@Configuration
class RoutingKeyConfig {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RoutingKeyConfig::class.java)
    }

    @Bean
    fun routingKey(props: SentinelProperties): RoutingKey {
        val rand = UUID.randomUUID().toString().replace("-", "").substring(0, 4)
        val id = "${props.instance}-$rand"
        log.info("Unique identifier for this session: $id")
        return RoutingKey(id)
    }
}