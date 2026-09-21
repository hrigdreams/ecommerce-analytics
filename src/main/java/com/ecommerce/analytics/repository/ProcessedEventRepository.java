package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * @return 1 if the event was newly recorded, 0 if it had already been processed.
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, processed_at)
            VALUES (:eventId, now())
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int markProcessed(@Param("eventId") String eventId);
}
