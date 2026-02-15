package com.analytics.api.repository;

import com.analytics.api.model.EventDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventSearchRepository extends ElasticsearchRepository<EventDocument, String> {

    Page<EventDocument> findByEventType(String eventType, Pageable pageable);

    Page<EventDocument> findByUserId(String userId, Pageable pageable);

    Page<EventDocument> findByRegion(String region, Pageable pageable);

    long countByEventType(String eventType);

    long countByRegion(String region);
}
