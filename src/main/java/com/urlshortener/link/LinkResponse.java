package com.urlshortener.link;

import java.time.Instant;

/**
 * managementToken is only ever populated here — this record is returned exclusively from the
 * create endpoint (no GET-link-metadata endpoint exists), so the token is naturally shown once
 * and never again, without needing to special-case it elsewhere.
 */
public record LinkResponse(String code, String shortUrl, String longUrl, Instant expiresAt, Instant createdAt,
                            String managementToken) {
}
