package com.urlshortener.common.exception;

/**
 * Missing or incorrect management token on a mutating link operation (currently: delete).
 * Deliberately does not reveal whether the code exists vs. the token was simply wrong — both
 * cases return the same 403, so this can't be used to probe for valid codes.
 */
public class LinkAccessDeniedException extends RuntimeException {
    public LinkAccessDeniedException() {
        super("Missing or invalid management token");
    }
}
