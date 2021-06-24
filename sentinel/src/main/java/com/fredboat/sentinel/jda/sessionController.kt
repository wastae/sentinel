/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.jda

import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.config.SentinelProperties
import com.fredboat.sentinel.entities.AppendSessionEvent
import com.fredboat.sentinel.entities.RemoveSessionEvent
import com.fredboat.sentinel.util.Rabbit
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.utils.SessionController
import net.dv8tion.jda.api.utils.SessionController.SessionConnectNode
import net.dv8tion.jda.api.utils.SessionControllerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

private val log: Logger = LoggerFactory.getLogger(RemoteSessionController::class.java)

@Service
class RemoteSessionController(
        val sentinelProps: SentinelProperties,
        val rabbit: Rabbit,
        val routingKey: RoutingKey
) : SessionController {

    private val adapter = SessionControllerAdapter()
    private val localQueue = ConcurrentHashMap<Int, SessionConnectNode>()
    private var globalRatelimit = -1L
    lateinit var shardManager: ShardManager

    override fun appendSession(node: SessionConnectNode) {
        localQueue[node.shardInfo.shardId] = node
        log.info("Added ${node.shardInfo} to the queue. Queue size is ${localQueue.size}.")
        node.send(false)
    }

    override fun removeSession(node: SessionConnectNode) {
        localQueue.remove(node.shardInfo.shardId)
        log.info("Removed ${node.shardInfo} from the queue. Queue size is ${localQueue.size}.")
        node.send(true)
    }

    /** Sends an event for each shard currently in the queue. Useful for when FredBoat has restarted, and needs
     *  to be aware of the queue. */
    fun syncSessionQueue() {
        localQueue.values.forEach { it.send(false) }
    }
    
    fun onRunRequest(id: Int): String {
        val status = shardManager.getShardById(id)?.status
        log.info("Received request to run shard $id, which has status $status")
        val node = localQueue[id]
        if(node == null) {
            val msg = RemoveSessionEvent(id, sentinelProps.shardCount, routingKey.key)
            rabbit.sendEvent(msg)
            throw IllegalStateException("Node $id is not queued")
        }

        if (shardManager.getShardById(id)?.status == JDA.Status.AWAITING_LOGIN_CONFIRMATION) {
            val msg = "Refusing to run shard $id as it has status $status"
            log.error(msg)
            node.send(true)
            return msg
        }

        node.run(false) // Always assume false, so that we don't immediately return
        removeSession(node)

        return "Started node ${node.shardInfo}" // Generates a reply
    }

    fun SessionConnectNode.send(remove: Boolean) {
        val msg: Any = if (remove) {
            RemoveSessionEvent(shardInfo.shardId, shardInfo.shardTotal, routingKey.key)
        } else {
            AppendSessionEvent(shardInfo.shardId, shardInfo.shardTotal, routingKey.key)
        }
        rabbit.sendEvent(msg)
    }

    /* Handle gateway and global ratelimit */

    override fun getGlobalRatelimit() = globalRatelimit

    override fun setGlobalRatelimit(ratelimit: Long) {
        rabbit.sendSession(SetGlobalRatelimit(ratelimit))
        globalRatelimit = ratelimit
    }

    fun handleRatelimitSet(event: SetGlobalRatelimit) {
        globalRatelimit = event.new
    }

    override fun getGateway(api: JDA) = adapter.getGateway(api)
    override fun getGatewayBot(api: JDA) = adapter.getGatewayBot(api)
}

data class SetGlobalRatelimit(val new: Long)