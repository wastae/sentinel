/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.jda

import com.fredboat.sentinel.io.SocketServer
import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.config.SentinelProperties
import com.fredboat.sentinel.entities.AppendSessionEvent
import com.fredboat.sentinel.entities.RemoveSessionEvent
import com.fredboat.sentinel.entities.RunSessionRequest
import com.fredboat.sentinel.io.SocketContext
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.SessionController.SessionConnectNode
import net.dv8tion.jda.api.utils.SessionController.ShardedGateway
import net.dv8tion.jda.api.utils.SessionControllerAdapter
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class RemoteSessionController(
    val sentinelProps: SentinelProperties,
    val routingKey: RoutingKey
) : SessionControllerAdapter() {

    private val adapter = SessionControllerAdapter()
    private val localQueue = ConcurrentHashMap<Int, SessionConnectNode>()
    lateinit var shardManager: ShardManager

    override fun appendSession(node: SessionConnectNode) {
        localQueue[node.shardInfo.shardId] = node
        log.info("Added ${node.shardInfo} to the queue. Queue size is ${localQueue.size}.")
        node.send(false)
    }

    override fun removeSession(node: SessionConnectNode) {
        if (node.jda.status == JDA.Status.RECONNECT_QUEUED) {
            log.info("${node.shardInfo} is reconnecting, not removing it from queue. Queue size is ${localQueue.size}")
            return
        }
        localQueue.remove(node.shardInfo.shardId)
        log.info("Removed ${node.shardInfo} from the queue. Queue size is ${localQueue.size}.")
        node.send(true)
    }

    /** Sends an event for each shard currently in the queue. Useful for when FredBoat has restarted, and needs
     *  to be aware of the queue. */
    fun syncSessionQueue() {
        localQueue.values.forEach { it.send(false) }
    }

    fun onRunRequest(request: RunSessionRequest, context: SocketContext) {
        val status = shardManager.getShardById(request.shardId)?.status
        log.info("Received request to run shard ${request.shardId}, which has status $status")
        val node = localQueue[request.shardId]
        if (node == null) {
            context.sendResponse(RemoveSessionEvent::class.java.simpleName, context.gson.toJson(RemoveSessionEvent(
                request.shardId, sentinelProps.shardCount, routingKey.key
            )), request.responseId)
            throw IllegalStateException("Node ${request.shardId} is not queued")
        }

        if (shardManager.getShardById(request.shardId)?.status == JDA.Status.AWAITING_LOGIN_CONFIRMATION) {
            val msg = "Refusing to run shard ${request.shardId} as it has status $status:${request.responseId}"
            log.error(msg)
            node.send(true)
            context.sendResponse("OnRunResponse", msg, request.responseId, false)
        }

        node.run(false) // Always assume false, so that we don't immediately return
        removeSession(node)

        context.sendResponse(
            "OnRunResponse",
            "Started node ${node.shardInfo}:${request.responseId}",
            request.responseId,
            false
        )
    }

    fun SessionConnectNode.send(remove: Boolean) {
        if (remove) {
            SocketServer.contextMap.forEach {
                it.value.sendResponse(RemoveSessionEvent::class.java.simpleName, it.value.gson.toJson(RemoveSessionEvent(
                    shardInfo.shardId, shardInfo.shardTotal, routingKey.key
                )), "0")
            }
        } else {
            SocketServer.contextMap.forEach {
                it.value.sendResponse(AppendSessionEvent::class.java.simpleName, it.value.gson.toJson(AppendSessionEvent(
                    shardInfo.shardId, shardInfo.shardTotal, routingKey.key
                )), "0")
            }
        }
    }

    /* Handle gateway and global ratelimit */

    override fun getGlobalRatelimit(): Long = globalRatelimit.get()

    override fun setGlobalRatelimit(ratelimit: Long) {
        // This event should be sent to all other sentinels
        //SocketServer.contextMap.forEach {
        //    it.value.sendResponse(SetGlobalRatelimit::class.java.simpleName, it.value.gson.toJson(SetGlobalRatelimit(
        //        ratelimit
        //    )))
        //}
        globalRatelimit.set(ratelimit)
    }

    override fun getGateway(): String {
        return sentinelProps.gatewayProxy.ifBlank {
            adapter.gateway
        }
    }

    override fun getShardedGateway(api: JDA): ShardedGateway {
        return if (sentinelProps.gatewayProxy.isNotBlank()) {
            ShardedGateway(sentinelProps.gatewayProxy, sentinelProps.shardCount)
        } else {
            adapter.getShardedGateway(api)
        }
    }
}

//data class SetGlobalRatelimit(val new: Long)