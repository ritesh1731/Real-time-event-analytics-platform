package com.analytics.consumer.repository;

import com.analytics.consumer.model.EventDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventSearchRepository extends ElasticsearchRepository<EventDocument, String> {

    List<EventDocument> findByEventType(String eventType);

    List<EventDocument> findByUserId(String userId);

    List<EventDocument> findByRegion(String region);

    long countByEventType(String eventType);
}
