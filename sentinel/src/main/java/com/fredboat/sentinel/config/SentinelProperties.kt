/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream

@Component
@ConfigurationProperties(prefix = "sentinel")
class SentinelProperties(
        discordToken: String = "",
        var sentinelId: Int = 0,
        var sentinelCount: Int = 1,
        var shardCount: Int = 1,
        var instance: String = "unknown"
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SentinelProperties::class.java)
    }

    private var _discordToken = discordToken
    var discordToken: String
        get() {
            if (_discordToken.isBlank()) {
                val file = File("common.yml")
                if (file.exists()) {
                    val yaml = Yaml()
                    val map = yaml.load<Map<String, Any>>(FileInputStream(file))
                    val newToken = map["discordToken"] as? String
                    if (newToken != null) {
                        log.info("Discovered token in ${file.absolutePath}")
                        _discordToken = newToken
                    } else {
                        log.error("Found ${file.absolutePath} but no token!")
                    }
                } else {
                    log.warn("common.yml is missing and no token was defined by Spring")
                }
            }

            if (_discordToken.isBlank()) {
                throw RuntimeException("No discord bot token provided." +
                        "\nMake sure to put a discord bot token into your common.yml file.")
            }

            return _discordToken
        }
        set(value) { _discordToken = value }

    fun getShards() = (0 until shardCount)
        .filter { it % sentinelCount == sentinelId }
        .toSet()

    override fun toString() = "$instance: [$sentinelId/$sentinelCount]"
}
