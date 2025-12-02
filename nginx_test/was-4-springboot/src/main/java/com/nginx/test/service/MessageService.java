package com.nginx.test.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public String send(String topic, String key, String message) {
        String messageId = UUID.randomUUID().toString();
        String messageKey = key != null ? key : messageId;

        String wrappedMessage = String.format(
                "{\"id\":\"%s\",\"source\":\"was-4-springboot\",\"timestamp\":\"%s\",\"payload\":%s}",
                messageId, Instant.now(), message
        );

        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(topic, messageKey, wrappedMessage);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send message: id={}, topic={}, error={}",
                        messageId, topic, ex.getMessage());
            } else {
                log.info("Message sent: id={}, topic={}, partition={}, offset={}",
                        messageId, topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return messageId;
    }

    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        try {
            var metrics = kafkaTemplate.metrics();
            status.put("status", "UP");
            status.put("metrics_count", metrics.size());
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
        return status;
    }
}
