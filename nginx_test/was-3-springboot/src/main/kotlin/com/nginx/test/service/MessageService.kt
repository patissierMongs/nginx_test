package com.nginx.test.service

import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Service
@Observed(name = "message.service")
class MessageService(
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun send(topic: String, key: String?, message: String): String {
        val messageId = UUID.randomUUID().toString()
        val messageKey = key ?: messageId

        val wrappedMessage = """
            {
                "id": "$messageId",
                "source": "was-3-springboot",
                "timestamp": "${java.time.Instant.now()}",
                "payload": $message
            }
        """.trimIndent()

        val future: CompletableFuture<SendResult<String, String>> =
            kafkaTemplate.send(topic, messageKey, wrappedMessage)

        future.whenComplete { result, ex ->
            if (ex != null) {
                logger.error("Failed to send message: id=$messageId, topic=$topic, error=${ex.message}")
            } else {
                logger.info(
                    "Message sent: id=$messageId, topic=$topic, " +
                    "partition=${result.recordMetadata.partition()}, " +
                    "offset=${result.recordMetadata.offset()}"
                )
            }
        }

        return messageId
    }

    fun healthCheck(): Map<String, Any> {
        return try {
            // Simple health check by getting cluster info
            val metrics = kafkaTemplate.metrics()
            mapOf(
                "status" to "UP",
                "metrics_count" to metrics.size
            )
        } catch (e: Exception) {
            mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Unknown error")
            )
        }
    }
}
