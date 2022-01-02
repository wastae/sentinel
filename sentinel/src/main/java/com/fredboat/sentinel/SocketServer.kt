package com.fredboat.sentinel

import com.corundumstudio.socketio.SocketIOServer
import com.fredboat.sentinel.jda.JdaRabbitEventListener
import com.fredboat.sentinel.jda.VoiceServerUpdateCache
import com.google.gson.Gson
import net.dv8tion.jda.api.sharding.ShardManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.LinkedHashSet

@Configuration
class SocketServer {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SocketServer::class.java)
        val contextMap = ConcurrentHashMap<UUID, JdaRabbitEventListener>()
        val voiceServerUpdateCache = VoiceServerUpdateCache()
        val subscriptionsCache = LinkedHashSet<Long>()
        val gson = Gson()
    }

    lateinit var shardManager: ShardManager

    @Bean
    fun startSocketServer(): SocketIOServer {
        val config = com.corundumstudio.socketio.Configuration()
        config.hostname = "localhost"
        config.port = 3005

        val socketServer = SocketIOServer(config)

        socketServer.addConnectListener {
            contextMap[it.sessionId] = JdaRabbitEventListener(
                shardManager,
                it
            )
            log.info("Session id ${it.sessionId} connected")
        }

        socketServer.addDisconnectListener {
            contextMap.remove(it.sessionId)?.removeListener()
            log.info("Session id ${it.sessionId} disconnected")
        }

        log.info("Configured SocketIOServer...")
        socketServer.start()
        return socketServer
    }
}