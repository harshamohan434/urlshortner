package com.urlshortener.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Atomic increment guarded by the expiry check, so a click against an already-expired
     * link never increments the count even under concurrent access — no read-modify-write
     * race between checking expiry and updating the counter.
     */
    @Modifying
    @Query("update ShortLink s set s.clickCount = s.clickCount + 1, s.lastAccessedAt = :now "
            + "where s.code = :code and (s.expiresAt is null or s.expiresAt > :now)")
    int recordClickIfActive(@Param("code") String code, @Param("now") Instant now);
}
