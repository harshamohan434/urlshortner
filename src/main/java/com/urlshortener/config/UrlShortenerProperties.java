package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code urlshortener.*} settings from application.yml.
 */
@ConfigurationProperties(prefix = "urlshortener")
public record UrlShortenerProperties(String baseUrl, int codeLength, RateLimit rateLimit) {

    public record RateLimit(int capacity, int refillPerMinute) {
    }
}
