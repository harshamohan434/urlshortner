package com.urlshortener.common.exception;

public class LinkNotFoundException extends RuntimeException {
    private final String code;

    public LinkNotFoundException(String code) {
        super("No link found for code '" + code + "'");
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
