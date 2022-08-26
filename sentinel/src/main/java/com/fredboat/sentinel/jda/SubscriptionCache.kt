package com.fredboat.sentinel.jda

import org.springframework.stereotype.Service

@Service
class SubscriptionCache {

    private val subscriptionsCache = LinkedHashSet<Long>()

    fun add(guildId: Long) = subscriptionsCache.add(guildId)
    fun remove(guildId: Long) = subscriptionsCache.remove(guildId)
    fun contains(guildId: Long): Boolean = subscriptionsCache.contains(guildId)
}