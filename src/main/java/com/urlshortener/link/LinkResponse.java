package com.urlshortener.link;

import java.time.Instant;

public record LinkResponse(String code, String shortUrl, String longUrl, Instant expiresAt, Instant createdAt) {
}
