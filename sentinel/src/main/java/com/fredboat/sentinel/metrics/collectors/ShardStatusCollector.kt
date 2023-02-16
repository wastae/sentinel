/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.metrics.collectors

import io.prometheus.client.Collector
import io.prometheus.client.GaugeMetricFamily
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Service

/**
 * Created by napster on 19.04.18.
 */
@Service
class ShardStatusCollector(private val shardManager: ShardManager) : Collector() {

    override fun collect(): List<MetricFamilySamples> {
        val mfs = ArrayList<MetricFamilySamples>()
        val noLabels = emptyList<String>()

        val totalShards = GaugeMetricFamily("fredboat_shards_total",
                "Total shards managed by this instance", noLabels)
        mfs.add(totalShards)

        val shardsConnected = GaugeMetricFamily("fredboat_shards_connected",
                "Total shards managed by this instance that are connected", noLabels)
        mfs.add(shardsConnected)

        val averagePing = GaugeMetricFamily("sentinel_gateway_ping_average",
            "The average ping of all shards", noLabels)
        mfs.add(averagePing)

        totalShards.addMetric(noLabels, shardManager.shards.size.toDouble())
        shardsConnected.addMetric(noLabels, shardManager.shards.stream()
                .filter { shard -> shard.status == JDA.Status.CONNECTED }
                .count().toDouble())
        averagePing.addMetric(noLabels, shardManager.averageGatewayPing)

        return mfs
    }
}