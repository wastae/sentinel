package com.fredboat.sentinel

import com.corundumstudio.socketio.SocketIOServer
import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.config.SentinelProperties
import com.fredboat.sentinel.jda.JdaWebsocketEventListener
import com.fredboat.sentinel.jda.VoiceServerUpdateCache
import com.fredboat.sentinel.rpc.FanoutConsumer
import com.google.gson.Gson
import net.dv8tion.jda.api.sharding.ShardManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ConcurrentHashMap

@Configuration
class SocketServer(
    private val sentinelProperties: SentinelProperties,
    private val key: RoutingKey,
    private val voiceServerUpdateCache: VoiceServerUpdateCache
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SocketServer::class.java)
        val contextMap = ConcurrentHashMap<String, JdaWebsocketEventListener>()
        val subscriptionsCache = LinkedHashSet<Long>()
        val gson = Gson()
    }

    lateinit var shardManager: ShardManager

    @Bean
    fun startSocketServer(): SocketIOServer {
        val config = com.corundumstudio.socketio.Configuration()
        config.hostname = sentinelProperties.address
        config.port = sentinelProperties.port

        val socketServer = SocketIOServer(config)

        socketServer.addConnectListener {
            if (it.handshakeData.urlParams["auth"]?.get(0).equals(sentinelProperties.password)) {
                val botId = it.handshakeData.urlParams["id"]?.get(0)
                if (botId != null) {
                    val oldConnection = contextMap[botId]
                    if (oldConnection == null) {
                        contextMap[botId] = JdaWebsocketEventListener(shardManager, voiceServerUpdateCache, it)
                        it.sendEvent("initialEvent", key.key)
                        FanoutConsumer.sendHello(it, sentinelProperties, key)
                        log.info("Bot with ID $botId connected for listening events to server with key ${key.key}")
                    } else {
                        it.sendEvent("reconnectEvent", key.key)
                        FanoutConsumer.sendHello(it, sentinelProperties, key)
                        oldConnection.resume(it)
                        log.info("Resumed events for bot with ID $botId")
                    }
                }
            } else {
                it.disconnect()
            }
        }

        socketServer.addDisconnectListener {
            val botId = it.handshakeData.urlParams["id"]?.get(0)
            if (botId != null) {
                contextMap[botId]?.pause()
                log.info("Bot with ID $botId disconnected from listening events")
            }
        }

        log.info("Configured SocketIOServer...")
        socketServer.start()
        return socketServer
    }
}