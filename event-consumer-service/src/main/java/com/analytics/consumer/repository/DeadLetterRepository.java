package com.analytics.consumer.repository;

import com.analytics.consumer.model.DeadLetterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntity, Long> {
}
