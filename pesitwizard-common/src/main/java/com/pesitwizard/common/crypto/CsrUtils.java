package com.pesitwizard.common.crypto;

import java.io.StringReader;
import java.security.KeyPair;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CsrUtils {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    public static String generateCsr(KeyPair keyPair, String subjectDn) throws CryptoException {
        try {
            X500Name subject = new X500Name(subjectDn);
            JcaPKCS10CertificationRequestBuilder csrBuilder = 
                new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(keyPair.getPrivate());

            PKCS10CertificationRequest csr = csrBuilder.build(signer);
            return PemUtils.toPem(csr);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Failed to generate CSR", e);
        }
    }

    public static PKCS10CertificationRequest parseCsr(String csrPem) throws CryptoException {
        try (PEMParser parser = new PEMParser(new StringReader(csrPem))) {
            Object obj = parser.readObject();
            if (obj instanceof PKCS10CertificationRequest) {
                return (PKCS10CertificationRequest) obj;
            }
            throw new CryptoException("Invalid CSR format");
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Failed to parse CSR", e);
        }
    }
}
