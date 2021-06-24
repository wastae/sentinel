/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.StringWriter

/**
 * Created by napster on 27.07.18.
 */
@RestController
@RequestMapping("/metrics")
class MetricsEndpoint {

    val registry: CollectorRegistry = CollectorRegistry.defaultRegistry

    @GetMapping(produces = [(TextFormat.CONTENT_TYPE_004)])
    fun getMetrics(@RequestParam(name = "name[]", required = false) includedParam: Array<String>?): String {
        return buildAnswer(includedParam)
    }

    private fun buildAnswer(includedParam: Array<String>?): String {
        val params: Set<String> =
                if (includedParam == null) {
                    emptySet()
                } else {
                    HashSet(listOf(*includedParam))
                }

        val writer = StringWriter()
        writer.use {
            TextFormat.write004(it, registry.filteredMetricFamilySamples(params))
            it.flush()
        }

        return writer.toString()
    }
}
