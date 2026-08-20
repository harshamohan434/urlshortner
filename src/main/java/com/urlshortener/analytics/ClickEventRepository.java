package com.urlshortener.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    List<ClickEvent> findTop5ByCodeOrderByOccurredAtDesc(String code);

    @Query("select c.deviceType as deviceType, count(c) as total from ClickEvent c "
            + "where c.code = :code group by c.deviceType")
    List<DeviceCount> countByDeviceType(@Param("code") String code);

    /**
     * Raw timestamps only (not full entities) for a code since a cutoff, so day-bucketing can
     * happen in Java rather than via a dialect-specific date-trunc query — this project runs
     * against both H2 (dev) and Postgres (prod) with no existing precedent for a portable
     * native date-truncation query, so keeping bucketing logic out of SQL avoids that risk.
     */
    @Query("select c.occurredAt from ClickEvent c where c.code = :code and c.occurredAt >= :since")
    List<Instant> findOccurredAtByCodeSince(@Param("code") String code, @Param("since") Instant since);

    interface DeviceCount {
        String getDeviceType();

        long getTotal();
    }
}
