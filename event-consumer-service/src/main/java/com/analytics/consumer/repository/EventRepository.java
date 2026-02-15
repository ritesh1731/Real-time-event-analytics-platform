package com.analytics.consumer.repository;

import com.analytics.consumer.model.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    // Used for idempotency check - prevents duplicate processing
    boolean existsByEventId(String eventId);

    Optional<EventEntity> findByEventId(String eventId);

    @Query("SELECT COUNT(e) FROM EventEntity e WHERE e.eventType = :eventType")
    long countByEventType(String eventType);
}
