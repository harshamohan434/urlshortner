package com.urlshortener.analytics;

/**
 * Deliberately coarse, heuristic User-Agent classification (substring matching only) — not a
 * real UA database. This is a documented v1 scope trade-off: full device/browser detection
 * would need an external library or dataset, which wasn't worth pulling in for the "coarse"
 * analytics field the requirements actually asked for.
 */
public final class UserAgentClassifier {

    private UserAgentClassifier() {
    }

    public static String deviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("bot") || ua.contains("spider") || ua.contains("crawler")) {
            return "bot";
        }
        if (ua.contains("tablet") || ua.contains("ipad")) {
            return "tablet";
        }
        if (ua.contains("mobi") || ua.contains("android") || ua.contains("iphone")) {
            return "mobile";
        }
        return "desktop";
    }

    public static String browserFamily(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg/") || ua.contains("edge/")) {
            return "Edge";
        }
        if (ua.contains("chrome/") && !ua.contains("chromium")) {
            return "Chrome";
        }
        if (ua.contains("firefox/")) {
            return "Firefox";
        }
        if (ua.contains("safari/") && !ua.contains("chrome/")) {
            return "Safari";
        }
        return "Other";
    }
}
