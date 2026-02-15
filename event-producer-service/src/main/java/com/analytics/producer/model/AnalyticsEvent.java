package com.analytics.producer.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalyticsEvent {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    @NotBlank(message = "eventType is required")
    private String eventType;         // e.g. PAGE_VIEW, PURCHASE, LOGIN, ERROR

    private String userId;
    private String sessionId;

    @NotNull(message = "payload is required")
    private Map<String, Object> payload;

    private String source;            // web, mobile-android, mobile-ios
    private String region;            // IN, US, EU

    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp = Instant.now();
}
