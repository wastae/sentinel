/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.jda

import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.handle.VoiceStateUpdateHandler
import net.dv8tion.jda.api.utils.data.DataObject


class VoiceStateUpdateInterceptor(jda: JDAImpl) : VoiceStateUpdateHandler(jda) {

    override fun handleInternally(content: DataObject): Long? {
        val guildId = content.getLong("guild_id")
        if (jda.guildSetupController.isLocked(guildId.toLong()))
            return guildId

        val userId = content.getLong("user_id")
        val channelId = content.getLong("channel_id")
        val guild = jda.getGuildById(guildId.toLong()) ?: return super.handleInternally(content)

        val member = guild.getMemberById(userId.toLong()) ?: return super.handleInternally(content)

        // We only need special handling if our own state is modified
        if (member != guild.selfMember) return super.handleInternally(content)

        val channel = guild.getVoiceChannelById(channelId.toLong())

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
        jda.client.updateAudioConnection(guildId.toLong(), channel)
        //}

        return super.handleInternally(content)
    }

}