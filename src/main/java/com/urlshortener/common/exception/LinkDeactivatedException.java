package com.urlshortener.common.exception;

/**
 * Distinct from LinkExpiredException (same 410 status, different error slug) so a client can
 * tell "the TTL passed" apart from "the owner took it down" if it cares to.
 */
public class LinkDeactivatedException extends RuntimeException {
    private final String code;

    public LinkDeactivatedException(String code) {
        super("Link '" + code + "' has been deactivated by its owner");
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
