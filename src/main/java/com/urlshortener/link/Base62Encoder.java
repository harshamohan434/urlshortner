package com.urlshortener.link;

/**
 * Encodes/decodes a non-negative long id to/from a Base62 string.
 *
 * <p>Auto-generated short codes are the Base62 encoding of the {@link ShortLink} row's
 * database id, left-padded with the alphabet's zero-character to {@code urlshortener.code-length}.
 * This is deliberately NOT a random-code-with-collision-retry scheme: encoding a sequential id
 * can never collide with another auto-generated code, which avoids retry storms under load.
 *
 * <p>Known limitation (documented, not solved here): {@code code-length} is a display-width
 * convention, not an enforced cap. Ids whose encoding exceeds that width are still encoded
 * correctly (just longer) rather than truncated, since truncation would break uniqueness. At
 * 62^7 (~3.5 trillion) possible 7-character codes this is not a v1 concern.
 */
public final class Base62Encoder {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {
    }

    public static String encode(long id, int minLength) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative: " + id);
        }
        StringBuilder sb = new StringBuilder();
        long n = id;
        if (n == 0) {
            sb.append(ALPHABET.charAt(0));
        }
        while (n > 0) {
            sb.append(ALPHABET.charAt((int) (n % BASE)));
            n /= BASE;
        }
        sb.reverse();
        while (sb.length() < minLength) {
            sb.insert(0, ALPHABET.charAt(0));
        }
        return sb.toString();
    }

    public static long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            int idx = ALPHABET.indexOf(c);
            if (idx < 0) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            result = result * BASE + idx;
        }
        return result;
    }
}
