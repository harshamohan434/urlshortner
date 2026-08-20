package com.urlshortener.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A short code mapped to a long URL. {@code code} is nullable at the schema level only to
 * support the two-phase insert used for auto-generated codes (see LinkService): the row is
 * first persisted to obtain its auto-increment id, then updated with the Base62-encoded code.
 * Custom-alias links skip that phase and get their code set in a single insert. Multiple
 * transient nulls are safe here since unique constraints treat NULLs as distinct in both H2
 * and Postgres, and the null window never survives past the create transaction.
 *
 * <p>{@code managementToken} + {@code deactivatedAt}: this system has no auth, so "the person
 * who created the link" can't be verified by identity. The management token (a random secret
 * returned only once, in the create response) is the ownership proof instead — see
 * docs/ai-log.md for the ambiguous-requirement resolution this implements. Deactivation is
 * soft (row + analytics history kept), matching how expiry already works.
 */
@Entity
@Table(name = "short_link", indexes = @Index(name = "idx_short_link_code", columnList = "code", unique = true))
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 2048)
    private String longUrl;

    @Column(nullable = false)
    private boolean customAlias;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant expiresAt;

    @Column(nullable = false)
    private long clickCount = 0L;

    private Instant lastAccessedAt;

    @Column(nullable = false, length = 64)
    private String managementToken;

    private Instant deactivatedAt;

    protected ShortLink() {
        // JPA
    }

    public ShortLink(String longUrl, String code, boolean customAlias, Instant createdAt, Instant expiresAt,
                      String managementToken) {
        this.longUrl = longUrl;
        this.code = code;
        this.customAlias = customAlias;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.managementToken = managementToken;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean isDeactivated() {
        return deactivatedAt != null;
    }

    public void deactivate(Instant at) {
        if (this.deactivatedAt == null) {
            this.deactivatedAt = at;
        }
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public boolean isCustomAlias() {
        return customAlias;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public String getManagementToken() {
        return managementToken;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }
}
