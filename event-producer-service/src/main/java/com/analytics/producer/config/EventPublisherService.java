package com.analytics.producer.config;

import com.analytics.producer.model.AnalyticsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisherService {

    private final KafkaTemplate<String, AnalyticsEvent> kafkaTemplate;

    private static final String USER_EVENTS_TOPIC   = "user-events";
    private static final String SYSTEM_EVENTS_TOPIC = "system-events";

    /**
     * Publishes event to the appropriate Kafka topic.
     * Uses eventId as key → ensures same-user events go to same partition (ordering).
     */
    public CompletableFuture<SendResult<String, AnalyticsEvent>> publishEvent(AnalyticsEvent event) {
        String topic = resolveTopicForEvent(event.getEventType());
        // Partition key: use userId for user events (keeps user's events ordered)
        String partitionKey = event.getUserId() != null ? event.getUserId() : event.getEventId();

        log.info("Publishing event: type={} id={} topic={} key={}",
                event.getEventType(), event.getEventId(), topic, partitionKey);

        CompletableFuture<SendResult<String, AnalyticsEvent>> future =
                kafkaTemplate.send(topic, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event {}: {}", event.getEventId(), ex.getMessage());
            } else {
                log.debug("Event {} published → partition={} offset={}",
                        event.getEventId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }

    private String resolveTopicForEvent(String eventType) {
        if (eventType == null) return USER_EVENTS_TOPIC;
        return switch (eventType.toUpperCase()) {
            case "ERROR", "SYSTEM_HEALTH", "LATENCY", "SERVER_START" -> SYSTEM_EVENTS_TOPIC;
            default -> USER_EVENTS_TOPIC;
        };
    }
}
