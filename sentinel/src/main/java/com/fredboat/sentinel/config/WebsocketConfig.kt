package com.fredboat.sentinel.config

import com.fredboat.sentinel.io.HandshakeInterceptorImpl
import com.fredboat.sentinel.io.SocketServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebsocketConfig @Autowired constructor(
    server: SocketServer,
    handshakeInterceptor: HandshakeInterceptorImpl
) : WebSocketConfigurer {

    private val server: SocketServer
    private val handshakeInterceptor: HandshakeInterceptorImpl

    init {
        this.server = server
        this.handshakeInterceptor = handshakeInterceptor
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(server, "/").addInterceptors(handshakeInterceptor)
    }
}