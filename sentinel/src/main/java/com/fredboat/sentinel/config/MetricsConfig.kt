/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.config

import io.prometheus.client.guava.cache.CacheMetricsCollector
import io.prometheus.client.logback.InstrumentedAppender
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class MetricsConfig {

    //guava cache metrics
    @Bean
    open fun cacheMetrics(): CacheMetricsCollector {
        return CacheMetricsCollector().register()
    }

    @Bean
    open fun instrumentedAppender(): InstrumentedAppender {
        return InstrumentedAppender()
    }

}