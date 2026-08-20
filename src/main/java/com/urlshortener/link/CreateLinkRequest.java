package com.urlshortener.link;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * customAlias and expiresAt are optional. longUrl's scheme/length are validated in
 * {@link LinkService}, not here, since the allow-listed-scheme check isn't expressible as a
 * simple annotation without adding a custom-validator dependency for one field.
 */
public record CreateLinkRequest(
        @NotBlank(message = "longUrl is required")
        @Size(max = 2048, message = "longUrl must be at most 2048 characters")
        String longUrl,

        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "customAlias must be 3-32 characters of letters, digits, '_' or '-'")
        String customAlias,

        Instant expiresAt
) {
}
