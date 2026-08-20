package com.urlshortener.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, String message, Map<String, Object> details) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, null);
    }

    public static ErrorResponse of(String error, String message, Map<String, Object> details) {
        return new ErrorResponse(error, message, details);
    }
}
