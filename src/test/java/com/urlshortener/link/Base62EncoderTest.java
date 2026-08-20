package com.urlshortener.link;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62EncoderTest {

    @Test
    void encodesZeroAsPaddedZeroes() {
        assertThat(Base62Encoder.encode(0, 7)).isEqualTo("0000000");
    }

    @Test
    void encodesOneWithPadding() {
        assertThat(Base62Encoder.encode(1, 7)).isEqualTo("0000001");
    }

    @Test
    void encodesMidRangeIdWithPadding() {
        String encoded = Base62Encoder.encode(123456, 7);
        assertThat(encoded).hasSize(7);
    }

    @Test
    void doesNotPadWhenEncodingAlreadyMeetsMinLength() {
        // 62^6 = 56800235584, which needs exactly 7 base62 digits to represent -> no padding
        long id = 56_800_235_584L;
        String encoded = Base62Encoder.encode(id, 7);
        assertThat(encoded).hasSize(7);
    }

    @Test
    void doesNotTruncateWhenEncodingExceedsMinLength() {
        // An id whose encoding is longer than minLength must never be truncated (would break
        // uniqueness) even though it no longer matches the "display width" convention.
        long bigId = Long.MAX_VALUE;
        String encoded = Base62Encoder.encode(bigId, 7);
        assertThat(encoded.length()).isGreaterThan(7);
        assertThat(Base62Encoder.decode(encoded)).isEqualTo(bigId);
    }

    @Test
    void roundTripsEncodeAndDecode() {
        for (long id : new long[] {0, 1, 61, 62, 3844, 999_999, 3_500_000_000_000L}) {
            String encoded = Base62Encoder.encode(id, 7);
            assertThat(Base62Encoder.decode(encoded)).isEqualTo(id);
        }
    }

    @Test
    void rejectsNegativeIds() {
        assertThatThrownBy(() -> Base62Encoder.encode(-1, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidCharactersOnDecode() {
        assertThatThrownBy(() -> Base62Encoder.decode("abc!123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
