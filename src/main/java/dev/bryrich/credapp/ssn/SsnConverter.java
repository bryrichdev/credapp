package dev.bryrich.credapp.ssn;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts provider SSNs at rest with AES-256-GCM.
 *
 * <p>The stored value is base64 of {@code IV || ciphertext || tag}. A fresh random IV is
 * generated per write, so the same SSN encrypts to a different value every time. That is
 * deliberate, and it means the column cannot be searched, indexed, or constrained UNIQUE.
 *
 * <p>Applied explicitly via {@code @Convert} on {@code Provider.ssn} — deliberately NOT
 * {@code autoApply}, which would encrypt every String column in the schema.
 *
 * <p>Instantiated by Spring rather than Hibernate so the key can be injected. There is no
 * no-arg constructor: if the bean container is ever bypassed, startup fails loudly instead
 * of silently running with no key.
 */
@Component
@Converter
public class SsnConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SsnConverter(@Value("${credapp.security.ssn-key}") String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "credapp.security.ssn-key must decode to 32 bytes; got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String ssn) {
        if (ssn == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(ssn.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt SSN", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] blob = Base64.getDecoder().decode(stored);
            if (blob.length <= IV_LENGTH) {
                throw new IllegalStateException("Stored SSN is too short to be valid ciphertext");
            }
            byte[] iv = Arrays.copyOfRange(blob, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(blob, IV_LENGTH, blob.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to decrypt SSN", e);
        }
    }
}
