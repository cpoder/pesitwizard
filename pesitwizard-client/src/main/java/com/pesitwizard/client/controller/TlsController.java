package com.pesitwizard.client.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pesitwizard.client.repository.PesitServerRepository;
import com.pesitwizard.security.SecretsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for TLS certificate management on PeSIT servers.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/servers/{serverId}/tls")
@RequiredArgsConstructor
public class TlsController {

    private final PesitServerRepository serverRepository;
    private final SecretsService secretsService;

    /**
     * Get TLS configuration status for a server
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getTlsStatus(@PathVariable String serverId) {
        return serverRepository.findById(serverId)
                .map(server -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("tlsEnabled", server.isTlsEnabled());
                    result.put("truststoreConfigured",
                            server.getTruststoreData() != null && server.getTruststoreData().length > 0);
                    result.put("keystoreConfigured",
                            server.getKeystoreData() != null && server.getKeystoreData().length > 0);
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Upload and validate a truststore (CA certificate) for TLS connections.
     * Accepts both PKCS12 keystores and PEM certificates.
     */
    @PostMapping("/truststore")
    public ResponseEntity<Map<String, Object>> uploadTruststore(
            @PathVariable String serverId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password) {

        return serverRepository.findById(serverId)
                .map(server -> {
                    try {
                        byte[] data = file.getBytes();
                        String filename = file.getOriginalFilename();
                        String certSubject = null;
                        String certExpiry = null;
                        byte[] truststoreData;
                        String truststorePassword;

                        // Check if it's a PEM file
                        if (filename != null && (filename.endsWith(".pem") || filename.endsWith(".crt")
                                || filename.endsWith(".cer"))) {
                            // Parse PEM certificate
                            String pemContent = new String(data);
                            X509Certificate cert = parsePemCertificate(pemContent);

                            certSubject = cert.getSubjectX500Principal().getName();
                            certExpiry = cert.getNotAfter().toInstant().toString();

                            // Convert to PKCS12 truststore for storage
                            truststorePassword = "changeit"; // Default password for converted truststore
                            truststoreData = convertCertToTruststore(cert, truststorePassword);

                            log.info("PEM certificate converted to truststore for server {}", serverId);
                        } else {
                            // Assume PKCS12 format
                            if (password == null || password.isEmpty()) {
                                Map<String, Object> error = new HashMap<>();
                                error.put("success", false);
                                error.put("error", "Password is required for PKCS12 keystores");
                                return ResponseEntity.badRequest().body(error);
                            }

                            KeyStore trustStore = KeyStore.getInstance("PKCS12");
                            try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
                                trustStore.load(bis, password.toCharArray());
                            }

                            Enumeration<String> aliases = trustStore.aliases();
                            if (aliases.hasMoreElements()) {
                                String alias = aliases.nextElement();
                                java.security.cert.Certificate cert = trustStore.getCertificate(alias);
                                if (cert instanceof X509Certificate x509) {
                                    certSubject = x509.getSubjectX500Principal().getName();
                                    certExpiry = x509.getNotAfter().toInstant().toString();
                                }
                            }

                            truststoreData = data;
                            truststorePassword = password;
                        }

                        // Store the truststore with encrypted password
                        server.setTruststoreData(truststoreData);
                        server.setTruststorePassword(secretsService.encrypt(truststorePassword));
                        serverRepository.save(server);

                        log.info("Truststore uploaded for server {}: {} bytes", serverId, truststoreData.length);

                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("message", "CA certificate uploaded and validated successfully");
                        response.put("certSubject", certSubject != null ? certSubject : "Unknown");
                        response.put("certExpiry", certExpiry != null ? certExpiry : "Unknown");
                        return ResponseEntity.ok(response);

                    } catch (Exception e) {
                        log.error("Failed to upload truststore for server {}: {}", serverId, e.getMessage());
                        Map<String, Object> error = new HashMap<>();
                        error.put("success", false);
                        error.put("error", "Invalid certificate: " + e.getMessage());
                        return ResponseEntity.badRequest().body(error);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Parse a PEM-encoded X509 certificate
     */
    private X509Certificate parsePemCertificate(String pemContent) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // Handle both with and without headers
        String certContent = pemContent;
        if (!certContent.contains("-----BEGIN")) {
            certContent = "-----BEGIN CERTIFICATE-----\n" + certContent + "\n-----END CERTIFICATE-----";
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(certContent.getBytes())) {
            return (X509Certificate) cf.generateCertificate(bis);
        }
    }

    /**
     * Convert an X509 certificate to a PKCS12 truststore
     */
    private byte[] convertCertToTruststore(X509Certificate cert, String password) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca-cert", cert);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        trustStore.store(bos, password.toCharArray());
        return bos.toByteArray();
    }

    /**
     * Upload and validate a keystore (client certificate) for mutual TLS
     */
    @PostMapping("/keystore")
    public ResponseEntity<Map<String, Object>> uploadKeystore(
            @PathVariable String serverId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("password") String password) {

        return serverRepository.findById(serverId)
                .map(server -> {
                    try {
                        byte[] data = file.getBytes();

                        // Validate the keystore with the provided password
                        KeyStore keyStore = KeyStore.getInstance("PKCS12");
                        try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
                            keyStore.load(bis, password.toCharArray());
                        }

                        // Verify it contains a private key
                        String certSubject = null;
                        String certExpiry = null;
                        boolean hasPrivateKey = false;
                        Enumeration<String> aliases = keyStore.aliases();
                        while (aliases.hasMoreElements()) {
                            String alias = aliases.nextElement();
                            if (keyStore.isKeyEntry(alias)) {
                                hasPrivateKey = true;
                                java.security.cert.Certificate cert = keyStore.getCertificate(alias);
                                if (cert instanceof X509Certificate x509) {
                                    certSubject = x509.getSubjectX500Principal().getName();
                                    certExpiry = x509.getNotAfter().toInstant().toString();
                                }
                                break;
                            }
                        }

                        if (!hasPrivateKey) {
                            Map<String, Object> noKeyError = new HashMap<>();
                            noKeyError.put("success", false);
                            noKeyError.put("error", "Keystore does not contain a private key");
                            return ResponseEntity.badRequest().body(noKeyError);
                        }

                        // Store the keystore with encrypted password
                        server.setKeystoreData(data);
                        server.setKeystorePassword(secretsService.encrypt(password));
                        serverRepository.save(server);

                        log.info("Keystore uploaded for server {}: {} bytes", serverId, data.length);

                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("message", "Keystore uploaded and validated successfully");
                        response.put("certSubject", certSubject != null ? certSubject : "Unknown");
                        response.put("certExpiry", certExpiry != null ? certExpiry : "Unknown");
                        return ResponseEntity.ok(response);

                    } catch (Exception e) {
                        log.error("Failed to upload keystore for server {}: {}", serverId, e.getMessage());
                        Map<String, Object> error = new HashMap<>();
                        error.put("success", false);
                        error.put("error", "Invalid keystore or wrong password: " + e.getMessage());
                        return ResponseEntity.badRequest().body(error);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Remove truststore from server
     */
    @DeleteMapping("/truststore")
    public ResponseEntity<Map<String, Object>> deleteTruststore(@PathVariable String serverId) {
        return serverRepository.findById(serverId)
                .map(server -> {
                    server.setTruststoreData(null);
                    server.setTruststorePassword(null);
                    serverRepository.save(server);
                    log.info("Truststore removed from server {}", serverId);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "Truststore removed");
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Remove keystore from server
     */
    @DeleteMapping("/keystore")
    public ResponseEntity<Map<String, Object>> deleteKeystore(@PathVariable String serverId) {
        return serverRepository.findById(serverId)
                .map(server -> {
                    server.setKeystoreData(null);
                    server.setKeystorePassword(null);
                    serverRepository.save(server);
                    log.info("Keystore removed from server {}", serverId);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "Keystore removed");
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
