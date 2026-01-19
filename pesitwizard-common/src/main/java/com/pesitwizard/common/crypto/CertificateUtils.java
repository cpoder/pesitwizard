package com.pesitwizard.common.crypto;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CertificateUtils {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    public static X509Certificate generateSelfSignedCertificate(
            KeyPair keyPair, X500Name subject, Duration validity, boolean isCA) throws CryptoException {
        try {
            BigInteger serialNumber = new BigInteger(128, new SecureRandom());
            Date notBefore = new Date();
            Date notAfter = Date.from(Instant.now().plus(validity));

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject, serialNumber, notBefore, notAfter, subject, keyPair.getPublic());

            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(isCA));
            if (isCA) {
                builder.addExtension(Extension.keyUsage, true, 
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            }

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keyPair.getPrivate());
            return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        } catch (Exception e) {
            throw new CryptoException("Failed to generate certificate", e);
        }
    }

    public static String calculateFingerprint(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) sb.append(":");
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "N/A";
        }
    }
}
