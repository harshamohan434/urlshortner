package com.urlshortener.analytics;

import java.time.LocalDate;
import java.util.List;

public record DailyStatsResponse(String code, List<DailyCount> days) {

    public record DailyCount(LocalDate date, long count) {
    }
}
