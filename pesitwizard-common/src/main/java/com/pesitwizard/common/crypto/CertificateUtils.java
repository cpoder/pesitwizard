package com.pesitwizard.common.crypto;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
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

            // Add Subject Key Identifier
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keyPair.getPrivate());
            return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        } catch (java.security.GeneralSecurityException | org.bouncycastle.operator.OperatorCreationException | org.bouncycastle.cert.CertIOException e) {
            throw new CryptoException("Failed to generate certificate", e);
        }
    }

    /**
     * Build a signed certificate with standard X.509 extensions (BasicConstraints, KeyUsage,
     * EKU, SKI, AKI, SAN). This is the shared implementation used by both OSS and enterprise CA services.
     *
     * @param subject      Subject X500Name
     * @param subjectKey   Subject's public key
     * @param issuerCert   CA certificate (issuer)
     * @param issuerKey    CA private key (for signing)
     * @param validityDays Certificate validity in days
     * @param purpose      "SERVER" or "CLIENT"
     * @return Signed X509Certificate with full extensions
     */
    public static X509Certificate buildSignedCertificate(
            X500Name subject,
            PublicKey subjectKey,
            X509Certificate issuerCert,
            PrivateKey issuerKey,
            int validityDays,
            String purpose) throws CryptoException {
        try {
            BigInteger serialNumber = new BigInteger(128, new SecureRandom());
            Date notBefore = new Date();
            Date notAfter = new Date(System.currentTimeMillis() + validityDays * 24L * 60 * 60 * 1000);

            X500Name issuer = new JcaX509CertificateHolder(issuerCert).getSubject();
            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer, serialNumber, notBefore, notAfter, subject, subjectKey);

            // BasicConstraints — not a CA
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

            // KeyUsage + EKU based on purpose
            if ("SERVER".equals(purpose)) {
                certBuilder.addExtension(Extension.keyUsage, true,
                        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
                certBuilder.addExtension(Extension.extendedKeyUsage, false,
                        new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
            } else {
                certBuilder.addExtension(Extension.keyUsage, true,
                        new KeyUsage(KeyUsage.digitalSignature));
                certBuilder.addExtension(Extension.extendedKeyUsage, false,
                        new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
            }

            // SKI and AKI
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(subjectKey));
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(issuerCert));

            // SAN for server certs — use CN as DNS name
            if ("SERVER".equals(purpose)) {
                String cn = extractCn(subject);
                if (cn != null) {
                    certBuilder.addExtension(Extension.subjectAlternativeName, false,
                            new GeneralNames(new GeneralName(GeneralName.dNSName, cn)));
                }
            }

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(issuerKey);
            return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
        } catch (java.security.GeneralSecurityException | org.bouncycastle.operator.OperatorCreationException | org.bouncycastle.cert.CertIOException e) {
            throw new CryptoException("Failed to build signed certificate", e);
        }
    }

    /**
     * Extract the Common Name (CN) from an X500Name.
     */
    public static String extractCn(X500Name name) {
        var cnRdn = name.getRDNs(BCStyle.CN);
        if (cnRdn.length > 0) {
            return IETFUtils.valueToString(cnRdn[0].getFirst().getValue());
        }
        return null;
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
        } catch (java.security.GeneralSecurityException e) {
            return "N/A";
        }
    }
}
