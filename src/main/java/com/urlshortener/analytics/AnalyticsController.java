package com.urlshortener.analytics;

import com.urlshortener.common.exception.InvalidRequestException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class AnalyticsController {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{code}/stats")
    public StatsResponse stats(@PathVariable String code) {
        return analyticsService.getStats(code);
    }

    @GetMapping("/{code}/stats/daily")
    public DailyStatsResponse dailyStats(@PathVariable String code,
                                          @RequestParam(defaultValue = "7") int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new InvalidRequestException("days must be between " + MIN_DAYS + " and " + MAX_DAYS);
        }
        return analyticsService.getDailyStats(code, days);
    }
}
