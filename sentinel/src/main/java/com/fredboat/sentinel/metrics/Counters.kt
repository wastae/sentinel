/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.metrics

import io.prometheus.client.Counter

object Counters {

    val failedSentinelRequests = Counter.build()
            .name("requests_failed_total")
            .help("All failed requests consumed by Sentinel")
            .labelNames("class")
            .register()!!

    // ################################################################################
    // ##                              JDA Stats
    // ################################################################################

    val jdaEvents = Counter.build()
            .name("jda_events_received_total")
            .help("All jda that JDA provides us with by class")
            .labelNames("class") //GuildJoinedEvent, MessageReceivedEvent, ReconnectEvent etc
            .register()!!

    val successfulRestActions = Counter.build()
            .name("jda_restactions_successful_total")
            .help("Total successful JDA restactions sent by FredBoat")
            .labelNames("restaction") // sendMessage, deleteMessage, sendTyping etc
            .register()!!

    val failedRestActions = Counter.build()
            .name("jda_restactions_failed_total")
            .help("Total failed JDA restactions sent by FredBoat")
            .labelNames("restaction", "error_response_code") //Use the error response codes like: 50013, 10008 etc
            .register()!!

    /**
     * Code prefixed with c or s for client and server, respectively
     */
    val shardDisconnects = Counter.build()
            .name("jda_disconnects")
            .help("Total shard disconnects")
            .labelNames("code")
            .register()!!

}