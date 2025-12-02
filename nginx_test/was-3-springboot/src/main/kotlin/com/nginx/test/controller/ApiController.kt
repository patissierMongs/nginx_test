package com.nginx.test.controller

import com.nginx.test.service.CacheService
import com.nginx.test.service.MessageService
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.InetAddress
import java.time.Instant

@RestController
@RequestMapping("/api")
@Observed(name = "api.controller")
class ApiController(
    private val cacheService: CacheService,
    private val messageService: MessageService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/info")
    fun getInfo(): ResponseEntity<Map<String, Any>> {
        logger.info("Received info request")

        val info = mapOf(
            "service" to "was-3-springboot",
            "type" to "kubernetes-docker-runtime",
            "framework" to "Spring Boot 3.2 + Kotlin",
            "timestamp" to Instant.now().toString(),
            "hostname" to try { InetAddress.getLocalHost().hostName } catch (e: Exception) { "unknown" },
            "ip" to try { InetAddress.getLocalHost().hostAddress } catch (e: Exception) { "unknown" },
            "javaVersion" to System.getProperty("java.version"),
            "environment" to mapOf(
                "POD_NAME" to (System.getenv("POD_NAME") ?: "local"),
                "POD_NAMESPACE" to (System.getenv("POD_NAMESPACE") ?: "default"),
                "NODE_NAME" to (System.getenv("NODE_NAME") ?: "local")
            )
        )

        return ResponseEntity.ok(info)
    }

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        val health = mapOf(
            "status" to "UP",
            "service" to "was-3-springboot",
            "timestamp" to Instant.now().toString(),
            "checks" to mapOf(
                "redis" to cacheService.healthCheck(),
                "kafka" to messageService.healthCheck()
            )
        )
        return ResponseEntity.ok(health)
    }

    @GetMapping("/cache/{key}")
    fun getCache(@PathVariable key: String): ResponseEntity<Map<String, Any?>> {
        logger.info("Cache GET request for key: $key")

        val value = cacheService.get(key)
        val result = mapOf(
            "operation" to "GET",
            "key" to key,
            "value" to value,
            "found" to (value != null),
            "source" to "redis-cluster"
        )

        return ResponseEntity.ok(result)
    }

    @PutMapping("/cache/{key}")
    fun setCache(
        @PathVariable key: String,
        @RequestBody body: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        logger.info("Cache SET request for key: $key")

        val value = body["value"]?.toString() ?: ""
        val ttl = (body["ttl"] as? Number)?.toLong() ?: 3600L

        cacheService.set(key, value, ttl)

        val result = mapOf(
            "operation" to "SET",
            "key" to key,
            "value" to value,
            "ttl" to ttl,
            "success" to true,
            "destination" to "redis-cluster"
        )

        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/cache/{key}")
    fun deleteCache(@PathVariable key: String): ResponseEntity<Map<String, Any>> {
        logger.info("Cache DELETE request for key: $key")

        val deleted = cacheService.delete(key)

        val result = mapOf(
            "operation" to "DELETE",
            "key" to key,
            "deleted" to deleted,
            "destination" to "redis-cluster"
        )

        return ResponseEntity.ok(result)
    }

    @PostMapping("/message")
    fun sendMessage(@RequestBody body: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        val topic = body["topic"]?.toString() ?: "nginx-test-events"
        val message = body["message"]?.toString() ?: ""
        val key = body["key"]?.toString()

        logger.info("Sending message to Kafka topic: $topic")

        val messageId = messageService.send(topic, key, message)

        val result = mapOf(
            "operation" to "PUBLISH",
            "topic" to topic,
            "key" to (key ?: "null"),
            "messageId" to messageId,
            "success" to true,
            "broker" to "kafka-cluster"
        )

        return ResponseEntity.ok(result)
    }

    @GetMapping("/slow")
    fun slowEndpoint(@RequestParam(defaultValue = "1000") delay: Long): ResponseEntity<Map<String, Any>> {
        logger.info("Slow endpoint called with delay: ${delay}ms")

        Thread.sleep(delay)

        val result = mapOf(
            "service" to "was-3-springboot",
            "endpoint" to "/api/slow",
            "delay_ms" to delay,
            "message" to "This endpoint simulates slow responses for testing timeouts"
        )

        return ResponseEntity.ok(result)
    }

    @GetMapping("/error")
    fun errorEndpoint(@RequestParam(defaultValue = "500") code: Int): ResponseEntity<Map<String, Any>> {
        logger.error("Error endpoint called with code: $code")

        val result = mapOf(
            "service" to "was-3-springboot",
            "endpoint" to "/api/error",
            "error_code" to code,
            "message" to "This endpoint simulates errors for testing error handling"
        )

        return ResponseEntity.status(code).body(result)
    }
}
