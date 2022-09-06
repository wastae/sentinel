package com.fredboat.sentinel.jda

import com.fredboat.sentinel.io.SocketServer
import com.fredboat.sentinel.entities.VoiceServerUpdate
import com.google.gson.Gson
import net.dv8tion.jda.api.hooks.VoiceDispatchInterceptor
import org.springframework.stereotype.Component

@Component
class VoiceInterceptor(private val voiceServerUpdateCache: VoiceServerUpdateCache) : VoiceDispatchInterceptor {

    private val gson = Gson()

    override fun onVoiceServerUpdate(update: VoiceDispatchInterceptor.VoiceServerUpdate) {
        val json = RawServerUpdateJson(update.endpoint, update.guildId, update.token).toString()
        val event = VoiceServerUpdate(update.sessionId, json)
        voiceServerUpdateCache[update.guildId] = event
        SocketServer.contextMap.forEach {
            it.value.sendResponse(VoiceServerUpdate::class.java.simpleName, it.value.gson.toJson(event))
        }
    }

    override fun onVoiceStateUpdate(update: VoiceDispatchInterceptor.VoiceStateUpdate) = false

    private inner class RawServerUpdateJson(val endpoint: String, val guild_id: String, val token: String) {
        override fun toString(): String = gson.toJson(this)
    }
}