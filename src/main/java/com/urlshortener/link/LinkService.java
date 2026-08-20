package com.urlshortener.link;

import com.github.benmanes.caffeine.cache.Cache;
import com.urlshortener.common.exception.AliasConflictException;
import com.urlshortener.common.exception.CodeGenerationConflictException;
import com.urlshortener.common.exception.InvalidUrlException;
import com.urlshortener.common.exception.LinkAccessDeniedException;
import com.urlshortener.common.exception.LinkNotFoundException;
import com.urlshortener.config.UrlShortenerProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class LinkService {

    private static final Set<String> RESERVED_ALIASES = Set.of("api", "health", "actuator", "h2-console");
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final ShortLinkRepository repository;
    private final UrlShortenerProperties properties;
    private final Cache<String, ShortLink> cache;
    private final Clock clock;

    public LinkService(ShortLinkRepository repository, UrlShortenerProperties properties,
                        Cache<String, ShortLink> cache, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.cache = cache;
        this.clock = clock;
    }

    @Transactional
    public LinkResponse createLink(CreateLinkRequest request) {
        validateLongUrl(request.longUrl());
        Instant now = clock.instant();
        String managementToken = UUID.randomUUID().toString();

        ShortLink link;
        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            String alias = request.customAlias();
            if (RESERVED_ALIASES.contains(alias.toLowerCase())) {
                throw new InvalidUrlException("customAlias '" + alias + "' is reserved");
            }
            if (repository.existsByCode(alias)) {
                throw new AliasConflictException(alias);
            }
            link = new ShortLink(request.longUrl(), alias, true, now, request.expiresAt(), managementToken);
            repository.save(link);
        } else {
            // Two-phase insert: persist without a code to obtain the auto-increment id,
            // then derive the Base62 code from that id and update the row.
            link = new ShortLink(request.longUrl(), null, false, now, request.expiresAt(), managementToken);
            repository.save(link);
            String generatedCode = Base62Encoder.encode(link.getId(), properties.codeLength());
            if (repository.existsByCode(generatedCode)) {
                // See CodeGenerationConflictException javadoc — rare, documented edge case.
                throw new CodeGenerationConflictException(generatedCode);
            }
            link.setCode(generatedCode);
            repository.save(link);
        }

        return toResponse(link);
    }

    /**
     * Cache-aside lookup for the redirect hot path. Misses are never cached (so a link created
     * immediately after a 404 is visible on the very next request), and callers must still
     * check {@link ShortLink#isExpired} against the current time even on a cache hit — the
     * cached entity's expiresAt is immutable, but "now" isn't.
     */
    public Optional<ShortLink> resolve(String code) {
        ShortLink cached = cache.getIfPresent(code);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<ShortLink> found = repository.findByCode(code);
        found.ifPresent(link -> cache.put(code, link));
        return found;
    }

    /**
     * Deactivates a link on behalf of whoever holds its management token — the only proof of
     * "ownership" this no-auth system has (see ShortLink javadoc). Idempotent: deactivating an
     * already-deactivated link succeeds silently rather than erroring, per normal DELETE
     * semantics. Always evicts the cache entry, even if it wasn't already deactivated — the
     * redirect cache-aside path (see resolve()) has no other invalidation trigger, so without
     * this a "deleted" link would keep redirecting for up to the cache's TTL.
     */
    @Transactional
    public void deactivate(String code, String providedToken) {
        ShortLink link = repository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));

        if (providedToken == null || !constantTimeEquals(link.getManagementToken(), providedToken)) {
            throw new LinkAccessDeniedException();
        }

        link.deactivate(clock.instant());
        repository.save(link);
        cache.invalidate(code);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private void validateLongUrl(String longUrl) {
        try {
            URI uri = new URI(longUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
                throw new InvalidUrlException("longUrl must use http or https");
            }
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("longUrl is not a valid URL");
        }
    }

    private LinkResponse toResponse(ShortLink link) {
        String shortUrl = properties.baseUrl() + "/" + link.getCode();
        return new LinkResponse(link.getCode(), shortUrl, link.getLongUrl(), link.getExpiresAt(),
                link.getCreatedAt(), link.getManagementToken());
    }
}
