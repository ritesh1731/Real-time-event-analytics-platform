package com.analytics.api.repository;

import com.analytics.api.model.EventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    Page<EventEntity> findByEventType(String eventType, Pageable pageable);

    Page<EventEntity> findByUserId(String userId, Pageable pageable);

    Page<EventEntity> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

    @Query("SELECT e.eventType, COUNT(e) FROM EventEntity e GROUP BY e.eventType ORDER BY COUNT(e) DESC")
    List<Object[]> countGroupByEventType();

    @Query("SELECT e.region, COUNT(e) FROM EventEntity e WHERE e.region IS NOT NULL GROUP BY e.region ORDER BY COUNT(e) DESC")
    List<Object[]> countGroupByRegion();

    @Query("SELECT e.source, COUNT(e) FROM EventEntity e WHERE e.source IS NOT NULL GROUP BY e.source ORDER BY COUNT(e) DESC")
    List<Object[]> countGroupBySource();

    long countByEventType(String eventType);
}
