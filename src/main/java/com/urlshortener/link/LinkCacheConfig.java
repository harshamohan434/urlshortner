package com.urlshortener.link;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * A plain Caffeine {@link Cache} bean (not Spring's {@code @Cacheable} abstraction) so
 * {@link LinkService} has full control over miss behavior: negative lookups (unknown codes)
 * are never cached — see the redirect hot-path rationale in LinkService.resolve — and every
 * cache hit is still checked against the current time for expiry, since the cached entity's
 * expiresAt doesn't change after caching but "now" does.
 */
@Configuration
public class LinkCacheConfig {

    @Bean
    public Cache<String, ShortLink> shortLinkCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }
}
