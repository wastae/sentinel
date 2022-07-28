package com.fredboat.sentinel.redis

import com.fredboat.sentinel.entities.RedisMember
import com.redis.om.spring.annotations.Document
import com.redis.om.spring.annotations.Indexed
import org.springframework.data.annotation.Id

@Document
data class CachedGuild(
    @Id
    @Indexed
    val id: String,

    @Indexed
    val memberList: MutableList<RedisMember>
)