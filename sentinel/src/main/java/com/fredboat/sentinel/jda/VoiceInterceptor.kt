package com.fredboat.sentinel.jda

import com.fredboat.sentinel.entities.VoiceServerUpdate
import com.fredboat.sentinel.util.Rabbit
import com.google.gson.Gson
import net.dv8tion.jda.api.hooks.VoiceDispatchInterceptor
import org.springframework.stereotype.Component

@Component
class VoiceInterceptor(private val rabbit: Rabbit, val cache: VoiceServerUpdateCache) : VoiceDispatchInterceptor {
    private val gson = Gson()

    override fun onVoiceServerUpdate(update: VoiceDispatchInterceptor.VoiceServerUpdate) {
        val json = RawServerUpdateJson(update.endpoint, update.guildId, update.token).toString()
        val event = VoiceServerUpdate(update.sessionId, json)
        cache[update.guildIdLong] = event

        rabbit.sendEvent(event)
    }

    override fun onVoiceStateUpdate(update: VoiceDispatchInterceptor.VoiceStateUpdate) = false

    private inner class RawServerUpdateJson(val endpoint: String, val guild_id: String, val token: String) {
        override fun toString(): String = gson.toJson(this)
    }
}