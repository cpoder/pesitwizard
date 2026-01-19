package com.pesitwizard.common.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KeystoreUtils {

    private static final String KEYSTORE_TYPE = "PKCS12";

    public static KeyPair generateKeyPair(int keySize) throws CryptoException {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(keySize, new SecureRandom());
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new CryptoException("Failed to generate key pair", e);
        }
    }

    public static byte[] createKeystore(X509Certificate cert, PrivateKey key, String alias, String password) throws CryptoException {
        return createKeystoreWithChain(cert, key, new Certificate[]{cert}, alias, password);
    }

    public static byte[] createKeystoreWithChain(X509Certificate cert, PrivateKey key, Certificate[] chain, String alias, String password) throws CryptoException {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            ks.load(null, null);
            ks.setKeyEntry(alias, key, password.toCharArray(), chain);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ks.store(bos, password.toCharArray());
            return bos.toByteArray();
        } catch (Exception e) {
            throw new CryptoException("Failed to create keystore", e);
        }
    }

    public static byte[] createTruststore(String alias, X509Certificate cert, String password) throws CryptoException {
        try {
            KeyStore ts = KeyStore.getInstance(KEYSTORE_TYPE);
            ts.load(null, null);
            ts.setCertificateEntry(alias, cert);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ts.store(bos, password.toCharArray());
            return bos.toByteArray();
        } catch (Exception e) {
            throw new CryptoException("Failed to create truststore", e);
        }
    }

    public static KeyStore loadKeystore(byte[] data, String password) throws CryptoException {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            ks.load(new ByteArrayInputStream(data), password.toCharArray());
            return ks;
        } catch (Exception e) {
            throw new CryptoException("Failed to load keystore", e);
        }
    }

    public static String generateSecurePassword() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
