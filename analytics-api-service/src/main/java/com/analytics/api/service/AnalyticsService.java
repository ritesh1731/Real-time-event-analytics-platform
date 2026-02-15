package com.analytics.api.service;

import com.analytics.api.model.EventDocument;
import com.analytics.api.model.EventEntity;
import com.analytics.api.repository.EventRepository;
import com.analytics.api.repository.EventSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final EventRepository       eventRepository;
    private final EventSearchRepository eventSearchRepository;
    private final StringRedisTemplate   redisTemplate;

    private static final String COUNT_KEY_PREFIX   = "count:event:";
    private static final String REGION_COUNT_PREFIX = "count:region:";

    // ── Dashboard Summary (Redis-first for sub-10ms response) ─────────────

    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        // Total events - read from Redis (O(1) sub-millisecond)
        String totalStr = redisTemplate.opsForValue().get(COUNT_KEY_PREFIX + "TOTAL");
        long total      = totalStr != null ? Long.parseLong(totalStr) : eventRepository.count();
        summary.put("totalEvents", total);

        // Per-type counts from Redis
        Map<String, Long> byType = new LinkedHashMap<>();
        for (String type : List.of("PAGE_VIEW", "PURCHASE", "LOGIN", "LOGOUT",
                                   "ADD_TO_CART", "SEARCH", "ERROR", "CLICK")) {
            String val = redisTemplate.opsForValue().get(COUNT_KEY_PREFIX + type);
            if (val != null) byType.put(type, Long.parseLong(val));
        }
        summary.put("byEventType", byType);

        // Region counts from Redis
        Map<String, Long> byRegion = new LinkedHashMap<>();
        for (String region : List.of("IN", "US", "EU", "APAC")) {
            String val = redisTemplate.opsForValue().get(REGION_COUNT_PREFIX + region);
            if (val != null) byRegion.put(region, Long.parseLong(val));
        }
        summary.put("byRegion", byRegion);

        // Real-time rate: events in last 5 minutes
        summary.put("eventsLast5Min", getEventsLastNMinutes(5));

        summary.put("timestamp", Instant.now().toString());
        return summary;
    }

    // ── DB Queries ─────────────────────────────────────────────────────────

    public Page<EventEntity> getEventsByType(String eventType, int page, int size) {
        return eventRepository.findByEventType(
                eventType,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    public Page<EventEntity> getEventsByUser(String userId, int page, int size) {
        return eventRepository.findByUserId(
                userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    public Page<EventEntity> getEventsByDateRange(Instant from, Instant to, int page, int size) {
        return eventRepository.findByCreatedAtBetween(
                from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    public List<Map<String, Object>> getEventTypeDistribution() {
        List<Object[]> rows = eventRepository.countGroupByEventType();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("eventType", r[0].toString());
            entry.put("count", ((Number) r[1]).longValue());
            result.add(entry);
        }
        return result;
    }

    public List<Map<String, Object>> getRegionDistribution() {
        List<Object[]> rows = eventRepository.countGroupByRegion();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("region", r[0].toString());
            entry.put("count", ((Number) r[1]).longValue());
            result.add(entry);
        }
        return result;
    }

    // ── Elasticsearch Search ───────────────────────────────────────────────

    public Page<EventDocument> searchByEventType(String eventType, int page, int size) {
        return eventSearchRepository.findByEventType(
                eventType, PageRequest.of(page, size));
    }

    public Page<EventDocument> searchByUser(String userId, int page, int size) {
        return eventSearchRepository.findByUserId(
                userId, PageRequest.of(page, size));
    }

    public Page<EventDocument> searchByRegion(String region, int page, int size) {
        return eventSearchRepository.findByRegion(
                region, PageRequest.of(page, size));
    }

    // ── Redis Rate Window ──────────────────────────────────────────────────

    public long getEventsLastNMinutes(int n) {
        long now         = System.currentTimeMillis() / 60_000;
        long total       = 0;
        for (long i = now - n; i <= now; i++) {
            String val = redisTemplate.opsForValue().get("rate:" + i);
            if (val != null) total += Long.parseLong(val);
        }
        return total;
    }

    public Map<String, Long> getRealTimeEventRate() {
        Map<String, Long> rates = new LinkedHashMap<>();
        rates.put("last1Min",  getEventsLastNMinutes(1));
        rates.put("last5Min",  getEventsLastNMinutes(5));
        rates.put("last15Min", getEventsLastNMinutes(15));
        rates.put("last60Min", getEventsLastNMinutes(60));
        return rates;
    }
}
