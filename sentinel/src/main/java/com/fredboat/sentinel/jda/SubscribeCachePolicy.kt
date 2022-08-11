package com.fredboat.sentinel.jda

import com.fredboat.sentinel.SocketServer
import com.fredboat.sentinel.config.SentinelProperties
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.utils.MemberCachePolicy

class SubscribeCachePolicy(private val sentinelProperties: SentinelProperties) : MemberCachePolicy {

    override fun cacheMember(member: Member): Boolean {
        return sentinelProperties.mainGuild == member.guild.idLong || SocketServer.subscriptionsCache.contains(member.guild.idLong)
    }
}