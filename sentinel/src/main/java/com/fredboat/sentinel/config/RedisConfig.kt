package com.fredboat.sentinel.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import java.time.Duration

@Configuration
class RedisConfig(private val redisProperties: RedisProperties) {
    @Bean
    fun jedisConnectionFactory(): JedisConnectionFactory {
        val redisStandaloneConfiguration = RedisStandaloneConfiguration()
        redisStandaloneConfiguration.username = redisProperties.username
        redisStandaloneConfiguration.password = RedisPassword.of(redisProperties.password)
        redisStandaloneConfiguration.hostName = redisProperties.hostname
        redisStandaloneConfiguration.port = redisProperties.port
        val jedisClientConfiguration = JedisClientConfiguration.builder()
            .clientName(redisProperties.clientName)
            .connectTimeout(Duration.ofMillis(redisProperties.connectTimeout))
            .readTimeout(Duration.ofMillis(redisProperties.readTimeout))
            .build()
        return JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration)
    }
}