package dev.bryrich.credapp.document;

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
 * Encrypts document files with AES-256-GCM under the same key as SSNs. Stored as
 * {@code IV || ciphertext || tag}. The additional data ties each ciphertext to its purpose, so
 * an encrypted SSN can never be passed off as a document or the other way round.
 */
@Component
public class DocumentCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final byte[] PURPOSE = "credcloud-document-v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public DocumentCipher(@Value("${credapp.security.ssn-key}") String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException("credapp.security.ssn-key must decode to 32 bytes; got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(PURPOSE);
            byte[] sealed = cipher.doFinal(plain);
            return ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt document", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        if (stored == null || stored.length <= IV_LENGTH) {
            throw new IllegalStateException("Stored document is malformed");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, Arrays.copyOfRange(stored, 0, IV_LENGTH)));
            cipher.updateAAD(PURPOSE);
            return cipher.doFinal(stored, IV_LENGTH, stored.length - IV_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to decrypt document", e);
        }
    }
}
