package com.urlshortener.common.exception;

/**
 * Extremely rare edge case: a generated Base62 code (derived from the row's auto-increment id)
 * collides with a code already claimed as a custom alias. Auto-generated codes and custom
 * aliases share one namespace by design (see docs/scenarios.md), so this is theoretically
 * possible if someone deliberately claims a numeric-looking alias that a later id's encoding
 * happens to produce. Documented as a known v2 cleanup item (e.g. reserving a disjoint alias
 * character range) rather than solved here — for now we fail loudly instead of silently
 * corrupting data.
 */
public class CodeGenerationConflictException extends RuntimeException {
    public CodeGenerationConflictException(String code) {
        super("Generated code '" + code + "' collided with an existing link");
    }
}
