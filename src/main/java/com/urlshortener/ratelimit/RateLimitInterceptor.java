package com.urlshortener.ratelimit;

import com.urlshortener.common.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Implemented as a {@link HandlerInterceptor} rather than a raw servlet {@code Filter}
 * deliberately: interceptors run inside the DispatcherServlet's dispatch, so an exception
 * thrown from preHandle is routed through Spring MVC's normal exception handling and lands in
 * {@link com.urlshortener.common.GlobalExceptionHandler} like any other controller exception.
 * A Filter's exceptions bypass that entirely, which would have meant a second, inconsistent
 * error-response code path just for rate limiting.
 *
 * <p>Only guards write traffic (POST) on the create-link endpoint, per the fixed
 * "write endpoints only" constraint — redirect and stats reads are never rate-limited here.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    public RateLimitInterceptor(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String clientId = clientIdFor(request);
        if (!rateLimiterService.tryConsume(clientId)) {
            throw new RateLimitExceededException(rateLimiterService.secondsUntilNextToken(clientId));
        }
        return true;
    }

    /**
     * Uses the raw remote address only — no X-Forwarded-For trust, since there's no configured
     * reverse-proxy allowlist in v1. Documented limitation: behind an untrusted proxy this
     * under-counts distinct clients (they all share the proxy's address); behind a trusted
     * proxy it over-restricts. Revisit if/when a proxy topology is defined.
     */
    private String clientIdFor(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
