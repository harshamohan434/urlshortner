package com.urlshortener.common.exception;

/**
 * Generic 400 for invalid request parameters (e.g. query params) that don't fit the existing,
 * URL-specific InvalidUrlException. Kept separate rather than reusing/renaming that class, to
 * avoid touching working, already-tested LinkService validation as part of this brownfield
 * change.
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
