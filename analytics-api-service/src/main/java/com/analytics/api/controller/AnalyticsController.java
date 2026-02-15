package com.analytics.api.controller;

import com.analytics.api.model.EventDocument;
import com.analytics.api.model.EventEntity;
import com.analytics.api.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    // ── Dashboard ─────────────────────────────────────────────────────────

    /**
     * GET /api/v1/analytics/dashboard
     * Returns total counts, by-type breakdown, by-region breakdown, real-time rates.
     * Served from Redis cache - sub-10ms response time.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(analyticsService.getDashboardSummary());
    }

    /**
     * GET /api/v1/analytics/rate
     * Returns event ingestion rate for last 1/5/15/60 minutes.
     */
    @GetMapping("/rate")
    public ResponseEntity<Map<String, Long>> getEventRate() {
        return ResponseEntity.ok(analyticsService.getRealTimeEventRate());
    }

    // ── Database Queries ──────────────────────────────────────────────────

    @GetMapping("/events/by-type/{eventType}")
    public ResponseEntity<Page<EventEntity>> getByType(
            @PathVariable String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(analyticsService.getEventsByType(eventType, page, size));
    }

    @GetMapping("/events/by-user/{userId}")
    public ResponseEntity<Page<EventEntity>> getByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(analyticsService.getEventsByUser(userId, page, size));
    }

    @GetMapping("/events/date-range")
    public ResponseEntity<Page<EventEntity>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(analyticsService.getEventsByDateRange(from, to, page, size));
    }

    @GetMapping("/distribution/event-types")
    public ResponseEntity<List<Map<String, Object>>> getEventTypeDistribution() {
        return ResponseEntity.ok(analyticsService.getEventTypeDistribution());
    }

    @GetMapping("/distribution/regions")
    public ResponseEntity<List<Map<String, Object>>> getRegionDistribution() {
        return ResponseEntity.ok(analyticsService.getRegionDistribution());
    }

    // ── Elasticsearch Search ──────────────────────────────────────────────

    @GetMapping("/search/by-type/{eventType}")
    public ResponseEntity<Page<EventDocument>> searchByType(
            @PathVariable String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(analyticsService.searchByEventType(eventType, page, size));
    }

    @GetMapping("/search/by-user/{userId}")
    public ResponseEntity<Page<EventDocument>> searchByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(analyticsService.searchByUser(userId, page, size));
    }

    @GetMapping("/search/by-region/{region}")
    public ResponseEntity<Page<EventDocument>> searchByRegion(
            @PathVariable String region,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(analyticsService.searchByRegion(region, page, size));
    }
}
