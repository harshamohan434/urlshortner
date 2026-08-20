package com.urlshortener.ratelimit;

import java.time.Clock;
import java.time.Duration;

/**
 * A simple, single-instance-memory token bucket. Not thread-hardened beyond synchronized
 * access on {@link #tryConsume} — acceptable for the low-throughput create-link endpoint this
 * guards. Explicitly NOT distributed: in a multi-instance deployment each instance has its own
 * bucket per IP, so the effective limit scales with instance count. Documented limitation, not
 * solved in v1 (would need a shared store like Redis).
 */
public class TokenBucket {

    private final int capacity;
    private final double refillTokensPerNano;
    private final Clock clock;

    private double availableTokens;
    private long lastRefillNanos;

    public TokenBucket(int capacity, int refillPerMinute, Clock clock) {
        this.capacity = capacity;
        this.refillTokensPerNano = refillPerMinute / (60.0 * 1_000_000_000.0);
        this.clock = clock;
        this.availableTokens = capacity;
        this.lastRefillNanos = nowNanos();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * Seconds until at least one token will be available, for the Retry-After header.
     */
    public synchronized long secondsUntilNextToken() {
        refill();
        if (availableTokens >= 1.0) {
            return 0;
        }
        double tokensNeeded = 1.0 - availableTokens;
        double nanosNeeded = tokensNeeded / refillTokensPerNano;
        return Math.max(1, Duration.ofNanos((long) nanosNeeded).toSeconds());
    }

    private void refill() {
        long now = nowNanos();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        availableTokens = Math.min(capacity, availableTokens + elapsed * refillTokensPerNano);
        lastRefillNanos = now;
    }

    private long nowNanos() {
        var instant = clock.instant();
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
