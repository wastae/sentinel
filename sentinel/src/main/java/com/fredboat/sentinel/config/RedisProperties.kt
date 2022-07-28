package com.fredboat.sentinel.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "redis")
class RedisProperties(
    var clientName: String = "",
    var username: String = "",
    var password: String = "",
    var hostname: String = "",
    var port: Int = 6379,
    var connectTimeout: Long = 10000,
    var readTimeout: Long = 10000
)