package com.fredboat.sentinel.io

import com.fredboat.sentinel.config.RoutingKey
import com.fredboat.sentinel.config.SentinelProperties
import com.fredboat.sentinel.jda.RemoteSessionController
import com.fredboat.sentinel.jda.SubscriptionCache
import com.fredboat.sentinel.rpc.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.sharding.ShardManager
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Suppress("UastIncorrectHttpHeaderInspection")
@Service
class SocketServer(
    audio: AudioRequests,
    info: InfoRequests,
    management: ManagementRequests,
    message: MessageRequests,
    permission: PermissionRequests,
    subscription: SubscriptionHandler,
    sessionController: RemoteSessionController,
    fanoutConsumer: FanoutConsumer,
    private val sentinelProperties: SentinelProperties,
    private val key: RoutingKey,
    private val subscriptionCache: SubscriptionCache,
    private val shardManager: ShardManager
) : TextWebSocketHandler() {

    private val handlers = WebSocketHandlers(
        audio,
        info,
        management,
        message,
        permission,
        subscription,
        sessionController,
        fanoutConsumer
    )
    private val resumableSessions = mutableMapOf<String, SocketContext>()

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SocketServer::class.java)
        private val gson = Gson()
        val contextMap = ConcurrentHashMap<String, SocketContext>()
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val resumeKey = session.handshakeHeaders.getFirst("Resume-Key")
        val clientName = session.handshakeHeaders.getFirst("Client-Name")

        var resumable: SocketContext? = null
        if (resumeKey != null) resumable = resumableSessions.remove(resumeKey)

        if (resumable != null) {
            contextMap[session.id] = resumable
            resumable.resume(session)
            log.info("Resumed session with key $resumeKey")
            return
        }

        contextMap[session.id] = SocketContext(
            sentinelProperties,
            key,
            gson,
            subscriptionCache,
            shardManager,
            this,
            session
        )

        log.info("Connection successfully established with $clientName")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val context = contextMap.remove(session.id) ?: return
        if (context.resumeKey != null) {
            resumableSessions.remove(context.resumeKey!!)?.let { removed ->
                log.warn("Shutdown resumable session with key ${removed.resumeKey} because it has the same key as a " +
                        "newly disconnected resumable session.")
                removed.shutdown()
            }

            resumableSessions[context.resumeKey!!] = context
            context.pause()
            log.info("Connection closed from {} with status {} -- " +
                    "Session can be resumed within the next {} seconds with key {}",
                session.remoteAddress,
                status,
                context.resumeTimeout,
                context.resumeKey)
            return
        }

        log.info("Connection closed from {} -- {}", session.remoteAddress, status)

        context.shutdown()
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                log.debug(message.payload)
                handleTextMessageSafe(session, message)
            } catch (e: Exception) {
                log.error("Exception while handling websocket message", e)
            }
        }
    }

    private fun handleTextMessageSafe(session: WebSocketSession, message: TextMessage) {
        val json = JSONObject(message.payload)

        log.debug(message.payload)

        if (!session.isOpen) {
            log.error("Ignoring closing websocket: " + session.remoteAddress!!)
            return
        }

        val context = contextMap[session.id]
            ?: throw IllegalStateException("No context for session ID ${session.id}. Broken websocket?")

        when (json.getString("op")) {
            "request"           -> handlers.consume(context, json)
            "configureResuming" -> handlers.configureResuming(context, json)
            else -> log.warn("Unexpected operation: " + json.getString("op"))
        }
    }

    internal fun onSessionResumeTimeout(context: SocketContext) {
        resumableSessions.remove(context.resumeKey)
        context.shutdown()
    }

    internal fun canResume(key: String) = resumableSessions[key]?.stopResumeTimeout() ?: false
}
