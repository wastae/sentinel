/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.config

import com.fredboat.sentinel.SentinelExchanges
import org.springframework.amqp.core.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitQueuesConfig {

    /* Events */

    //@Bean
    //fun eventExchange() = DirectExchange(SentinelExchanges.EVENTS)

    //@Bean
    //fun eventQueue() = Queue(SentinelExchanges.EVENTS, false)

    /* Requests */

    @Bean
    fun requestExchange() = DirectExchange(SentinelExchanges.REQUESTS)

    @Bean
    fun requestQueue(key: RoutingKey) = createQueue(SentinelExchanges.REQUESTS, key)

    @Bean
    fun requestBinding(
            @Qualifier("requestExchange") requestExchange: DirectExchange,
            @Qualifier("requestQueue") requestQueue: Queue,
            key: RoutingKey
    ): Binding {
        return BindingBuilder.bind(requestQueue).to(requestExchange).with(key.key)
    }

    /* Fanout */

    @Bean
    fun fanoutQueue(key: RoutingKey) = createQueue(SentinelExchanges.FANOUT, key)

    /** The fanout where we will receive broadcast messages from FredBoat */
    @Bean
    fun fanoutExchange(@Qualifier("fanoutQueue") fanoutQueue: Queue): FanoutExchange {
        return FanoutExchange(SentinelExchanges.FANOUT, false, false)
    }

    /** Receive messages from [fanout] to [fanoutQueue] */
    @Bean
    fun fanoutBinding(
            @Qualifier("fanoutQueue") fanoutQueue: Queue,
            @Qualifier("fanoutExchange") fanout: FanoutExchange
    ): Binding {
        return BindingBuilder.bind(fanoutQueue).to(fanout)
    }

    /* Sessions */

    @Bean
    fun sessionsQueue(key: RoutingKey) = createQueue(SentinelExchanges.SESSIONS, key)

    /** The fanout where we will receive broadcast messages from FredBoat */
    @Bean
    fun sessionsExchange(@Qualifier("sessionsQueue") sessionsQueue: Queue): FanoutExchange {
        return FanoutExchange(SentinelExchanges.SESSIONS, false, false)
    }

    /** Receive messages from [sessionsFanout] to [sessionsQueue] */
    @Bean
    fun sessionsBinding(
            @Qualifier("sessionsQueue") sessionsQueue: Queue,
            @Qualifier("sessionsExchange") sessionsFanout: FanoutExchange
    ): Binding {
        return BindingBuilder.bind(sessionsQueue).to(sessionsFanout)
    }

    /** Equivalent to an AnonymousQueue, but with a custom name */
    private fun createQueue(exchange: String, key: RoutingKey)
            = Queue("$exchange-$key", false, true, true)

}