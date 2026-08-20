package com.urlshortener.common.exception;

public class LinkExpiredException extends RuntimeException {
    private final String code;

    public LinkExpiredException(String code) {
        super("Link '" + code + "' has expired");
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
