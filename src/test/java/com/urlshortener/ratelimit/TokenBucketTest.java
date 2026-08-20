package com.urlshortener.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    /** Fixed clock whose instant can be advanced without real sleeps, for deterministic refill tests. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void allowsUpToCapacityRequests() {
        MutableClock clock = new MutableClock();
        TokenBucket bucket = new TokenBucket(3, 60, clock);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillsOverTimeWithoutRealSleeps() {
        MutableClock clock = new MutableClock();
        // capacity 1, refill 60/minute = 1 token/second
        TokenBucket bucket = new TokenBucket(1, 60, clock);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        clock.advance(Duration.ofSeconds(1));

        assertThat(bucket.tryConsume()).isTrue();
    }

    @Test
    void doesNotRefillBeyondCapacity() {
        MutableClock clock = new MutableClock();
        TokenBucket bucket = new TokenBucket(2, 60, clock);
        bucket.tryConsume();
        bucket.tryConsume();

        clock.advance(Duration.ofMinutes(10)); // way more than enough to overfill if uncapped

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void reportsSecondsUntilNextTokenWhenExhausted() {
        MutableClock clock = new MutableClock();
        TokenBucket bucket = new TokenBucket(1, 60, clock); // 1 token/second
        bucket.tryConsume();

        assertThat(bucket.secondsUntilNextToken()).isGreaterThanOrEqualTo(1);
    }
}
