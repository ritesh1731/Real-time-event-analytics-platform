package com.analytics.consumer.consumer;

import com.analytics.consumer.service.EventProcessingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final EventProcessingService processingService;
    private final ObjectMapper           objectMapper;

    /**
     * Consumes user events from Kafka.
     * Consumer group "analytics-group" → all instances share load across partitions.
     * Adding more consumer instances scales throughput proportionally (up to partition count).
     * Manual ACK (AckMode.MANUAL) → offset committed only after successful processing.
     */
    @KafkaListener(
            topics          = {"user-events", "system-events"},
            groupId         = "analytics-group",
            containerFactory= "kafkaListenerContainerFactory"
    )
    public void consumeEvent(
            ConsumerRecord<String, String> record,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int    partition,
            @Header(KafkaHeaders.OFFSET)             long   offset) {

        log.debug("Received event: topic={} partition={} offset={} key={}",
                topic, partition, offset, record.key());

        try {
            Map<String, Object> eventMap = objectMapper.readValue(
                    record.value(), new TypeReference<>() {});

            processingService.processEvent(eventMap);

            // ✅ Commit offset only after successful processing (manual ACK)
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing event at offset {}: {}", offset, e.getMessage());
            // Send to Dead Letter Queue instead of crashing consumer
            processingService.sendToDeadLetterQueue(record.value(), e.getMessage());
            // Still acknowledge to avoid infinite retry loop on poison messages
            ack.acknowledge();
        }
    }

    /**
     * Dedicated DLQ consumer - for monitoring/reprocessing failed events.
     */
    @KafkaListener(
            topics  = "dead-letter-events",
            groupId = "dlq-monitor-group"
    )
    public void consumeDeadLetterEvent(
            ConsumerRecord<String, String> record,
            Acknowledgment ack) {
        log.warn("DLQ event received: key={} value={}", record.key(), record.value());
        processingService.sendToDeadLetterQueue(record.value(), "Received from Kafka DLQ topic");
        ack.acknowledge();
    }
}
