package com.fredboat.sentinel.redis.repositories

import com.fredboat.sentinel.redis.CachedGuild
import com.redis.om.spring.repository.RedisDocumentRepository

interface GuildsRepository : RedisDocumentRepository<CachedGuild, String>