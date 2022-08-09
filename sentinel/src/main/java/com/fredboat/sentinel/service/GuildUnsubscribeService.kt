package com.fredboat.sentinel.service

import com.fredboat.sentinel.rpc.SubscriptionHandler
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.*
import net.dv8tion.jda.internal.handle.EventCache
import net.dv8tion.jda.internal.utils.cache.SnowflakeCacheViewImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Suppress("SameParameterValue")
@Service
@EnableScheduling
class GuildUnsubscribeService(
    private val shardManager: ShardManager
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GuildUnsubscribeService::class.java)
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    private fun invalidateCache() {
        if (SubscriptionHandler.unsubscribeQueue.isNotEmpty()) {
            val iterator = SubscriptionHandler.unsubscribeQueue.iterator()
            while (iterator.hasNext()) {
                val id = iterator.next()

                shardManager.getGuildById(id)?.let {
                    invalidateGuildCache(it)
                }

                iterator.remove()
            }

            log.info("Total users cache size after invalidating ${shardManager.userCache.size()}")
        }
    }

    private fun invalidateGuildCache(guild: Guild) {
        val membersView = (guild as GuildImpl).membersView
        membersView.writeLock().use {
            membersView.map.forEachEntry { id, member ->
                if (id != guild.jda.selfUser.idLong) {
                    updateMemberCache(member as MemberImpl, guild.jda, true)
                }; true
            }
        }
    }

    private fun updateMemberCache(member: MemberImpl, jda: JDAImpl, forceRemove: Boolean): Boolean {
        val guild = member.guild
        val user = member.user as UserImpl
        val membersView = guild.membersView
        if (forceRemove) {
            if (membersView.remove(member.idLong) == null) return false
            log.debug("Unloading member {}", member)
            if (user.mutualGuilds.isEmpty()) {
                user.setFake(true)
                jda.usersView.remove(user.idLong)
            }
            val voiceState = member.voiceState as GuildVoiceStateImpl?
            if (voiceState != null) {
                val connectedChannel = voiceState.channel as VoiceChannelImpl?
                connectedChannel?.connectedMembersMap?.remove(member.idLong)
                voiceState.setConnectedChannel(null)
            }

            return false
        } else if (guild.getMemberById(member.idLong) != null) {
            return true
        }

        log.debug("Loading member {}", member)

        if (jda.getUserById(user.idLong) == null) {
            val usersView: SnowflakeCacheViewImpl<User> = jda.usersView
            usersView.writeLock().use { usersView.map.put(user.idLong, user) }
        }

        membersView.writeLock().use {
            membersView.map.put(member.idLong, member)
            if (member.isOwner) guild.owner = member
        }

        val hashId = guild.idLong xor user.idLong
        jda.eventCache.playbackCache(EventCache.Type.USER, member.idLong)
        jda.eventCache.playbackCache(EventCache.Type.MEMBER, hashId)
        return true
    }
}