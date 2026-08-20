package com.urlshortener.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record StatsResponse(
        String code,
        long clickCount,
        Instant lastAccessedAt,
        Instant createdAt,
        Instant expiresAt,
        List<String> recentReferrers,
        Map<String, Long> deviceBreakdown
) {
}
