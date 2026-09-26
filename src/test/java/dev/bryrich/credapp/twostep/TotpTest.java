package dev.bryrich.credapp.twostep;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B's SHA-1 key. */
    private static final byte[] KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfcTestVectors() {
        // The RFC lists eight digits; apps show the last six.
        assertThat(Totp.code(KEY, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(KEY, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.code(KEY, Totp.step(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
        assertThat(Totp.code(KEY, Totp.step(Instant.ofEpochSecond(20000000000L)))).isEqualTo("353130");
    }

    @Test
    void acceptsOneStepEitherSideAndNeverTheSameStepTwice() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = Totp.step(now);
        assertThat(Totp.match(KEY, Totp.code(KEY, step), now, 0)).isEqualTo(step);
        assertThat(Totp.match(KEY, Totp.code(KEY, step - 1), now, 0)).isEqualTo(step - 1);
        assertThat(Totp.match(KEY, Totp.code(KEY, step + 1), now, 0)).isEqualTo(step + 1);
        assertThat(Totp.match(KEY, Totp.code(KEY, step - 2), now, 0)).isEqualTo(-1);
        assertThat(Totp.match(KEY, Totp.code(KEY, step), now, step)).as("already used").isEqualTo(-1);
        assertThat(Totp.match(KEY, "12345", now, 0)).isEqualTo(-1);
        assertThat(Totp.match(KEY, null, now, 0)).isEqualTo(-1);
    }

    @Test
    void writesKeysAndLinksTheWayAppsReadThem() {
        assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(Totp.uri("foobar".getBytes(StandardCharsets.US_ASCII), "ana@example.com", "CredCloud"))
                .isEqualTo("otpauth://totp/CredCloud:ana%40example.com?secret=MZXW6YTBOI&issuer=CredCloud"
                        + "&algorithm=SHA1&digits=6&period=30");
    }
}
