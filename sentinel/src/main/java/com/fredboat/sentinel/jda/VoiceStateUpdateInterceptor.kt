/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.jda

import net.dv8tion.jda.core.entities.impl.JDAImpl
import net.dv8tion.jda.core.handle.VoiceStateUpdateHandler
import org.json.JSONObject

class VoiceStateUpdateInterceptor(jda: JDAImpl) : VoiceStateUpdateHandler(jda) {

    override fun handleInternally(content: JSONObject): Long? {
        val guildId = if (content.has("guild_id")) content.getLong("guild_id") else null
        if (guildId != null && jda.guildSetupController.isLocked(guildId))
            return guildId
        if (guildId == null)
            return super.handleInternally(content)

        val userId = content.getLong("user_id")
        val channelId = if (!content.isNull("channel_id")) content.getLong("channel_id") else null
        val guild = jda.getGuildById(guildId) ?: return super.handleInternally(content)

        val member = guild.getMemberById(userId) ?: return super.handleInternally(content)

        // We only need special handling if our own state is modified
        if (member != guild.selfMember) return super.handleInternally(content)

        val channel = if (channelId != null) guild.getVoiceChannelById(channelId) else null

        /* These should be handled by JDA jda on FredBoat
        if (channelId == null) {
            // Null channel means disconnected
            if (link.getState() !== Link.State.DESTROYED) {
                link.onDisconnected()
            }
        } else if (channel != null) {
            link.setChannel(channel) // Change expected channel
        }*/

        //if (link.getState() === Link.State.CONNECTED) {
        // This may be problematic
        jda.client.updateAudioConnection(guildId, channel)
        //}

        return super.handleInternally(content)
    }

}