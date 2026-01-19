package com.pesitwizard.common.crypto;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PemUtils {

    public static String toPem(Object obj) throws CryptoException {
        try {
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
                writer.writeObject(obj);
            }
            return sw.toString();
        } catch (Exception e) {
            throw new CryptoException("Failed to convert to PEM", e);
        }
    }

    public static X509Certificate parseCertificate(String pem) throws CryptoException {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(pem.getBytes()));
        } catch (Exception e) {
            throw new CryptoException("Failed to parse certificate", e);
        }
    }

    public static PrivateKey parsePrivateKey(String pem) throws CryptoException {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

            if (obj instanceof PEMKeyPair) {
                return converter.getPrivateKey(((PEMKeyPair) obj).getPrivateKeyInfo());
            } else if (obj instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) obj);
            }
            throw new CryptoException("Unknown private key format: " + obj.getClass().getName());
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Failed to parse private key", e);
        }
    }
}
