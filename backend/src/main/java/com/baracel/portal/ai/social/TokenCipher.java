package com.baracel.portal.ai.social;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM for social tokens. A stored value looks like {@code keyId:base64url(iv || ciphertext+tag)}. New values use the current
 * key; values written with an older key id still decrypt while that key is listed in social.crypto.old-keys ("id:base64,id2:base64").
 * Never logs or returns a key or a token.
 */
@Component
public class TokenCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String currentId;
    private final Map<String, byte[]> keys = new HashMap<>();

    public TokenCipher(SocialProperties props) {
        this.currentId = props.crypto().tokenKeyId();
        if (!props.crypto().tokenKey().isBlank()) keys.put(currentId, decodeKey(props.crypto().tokenKey()));
        for (String pair : props.crypto().oldKeys().split(",")) {
            String[] kv = pair.trim().split(":", 2);
            if (kv.length == 2 && !kv[0].isBlank()) keys.put(kv[0].trim(), decodeKey(kv[1].trim()));
        }
    }

    private static byte[] decodeKey(String b64) {
        byte[] k = Base64.getDecoder().decode(b64);
        if (k.length != 32) throw new IllegalStateException("SOCIAL_TOKEN_KEY must be 32 bytes, base64 encoded (for example: openssl rand -base64 32)");
        return k;
    }

    public boolean configured() { return keys.containsKey(currentId); }

    public String encrypt(String plain) {
        byte[] key = keys.get(currentId);
        if (key == null) throw new IllegalStateException("SOCIAL_TOKEN_KEY is not set, so tokens cannot be stored.");
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(currentId.getBytes(StandardCharsets.UTF_8));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return currentId + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(all);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a token");
        }
    }

    public String decrypt(String stored) {
        int i = stored.indexOf(':');
        if (i < 1) throw new IllegalStateException("Unreadable token");
        String id = stored.substring(0, i);
        byte[] key = keys.get(id);
        if (key == null) throw new IllegalStateException("No key with id " + id + " to read this token");
        try {
            byte[] all = Base64.getUrlDecoder().decode(stored.substring(i + 1));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            c.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a token");
        }
    }
}
