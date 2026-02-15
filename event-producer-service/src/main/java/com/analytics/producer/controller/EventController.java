package com.analytics.producer.controller;

import com.analytics.producer.config.EventPublisherService;
import com.analytics.producer.model.AnalyticsEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventPublisherService publisherService;

    // ── Single Event ─────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Map<String, Object>> publishEvent(
            @Valid @RequestBody AnalyticsEvent event) {
        publisherService.publishEvent(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "status", "accepted",
                        "eventId", event.getEventId(),
                        "message", "Event queued for processing"
                ));
    }

    // ── Batch Events ─────────────────────────────────────────────────────────

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> publishBatch(
            @Valid @RequestBody List<AnalyticsEvent> events) {
        if (events.isEmpty() || events.size() > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Batch size must be between 1 and 100"));
        }
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (AnalyticsEvent event : events) {
            futures.add(publisherService.publishEvent(event));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "status", "accepted",
                        "count", events.size(),
                        "message", "Batch queued for processing"
                ));
    }

    // ── Simulate load for testing ─────────────────────────────────────────

    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulateEvents(
            @RequestParam(defaultValue = "50") int count) {
        if (count > 500) count = 500;

        String[] eventTypes = {"PAGE_VIEW", "PURCHASE", "LOGIN", "LOGOUT",
                               "ADD_TO_CART", "SEARCH", "ERROR", "CLICK"};
        String[] users      = {"user_101", "user_202", "user_303", "user_404", "user_505"};
        String[] sources    = {"web", "mobile-android", "mobile-ios"};
        String[] regions    = {"IN", "US", "EU", "APAC"};
        Random   rng        = new Random();

        List<String> publishedIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String type = eventTypes[rng.nextInt(eventTypes.length)];
            Map<String, Object> payload = buildPayload(type, rng);

            AnalyticsEvent event = AnalyticsEvent.builder()
                    .eventType(type)
                    .userId(users[rng.nextInt(users.length)])
                    .sessionId("session_" + rng.nextInt(1000))
                    .payload(payload)
                    .source(sources[rng.nextInt(sources.length)])
                    .region(regions[rng.nextInt(regions.length)])
                    .build();

            publisherService.publishEvent(event);
            publishedIds.add(event.getEventId());
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "status", "accepted",
                        "simulated", count,
                        "firstEventId", publishedIds.get(0)
                ));
    }

    private Map<String, Object> buildPayload(String eventType, Random rng) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp_ms", System.currentTimeMillis());
        switch (eventType) {
            case "PAGE_VIEW" -> {
                payload.put("page", List.of("/home", "/products", "/cart", "/checkout")
                        .get(rng.nextInt(4)));
                payload.put("duration_ms", rng.nextInt(5000) + 500);
            }
            case "PURCHASE" -> {
                payload.put("amount", Math.round((rng.nextDouble() * 5000 + 100) * 100.0) / 100.0);
                payload.put("currency", "INR");
                payload.put("items", rng.nextInt(5) + 1);
            }
            case "ERROR" -> {
                payload.put("errorCode", List.of("500", "404", "503").get(rng.nextInt(3)));
                payload.put("message", "Simulated error");
            }
            default -> payload.put("action", eventType.toLowerCase());
        }
        return payload;
    }
}
