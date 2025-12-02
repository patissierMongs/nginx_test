package com.nginx.test.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;

    public String get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            log.debug("Cache GET: key={}, found={}", key, value != null);
            return value;
        } catch (Exception e) {
            log.error("Cache GET error: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    public void set(String key, String value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
            log.debug("Cache SET: key={}, ttl={}", key, ttlSeconds);
        } catch (Exception e) {
            log.error("Cache SET error: key={}, error={}", key, e.getMessage());
            throw e;
        }
    }

    public boolean delete(String key) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("Cache DELETE: key={}, deleted={}", key, deleted);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.error("Cache DELETE error: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            status.put("status", "PONG".equals(pong) ? "UP" : "DOWN");
            status.put("response", pong != null ? pong : "NO_RESPONSE");
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
        return status;
    }
}
