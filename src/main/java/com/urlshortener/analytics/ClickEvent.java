package com.urlshortener.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One recorded click, keyed by the short link's {@code code} (denormalized string, not a
 * numeric FK) so the async recording path never needs to touch the ShortLink entity/session —
 * simpler and avoids any cross-thread persistence-context concerns. Deliberately holds no IP
 * address or other PII: only referrer and a coarse device/browser classification.
 */
@Entity
@Table(name = "click_event", indexes = {
        @Index(name = "idx_click_event_code", columnList = "code"),
        // Added alongside the daily-rollup endpoint: without this, "clicks for a code in the
        // last N days" degrades to a residual scan past the code-only index as click volume
        // grows for a popular link. Added additively (existing index kept) rather than
        // replacing it, to keep this a purely additive brownfield change.
        @Index(name = "idx_click_event_code_occurred_at", columnList = "code, occurredAt")
})
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(length = 512)
    private String referrer;

    @Column(nullable = false, length = 32)
    private String deviceType;

    @Column(nullable = false, length = 32)
    private String browserFamily;

    protected ClickEvent() {
        // JPA
    }

    public ClickEvent(String code, Instant occurredAt, String referrer, String deviceType, String browserFamily) {
        this.code = code;
        this.occurredAt = occurredAt;
        this.referrer = referrer;
        this.deviceType = deviceType;
        this.browserFamily = browserFamily;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getBrowserFamily() {
        return browserFamily;
    }
}
