/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.rpc

import com.fredboat.sentinel.SentinelExchanges
import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.config.SentinelProperties
import com.fredboat.sentinel.entities.FredBoatHello
import com.fredboat.sentinel.entities.SentinelHello
import com.fredboat.sentinel.entities.SyncSessionQueueRequest
import com.fredboat.sentinel.jda.RemoteSessionController
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
@RabbitListener(queues = ["#{fanoutQueue.name}"], errorHandler = "rabbitListenerErrorHandler")
class FanoutConsumer(
        private val template: RabbitTemplate,
        private val sentinelProperties: SentinelProperties,
        private val key: RoutingKey,
        @param:Qualifier("guildSubscriptions")
        private val subscriptions: MutableSet<Long>,
        private val shardManager: ShardManager,
        private val sessionController: RemoteSessionController
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(FanoutConsumer::class.java)
    }

    var knownFredBoatId: String? = null

    init {
        sendHello()
    }

    @RabbitHandler
    fun onHello(event: FredBoatHello) {
        if (event.id != knownFredBoatId) {
            log.info("FredBoat ${event.id} says hello \uD83D\uDC4B - Replaces $knownFredBoatId")
            knownFredBoatId = event.id
            subscriptions.clear()
            sessionController.syncSessionQueue()
        } else {
            log.info("FredBoat ${event.id} says hello \uD83D\uDC4B")
        }

        sendHello()
    }

    private fun sendHello() {
        val message = sentinelProperties.run {  SentinelHello(
                shardStart,
                shardEnd,
                shardCount,
                key.key
        )}
        template.convertAndSend(SentinelExchanges.EVENTS, message)
    }

    @RabbitHandler
    fun consume(request: SyncSessionQueueRequest) {
        sessionController.syncSessionQueue()
    }

}