package com.nginx.test.controller;

import com.nginx.test.service.CacheService;
import com.nginx.test.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final CacheService cacheService;
    private final MessageService messageService;

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        log.info("Received info request");

        Map<String, Object> info = new HashMap<>();
        info.put("service", "was-4-springboot");
        info.put("type", "kubernetes-docker-runtime");
        info.put("framework", "Spring Boot 3.2 + Java 21");
        info.put("timestamp", Instant.now().toString());

        try {
            info.put("hostname", InetAddress.getLocalHost().getHostName());
            info.put("ip", InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            info.put("hostname", "unknown");
            info.put("ip", "unknown");
        }

        info.put("javaVersion", System.getProperty("java.version"));

        Map<String, String> environment = new HashMap<>();
        environment.put("POD_NAME", System.getenv().getOrDefault("POD_NAME", "local"));
        environment.put("POD_NAMESPACE", System.getenv().getOrDefault("POD_NAMESPACE", "default"));
        environment.put("NODE_NAME", System.getenv().getOrDefault("NODE_NAME", "local"));
        info.put("environment", environment);

        return ResponseEntity.ok(info);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "was-4-springboot");
        health.put("timestamp", Instant.now().toString());

        Map<String, Object> checks = new HashMap<>();
        checks.put("redis", cacheService.healthCheck());
        checks.put("kafka", messageService.healthCheck());
        health.put("checks", checks);

        return ResponseEntity.ok(health);
    }

    @GetMapping("/cache/{key}")
    public ResponseEntity<Map<String, Object>> getCache(@PathVariable String key) {
        log.info("Cache GET request for key: {}", key);

        String value = cacheService.get(key);

        Map<String, Object> result = new HashMap<>();
        result.put("operation", "GET");
        result.put("key", key);
        result.put("value", value);
        result.put("found", value != null);
        result.put("source", "redis-cluster");

        return ResponseEntity.ok(result);
    }

    @PutMapping("/cache/{key}")
    public ResponseEntity<Map<String, Object>> setCache(
            @PathVariable String key,
            @RequestBody Map<String, Object> body) {
        log.info("Cache SET request for key: {}", key);

        String value = body.getOrDefault("value", "").toString();
        long ttl = body.containsKey("ttl") ? ((Number) body.get("ttl")).longValue() : 3600L;

        cacheService.set(key, value, ttl);

        Map<String, Object> result = new HashMap<>();
        result.put("operation", "SET");
        result.put("key", key);
        result.put("value", value);
        result.put("ttl", ttl);
        result.put("success", true);
        result.put("destination", "redis-cluster");

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/cache/{key}")
    public ResponseEntity<Map<String, Object>> deleteCache(@PathVariable String key) {
        log.info("Cache DELETE request for key: {}", key);

        boolean deleted = cacheService.delete(key);

        Map<String, Object> result = new HashMap<>();
        result.put("operation", "DELETE");
        result.put("key", key);
        result.put("deleted", deleted);
        result.put("destination", "redis-cluster");

        return ResponseEntity.ok(result);
    }

    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> body) {
        String topic = body.getOrDefault("topic", "nginx-test-events").toString();
        String message = body.getOrDefault("message", "").toString();
        String key = body.containsKey("key") ? body.get("key").toString() : null;

        log.info("Sending message to Kafka topic: {}", topic);

        String messageId = messageService.send(topic, key, message);

        Map<String, Object> result = new HashMap<>();
        result.put("operation", "PUBLISH");
        result.put("topic", topic);
        result.put("key", key != null ? key : "null");
        result.put("messageId", messageId);
        result.put("success", true);
        result.put("broker", "kafka-cluster");

        return ResponseEntity.ok(result);
    }

    @GetMapping("/slow")
    public ResponseEntity<Map<String, Object>> slowEndpoint(
            @RequestParam(defaultValue = "1000") long delay) throws InterruptedException {
        log.info("Slow endpoint called with delay: {}ms", delay);

        Thread.sleep(delay);

        Map<String, Object> result = new HashMap<>();
        result.put("service", "was-4-springboot");
        result.put("endpoint", "/api/slow");
        result.put("delay_ms", delay);
        result.put("message", "This endpoint simulates slow responses for testing timeouts");

        return ResponseEntity.ok(result);
    }

    @GetMapping("/error")
    public ResponseEntity<Map<String, Object>> errorEndpoint(
            @RequestParam(defaultValue = "500") int code) {
        log.error("Error endpoint called with code: {}", code);

        Map<String, Object> result = new HashMap<>();
        result.put("service", "was-4-springboot");
        result.put("endpoint", "/api/error");
        result.put("error_code", code);
        result.put("message", "This endpoint simulates errors for testing error handling");

        return ResponseEntity.status(code).body(result);
    }
}
