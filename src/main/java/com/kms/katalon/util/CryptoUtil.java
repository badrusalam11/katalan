package com.kms.katalon.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Katalon compatibility stub for CryptoUtil
 * Basic encryption/decryption utility.
 */
public class CryptoUtil {
    
    private static final String DEFAULT_KEY = "KatalanKey123456"; // 16 bytes
    private static final String ALGORITHM = "AES";
    
    public static String encode(String plainText) {
        return encode(plainText, DEFAULT_KEY);
    }
    
    public static String encode(String plainText, String key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(normalizeKey(key), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed: " + e.getMessage(), e);
        }
    }
    
    public static String decode(String cipherText) {
        return decode(cipherText, DEFAULT_KEY);
    }
    
    public static String decode(String cipherText, String key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(normalizeKey(key), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }
    
    private static byte[] normalizeKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] normalized = new byte[16];
        System.arraycopy(keyBytes, 0, normalized, 0, Math.min(keyBytes.length, 16));
        return normalized;
    }
}
