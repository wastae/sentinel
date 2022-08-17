package com.fredboat.sentinel.jda

import com.fredboat.sentinel.SocketServer
import com.fredboat.sentinel.config.SentinelProperties
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.utils.MemberCachePolicy

class SubscribeCachePolicy(private val sentinelProperties: SentinelProperties) : MemberCachePolicy {

    override fun cacheMember(member: Member): Boolean {
        // Don't unload
        // 1. Main guild
        // 2. Members that in voice
        // 3. Members that in guild which is subscribed
        return sentinelProperties.mainGuild == member.guild.idLong || member.voiceState?.channel != null || SocketServer.subscriptionsCache.contains(member.guild.idLong)
    }
}