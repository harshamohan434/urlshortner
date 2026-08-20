package com.urlshortener.ratelimit;

import com.urlshortener.config.UrlShortenerProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final UrlShortenerProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    // Single constructor, Clock injected via ClockConfig — real clock in production, a
    // fixed/advanceable one in tests, without an ambiguous second constructor for Spring to
    // guess between (that ambiguity previously broke the entire app context at startup).
    public RateLimiterService(UrlShortenerProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean tryConsume(String clientId) {
        return bucketFor(clientId).tryConsume();
    }

    public long secondsUntilNextToken(String clientId) {
        return bucketFor(clientId).secondsUntilNextToken();
    }

    /**
     * Clears all per-client bucket state. Primarily test-support, since Spring caches the
     * application context (and this singleton bean) across test methods within a class —
     * without a reset hook, one test's requests would silently exhaust the bucket for the
     * next. Also a reasonable operational escape hatch if ever needed.
     */
    public void reset() {
        buckets.clear();
    }

    private TokenBucket bucketFor(String clientId) {
        return buckets.computeIfAbsent(clientId, id -> new TokenBucket(
                properties.rateLimit().capacity(), properties.rateLimit().refillPerMinute(), clock));
    }
}
