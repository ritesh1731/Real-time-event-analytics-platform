package com.analytics.consumer.service;

import com.analytics.consumer.model.DeadLetterEntity;
import com.analytics.consumer.model.EventDocument;
import com.analytics.consumer.model.EventEntity;
import com.analytics.consumer.repository.DeadLetterRepository;
import com.analytics.consumer.repository.EventRepository;
import com.analytics.consumer.repository.EventSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProcessingService {

    private final EventRepository         eventRepository;
    private final DeadLetterRepository    deadLetterRepository;
    private final EventSearchRepository   eventSearchRepository;
    private final StringRedisTemplate     redisTemplate;
    private final ObjectMapper            objectMapper;

    private static final String PROCESSED_KEY_PREFIX  = "processed:";
    private static final String COUNT_KEY_PREFIX       = "count:event:";
    private static final String REGION_COUNT_PREFIX    = "count:region:";
    private static final Duration IDEMPOTENCY_TTL      = Duration.ofHours(24);

    /**
     * Main processing method:
     * 1. Idempotency check (Redis + DB)  → skip duplicates
     * 2. Persist to PostgreSQL           → durable storage
     * 3. Index in Elasticsearch          → searchable analytics
     * 4. Update Redis counters           → fast aggregations
     */
    @Transactional
    public void processEvent(Map<String, Object> eventMap) {
        String eventId   = (String) eventMap.get("eventId");
        String eventType = (String) eventMap.get("eventType");

        // ── Step 1: Idempotency Check ─────────────────────────────────────
        if (isDuplicate(eventId)) {
            log.warn("Duplicate event skipped: {}", eventId);
            return;
        }

        // ── Step 2: Persist to PostgreSQL ─────────────────────────────────
        persistToDatabase(eventMap);

        // ── Step 3: Index to Elasticsearch ───────────────────────────────
        indexToElasticsearch(eventMap);

        // ── Step 4: Update Redis counters ─────────────────────────────────
        updateRedisCounters(eventType, (String) eventMap.get("region"));

        // ── Step 5: Mark as processed (idempotency) ───────────────────────
        markAsProcessed(eventId);

        log.info("Event processed successfully: id={} type={}", eventId, eventType);
    }

    /**
     * Sends unprocessable events to the Dead Letter Queue table.
     * This ensures no data loss even when processing fails.
     */
    public void sendToDeadLetterQueue(String rawPayload, String errorMessage) {
        try {
            DeadLetterEntity dlq = DeadLetterEntity.builder()
                    .rawPayload(rawPayload)
                    .errorMessage(errorMessage)
                    .build();
            deadLetterRepository.save(dlq);
            log.warn("Event sent to DLQ: error={}", errorMessage);
        } catch (Exception e) {
            log.error("Failed to save to DLQ: {}", e.getMessage());
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────

    private boolean isDuplicate(String eventId) {
        if (eventId == null) return false;
        // Fast check in Redis first (sub-millisecond)
        Boolean existsInRedis = redisTemplate.hasKey(PROCESSED_KEY_PREFIX + eventId);
        if (Boolean.TRUE.equals(existsInRedis)) return true;
        // Fallback to DB check (in case Redis was flushed)
        return eventRepository.existsByEventId(eventId);
    }

    @CircuitBreaker(name = "postgres", fallbackMethod = "persistFallback")
    private void persistToDatabase(Map<String, Object> eventMap) {
        EventEntity entity = EventEntity.builder()
                .eventId((String) eventMap.get("eventId"))
                .eventType((String) eventMap.get("eventType"))
                .userId((String) eventMap.get("userId"))
                .sessionId((String) eventMap.get("sessionId"))
                .payload(getNestedMap(eventMap, "payload"))
                .source((String) eventMap.get("source"))
                .region((String) eventMap.get("region"))
                .createdAt(parseInstant(eventMap.get("timestamp")))
                .processedAt(Instant.now())
                .build();
        eventRepository.save(entity);
    }

    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "elasticFallback")
    private void indexToElasticsearch(Map<String, Object> eventMap) {
        EventDocument doc = EventDocument.builder()
                .eventId((String) eventMap.get("eventId"))
                .eventType((String) eventMap.get("eventType"))
                .userId((String) eventMap.get("userId"))
                .sessionId((String) eventMap.get("sessionId"))
                .payload(getNestedMap(eventMap, "payload"))
                .source((String) eventMap.get("source"))
                .region((String) eventMap.get("region"))
                .timestamp(parseInstant(eventMap.get("timestamp")))
                .build();
        eventSearchRepository.save(doc);
    }

    private void updateRedisCounters(String eventType, String region) {
        // Increment event type counter (persists indefinitely)
        redisTemplate.opsForValue().increment(COUNT_KEY_PREFIX + eventType);
        redisTemplate.opsForValue().increment(COUNT_KEY_PREFIX + "TOTAL");

        // Increment region counter
        if (region != null) {
            redisTemplate.opsForValue().increment(REGION_COUNT_PREFIX + region);
        }

        // Per-minute rolling window counter for real-time metrics
        long minuteBucket = System.currentTimeMillis() / 60_000;
        String minuteKey  = "rate:" + minuteBucket;
        redisTemplate.opsForValue().increment(minuteKey);
        redisTemplate.expire(minuteKey, Duration.ofMinutes(10)); // auto-expire old buckets
    }

    private void markAsProcessed(String eventId) {
        if (eventId != null) {
            redisTemplate.opsForValue()
                    .set(PROCESSED_KEY_PREFIX + eventId, "1", IDEMPOTENCY_TTL);
        }
    }

    // Fallback methods for Circuit Breaker
    private void persistFallback(Map<String, Object> eventMap, Throwable t) {
        log.error("PostgreSQL circuit open - skipping DB persist for event {}: {}",
                eventMap.get("eventId"), t.getMessage());
    }

    private void elasticFallback(Map<String, Object> eventMap, Throwable t) {
        log.error("Elasticsearch circuit open - skipping ES index for event {}: {}",
                eventMap.get("eventId"), t.getMessage());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedMap(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return null;
    }

    private Instant parseInstant(Object val) {
        if (val == null) return Instant.now();
        try {
            return Instant.parse(val.toString());
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
