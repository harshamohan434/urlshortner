package com.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable Clock bean, so anything needing "now" (e.g. RateLimiterService) can be
 * constructor-injected with a real clock in production and a fixed/advanceable one in tests,
 * rather than calling Instant.now()/Clock.systemUTC() directly and being untestable.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
