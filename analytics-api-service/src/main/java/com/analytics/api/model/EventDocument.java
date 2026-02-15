package com.analytics.api.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.Map;

@Document(indexName = "analytics-events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventDocument {

    @Id
    private String eventId;

    @Field(type = FieldType.Keyword)
    private String eventType;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String region;

    @Field(type = FieldType.Keyword)
    private String source;

    @Field(type = FieldType.Object)
    private Map<String, Object> payload;

    @Field(type = FieldType.Date)
    private Instant timestamp;
}
