package com.urlshortener.link;

import com.urlshortener.analytics.AnalyticsService;
import com.urlshortener.common.exception.LinkDeactivatedException;
import com.urlshortener.common.exception.LinkExpiredException;
import com.urlshortener.common.exception.LinkNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class RedirectController {

    private final LinkService linkService;
    private final AnalyticsService analyticsService;

    public RedirectController(LinkService linkService, AnalyticsService analyticsService) {
        this.linkService = linkService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        ShortLink link = linkService.resolve(code).orElseThrow(() -> new LinkNotFoundException(code));

        if (link.isExpired(Instant.now())) {
            throw new LinkExpiredException(code);
        }
        if (link.isDeactivated()) {
            throw new LinkDeactivatedException(code);
        }

        // Fire-and-forget: the redirect response does not wait on this.
        analyticsService.recordClickAsync(code, request.getHeader("Referer"), request.getHeader("User-Agent"));

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, link.getLongUrl())
                .build();
    }
}
