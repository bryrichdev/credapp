package dev.bryrich.credapp.twostep;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Time-based one-time codes (RFC 6238) as authenticator apps make them: HMAC-SHA1, six
 * digits, a new code every 30 seconds.
 */
final class Totp {

    static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int MODULUS = 1_000_000;
    /** Codes one step either side of now still count, for clocks a little out and slow typists. */
    private static final int DRIFT_STEPS = 1;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** A new 160-bit secret. */
    static byte[] newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return secret;
    }

    static long step(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), PERIOD_SECONDS);
    }

    /** The code for one 30-second step, zero-padded to six digits. */
    static String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % MODULUS);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is unavailable", e);
        }
    }

    /**
     * The step a typed code belongs to, or -1 when it matches none near now. Steps at or before
     * {@code usedStep} don't count, so a code can't be used twice.
     */
    static long match(byte[] secret, String typed, Instant now, long usedStep) {
        if (typed == null || !typed.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        long current = step(now);
        long found = -1;
        for (long step = current - DRIFT_STEPS; step <= current + DRIFT_STEPS; step++) {
            // Every candidate is checked, and in constant time, so timing says nothing.
            boolean same = MessageDigest.isEqual(code(secret, step).getBytes(StandardCharsets.US_ASCII),
                    typed.getBytes(StandardCharsets.US_ASCII));
            if (same && step > usedStep && found < 0) {
                found = step;
            }
        }
        return found;
    }

    /** The otpauth:// link an authenticator app reads from the QR code. */
    static String uri(byte[] secret, String account, String issuer) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32(secret) + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    /** RFC 4648 base32 without padding, as authenticator apps expect for typed-in keys. */
    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return out.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
