package com.nginx.test.service

import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
@Observed(name = "cache.service")
class CacheService(
    private val redisTemplate: StringRedisTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun get(key: String): String? {
        return try {
            val value = redisTemplate.opsForValue().get(key)
            logger.debug("Cache GET: key=$key, found=${value != null}")
            value
        } catch (e: Exception) {
            logger.error("Cache GET error: key=$key, error=${e.message}")
            null
        }
    }

    fun set(key: String, value: String, ttlSeconds: Long = 3600) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds))
            logger.debug("Cache SET: key=$key, ttl=$ttlSeconds")
        } catch (e: Exception) {
            logger.error("Cache SET error: key=$key, error=${e.message}")
            throw e
        }
    }

    fun delete(key: String): Boolean {
        return try {
            val deleted = redisTemplate.delete(key)
            logger.debug("Cache DELETE: key=$key, deleted=$deleted")
            deleted
        } catch (e: Exception) {
            logger.error("Cache DELETE error: key=$key, error=${e.message}")
            false
        }
    }

    fun healthCheck(): Map<String, Any> {
        return try {
            val pong = redisTemplate.connectionFactory?.connection?.ping()
            mapOf(
                "status" to if (pong == "PONG") "UP" else "DOWN",
                "response" to (pong ?: "NO_RESPONSE")
            )
        } catch (e: Exception) {
            mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Unknown error")
            )
        }
    }
}
