package com.kms.katalon.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Katalon compatibility class for CryptoUtil.
 *
 * Same algorithm, salt, key and iteration count as Katalon Studio's own
 * com.kms.katalon.util.CryptoUtil, so text encrypted in Katalon Studio (Tools >
 * Encrypt Text, used by setEncryptedText) decrypts to the same plain text here.
 */
public final class CryptoUtil {

    private static final String DF_ALGORITHM = "PBEwithSHA1AndDESede";
    private static final String DF_SALT = "K@tal0n STudlO";
    private static final String DF_SECRET_KEY = "S3cReT K3i";
    private static final int DF_ITERATION = 20;
    private static final String DF_ENCODING = "UTF-8";

    private CryptoUtil() {}

    public static CrytoInfo getDefault(String data) {
        return create(DF_ALGORITHM, data, DF_SALT.getBytes(), DF_SECRET_KEY);
    }

    public static CrytoInfo getDefault(String salt, String data) {
        return create(DF_ALGORITHM, data, salt.getBytes(), DF_SECRET_KEY);
    }

    public static CrytoInfo create(String algorithm, String data, byte[] salt, String privateKey) {
        CrytoInfo info = new CrytoInfo();
        info.algorithm = algorithm;
        info.data = data;
        info.salt = salt;
        info.privateKey = privateKey;
        return info;
    }

    public static CrytoInfo create(String algorithm, String data, byte[] salt, String privateKey, int iteration) {
        CrytoInfo info = create(algorithm, data, salt, privateKey);
        info.iteration = iteration;
        return info;
    }

    public static CrytoInfo create(String algorithm, String data, byte[] salt, String privateKey, int iteration,
            String encode) {
        CrytoInfo info = create(algorithm, data, salt, privateKey, iteration);
        info.encode = encode;
        return info;
    }

    public static String encode(CrytoInfo info) throws GeneralSecurityException, UnsupportedEncodingException {
        Cipher cipher = initCipher(info, Cipher.ENCRYPT_MODE);
        return Base64.getEncoder().encodeToString(cipher.doFinal(info.data.getBytes(info.encode)));
    }

    public static String decode(CrytoInfo info) throws GeneralSecurityException, IOException {
        Cipher cipher = initCipher(info, Cipher.DECRYPT_MODE);
        return new String(cipher.doFinal(Base64.getDecoder().decode(info.data)), info.encode);
    }

    /** katalan convenience: encrypt with Katalon Studio's default settings. */
    public static String encode(String plainText) {
        try {
            return encode(getDefault(plainText));
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /** katalan convenience: decrypt text encrypted with Katalon Studio's default settings. */
    public static String decode(String cipherText) {
        try {
            return decode(getDefault(cipherText));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    private static Cipher initCipher(CrytoInfo info, int mode) throws GeneralSecurityException {
        SecretKey key = SecretKeyFactory.getInstance(info.algorithm)
                .generateSecret(new PBEKeySpec(info.privateKey.toCharArray()));
        Cipher cipher = Cipher.getInstance(info.algorithm);
        cipher.init(mode, key, new PBEParameterSpec(info.salt, info.iteration));
        return cipher;
    }

    public static class CrytoInfo {
        private String data;
        private String algorithm;
        private byte[] salt;
        private String privateKey;
        private int iteration = DF_ITERATION;
        private String encode = DF_ENCODING;
    }
}
