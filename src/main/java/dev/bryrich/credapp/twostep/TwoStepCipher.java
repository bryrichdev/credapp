package dev.bryrich.credapp.twostep;

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
 * Encrypts two-step secrets with AES-256-GCM under the SSN key, stored as
 * {@code IV || ciphertext || tag}. The additional data ties each ciphertext to this purpose
 * and account, so a secret can't be copied onto another account.
 */
@Component
class TwoStepCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    TwoStepCipher(@Value("${credapp.security.ssn-key}") String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException("credapp.security.ssn-key must decode to 32 bytes; got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    byte[] encrypt(long userId, byte[] plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(purpose(userId));
            byte[] sealed = cipher.doFinal(plain);
            return ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt a two-step secret", e);
        }
    }

    byte[] decrypt(long userId, byte[] stored) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, Arrays.copyOfRange(stored, 0, IV_LENGTH)));
            cipher.updateAAD(purpose(userId));
            return cipher.doFinal(stored, IV_LENGTH, stored.length - IV_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to decrypt a two-step secret", e);
        }
    }

    private static byte[] purpose(long userId) {
        return ("credcloud-totp-v1:" + userId).getBytes(StandardCharsets.UTF_8);
    }
}
