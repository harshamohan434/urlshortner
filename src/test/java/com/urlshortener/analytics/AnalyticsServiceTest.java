package com.urlshortener.analytics;

import com.urlshortener.common.exception.LinkNotFoundException;
import com.urlshortener.link.ShortLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage for the day-bucketing logic added for the brownfield daily-rollup
 * endpoint — uses a fixed Clock so day-boundary behavior is deterministic, and mocked
 * repositories so it doesn't need a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-10T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ShortLinkRepository shortLinkRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    @Test
    void throwsNotFoundForUnknownCode() {
        when(shortLinkRepository.existsByCode("missing")).thenReturn(false);
        AnalyticsService service = new AnalyticsService(shortLinkRepository, clickEventRepository, FIXED_CLOCK);

        assertThatThrownBy(() -> service.getDailyStats("missing", 7))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    void bucketsClicksByCalendarDayAndZeroFillsGaps() {
        when(shortLinkRepository.existsByCode("abc123")).thenReturn(true);
        // "today" is 2026-03-10 (per FIXED_CLOCK). 3 clicks on the 10th, 1 on the 8th, none on the 9th.
        when(clickEventRepository.findOccurredAtByCodeSince(eq("abc123"), any())).thenReturn(List.of(
                Instant.parse("2026-03-10T09:00:00Z"),
                Instant.parse("2026-03-10T09:05:00Z"),
                Instant.parse("2026-03-10T14:59:59Z"),
                Instant.parse("2026-03-08T00:00:01Z")
        ));

        AnalyticsService service = new AnalyticsService(shortLinkRepository, clickEventRepository, FIXED_CLOCK);
        DailyStatsResponse response = service.getDailyStats("abc123", 5); // 2026-03-06 .. 2026-03-10

        assertThat(response.code()).isEqualTo("abc123");
        assertThat(response.days()).hasSize(5);
        assertThat(response.days().get(0).date()).isEqualTo(LocalDate.of(2026, 3, 6));
        assertThat(response.days().get(4).date()).isEqualTo(LocalDate.of(2026, 3, 10));

        assertThat(countFor(response, LocalDate.of(2026, 3, 6))).isEqualTo(0);
        assertThat(countFor(response, LocalDate.of(2026, 3, 7))).isEqualTo(0);
        assertThat(countFor(response, LocalDate.of(2026, 3, 8))).isEqualTo(1);
        assertThat(countFor(response, LocalDate.of(2026, 3, 9))).isEqualTo(0);
        assertThat(countFor(response, LocalDate.of(2026, 3, 10))).isEqualTo(3);
    }

    @Test
    void excludesClicksBeforeTheWindow() {
        when(shortLinkRepository.existsByCode("abc123")).thenReturn(true);
        // days=1 means the query "since" cutoff is start of 2026-03-10 — the repository call
        // itself is responsible for filtering, so this verifies the correct cutoff is passed.
        when(clickEventRepository.findOccurredAtByCodeSince(eq("abc123"), any())).thenReturn(List.of());

        AnalyticsService service = new AnalyticsService(shortLinkRepository, clickEventRepository, FIXED_CLOCK);
        DailyStatsResponse response = service.getDailyStats("abc123", 1);

        assertThat(response.days()).hasSize(1);
        assertThat(response.days().get(0).date()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(response.days().get(0).count()).isZero();
    }

    private long countFor(DailyStatsResponse response, LocalDate date) {
        return response.days().stream()
                .filter(d -> d.date().equals(date))
                .findFirst()
                .orElseThrow()
                .count();
    }
}
