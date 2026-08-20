package com.urlshortener.analytics;

import com.urlshortener.common.exception.LinkNotFoundException;
import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final ShortLinkRepository shortLinkRepository;
    private final ClickEventRepository clickEventRepository;
    private final Clock clock;

    public AnalyticsService(ShortLinkRepository shortLinkRepository, ClickEventRepository clickEventRepository,
                             Clock clock) {
        this.shortLinkRepository = shortLinkRepository;
        this.clickEventRepository = clickEventRepository;
        this.clock = clock;
    }

    /**
     * Runs off the request thread so the redirect response is never blocked on an analytics
     * write. Failures here must never propagate back to the (already-completed) redirect —
     * they're logged and swallowed, which is the correct trade-off for best-effort analytics
     * vs. a user-facing redirect.
     */
    @Async
    @Transactional
    public void recordClickAsync(String code, String referrer, String userAgent) {
        try {
            Instant now = clock.instant();
            int updated = shortLinkRepository.recordClickIfActive(code, now);
            if (updated == 0) {
                // Link expired between the redirect check and this async write, or was
                // deleted — either way, don't record a click event for it.
                return;
            }
            String deviceType = UserAgentClassifier.deviceType(userAgent);
            String browserFamily = UserAgentClassifier.browserFamily(userAgent);
            clickEventRepository.save(new ClickEvent(code, now, referrer, deviceType, browserFamily));
        } catch (Exception e) {
            log.warn("Failed to record click for code '{}'", code, e);
        }
    }

    @Transactional(readOnly = true)
    public StatsResponse getStats(String code) {
        ShortLink link = shortLinkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));

        List<String> recentReferrers = clickEventRepository.findTop5ByCodeOrderByOccurredAtDesc(code).stream()
                .map(e -> e.getReferrer() == null ? "direct" : e.getReferrer())
                .toList();

        Map<String, Long> deviceBreakdown = new LinkedHashMap<>();
        clickEventRepository.countByDeviceType(code)
                .forEach(row -> deviceBreakdown.put(row.getDeviceType(), row.getTotal()));

        return new StatsResponse(
                link.getCode(),
                link.getClickCount(),
                link.getLastAccessedAt(),
                link.getCreatedAt(),
                link.getExpiresAt(),
                recentReferrers,
                deviceBreakdown
        );
    }

    /**
     * Click counts bucketed by calendar day (in the clock's zone — UTC in production, see
     * ClockConfig) for the last {@code days} days including today. Days with zero clicks are
     * still included with count 0, so a chart consumer never has to fill gaps itself.
     *
     * <p>Bucketing happens in Java, not SQL: this project runs against both H2 (dev) and
     * Postgres (prod), and there's no existing precedent here for a portable date-trunc query
     * across both dialects — see ClickEventRepository.findOccurredAtByCodeSince.
     */
    @Transactional(readOnly = true)
    public DailyStatsResponse getDailyStats(String code, int days) {
        if (!shortLinkRepository.existsByCode(code)) {
            throw new LinkNotFoundException(code);
        }

        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = today.minusDays(days - 1L);
        Instant since = firstDay.atStartOfDay(zone).toInstant();

        Map<LocalDate, Long> counts = clickEventRepository.findOccurredAtByCodeSince(code, since).stream()
                .collect(Collectors.groupingBy(t -> LocalDate.ofInstant(t, zone), Collectors.counting()));

        List<DailyStatsResponse.DailyCount> buckets = new ArrayList<>(days);
        for (LocalDate d = firstDay; !d.isAfter(today); d = d.plusDays(1)) {
            buckets.add(new DailyStatsResponse.DailyCount(d, counts.getOrDefault(d, 0L)));
        }

        return new DailyStatsResponse(code, buckets);
    }
}
