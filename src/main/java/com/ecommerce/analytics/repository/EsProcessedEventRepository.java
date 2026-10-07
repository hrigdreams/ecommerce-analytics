package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.EsProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EsProcessedEventRepository extends JpaRepository<EsProcessedEvent, String> {

    /** @return 1 if newly recorded, 0 if this event was already processed by the ES indexer. */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO es_processed_events (event_id, processed_at)
            VALUES (:eventId, now())
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int markProcessed(@Param("eventId") String eventId);
}
