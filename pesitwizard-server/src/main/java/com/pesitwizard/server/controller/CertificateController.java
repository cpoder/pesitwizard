package com.pesitwizard.server.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pesitwizard.server.entity.CertificateStore;
import com.pesitwizard.server.entity.CertificateStore.CertificatePurpose;
import com.pesitwizard.server.entity.CertificateStore.StoreFormat;
import com.pesitwizard.server.entity.CertificateStore.StoreType;
import com.pesitwizard.server.service.CertificateAuthorityService;
import com.pesitwizard.server.service.CertificateAuthorityService.CertificateRequest;
import com.pesitwizard.server.service.CertificateAuthorityService.SignedCertificate;
import com.pesitwizard.server.service.CertificateService;
import com.pesitwizard.server.service.CertificateService.CertificateStatistics;
import com.pesitwizard.server.ssl.SslConfigurationException;
import com.pesitwizard.server.ssl.SslContextFactory.CertificateInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for certificate management.
 * Provides endpoints for managing keystores and truststores centrally.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final CertificateAuthorityService caService;

    // ========== List & Get ==========

    /**
     * List all certificate stores
     */
    @GetMapping
    public ResponseEntity<List<CertificateStore>> listCertificates() {
        return ResponseEntity.ok(certificateService.getAllCertificateStores());
    }

    /**
     * List keystores
     */
    @GetMapping("/keystores")
    public ResponseEntity<List<CertificateStore>> listKeystores(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        if (activeOnly) {
            return ResponseEntity.ok(certificateService.getActiveCertificateStoresByType(StoreType.KEYSTORE));
        }
        return ResponseEntity.ok(certificateService.getCertificateStoresByType(StoreType.KEYSTORE));
    }

    /**
     * List truststores
     */
    @GetMapping("/truststores")
    public ResponseEntity<List<CertificateStore>> listTruststores(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        if (activeOnly) {
            return ResponseEntity.ok(certificateService.getActiveCertificateStoresByType(StoreType.TRUSTSTORE));
        }
        return ResponseEntity.ok(certificateService.getCertificateStoresByType(StoreType.TRUSTSTORE));
    }

    /**
     * Get a specific certificate store
     */
    @GetMapping("/{id}")
    public ResponseEntity<CertificateStore> getCertificate(@PathVariable Long id) {
        return certificateService.getCertificateStore(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get certificate store by name
     */
    @GetMapping("/name/{name}")
    public ResponseEntity<CertificateStore> getCertificateByName(@PathVariable String name) {
        return certificateService.getCertificateStoreByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get default keystore
     */
    @GetMapping("/keystores/default")
    public ResponseEntity<CertificateStore> getDefaultKeystore() {
        return certificateService.getDefaultCertificateStore(StoreType.KEYSTORE)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get default truststore
     */
    @GetMapping("/truststores/default")
    public ResponseEntity<CertificateStore> getDefaultTruststore() {
        return certificateService.getDefaultCertificateStore(StoreType.TRUSTSTORE)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ========== Create ==========

    /**
     * Upload a new keystore
     */
    @PostMapping(value = "/keystores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadKeystore(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "PKCS12") StoreFormat format,
            @RequestParam(required = false) String storePassword,
            @RequestParam(required = false) String keyPassword,
            @RequestParam(required = false) String keyAlias,
            @RequestParam(defaultValue = "SERVER") CertificatePurpose purpose,
            @RequestParam(required = false) String partnerId,
            @RequestParam(defaultValue = "false") boolean isDefault,
            Principal principal) {

        try {
            CertificateStore store = certificateService.createCertificateStore(
                    name,
                    description,
                    StoreType.KEYSTORE,
                    format,
                    file.getBytes(),
                    storePassword,
                    keyPassword,
                    keyAlias,
                    purpose,
                    partnerId,
                    isDefault,
                    principal.getName());

            log.info("Keystore uploaded: {}", name);
            return ResponseEntity.status(HttpStatus.CREATED).body(store);

        } catch (SslConfigurationException e) {
            log.error("Invalid keystore: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid keystore: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to upload keystore", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to upload keystore: " + e.getMessage()));
        }
    }

    /**
     * Upload a new truststore
     */
    @PostMapping(value = "/truststores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadTruststore(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "PKCS12") StoreFormat format,
            @RequestParam(required = false) String storePassword,
            @RequestParam(required = false) String partnerId,
            @RequestParam(defaultValue = "false") boolean isDefault) {

        try {
            CertificateStore store = certificateService.createCertificateStore(
                    name,
                    description,
                    StoreType.TRUSTSTORE,
                    format,
                    file.getBytes(),
                    storePassword,
                    null,
                    null,
                    CertificatePurpose.CA,
                    partnerId,
                    isDefault,
                    "api");

            log.info("Truststore uploaded: {}", name);
            return ResponseEntity.status(HttpStatus.CREATED).body(store);

        } catch (SslConfigurationException e) {
            log.error("Invalid truststore: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid truststore: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to upload truststore", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to upload truststore: " + e.getMessage()));
        }
    }

    // ========== Update ==========

    /**
     * Update a certificate store
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateCertificate(
            @PathVariable Long id,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String storePassword,
            @RequestParam(required = false) String keyPassword,
            @RequestParam(required = false) String keyAlias,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean isDefault) {

        try {
            byte[] storeData = file != null ? file.getBytes() : null;

            CertificateStore store = certificateService.updateCertificateStore(
                    id,
                    description,
                    storeData,
                    storePassword,
                    keyPassword,
                    keyAlias,
                    active,
                    isDefault,
                    "api");

            return ResponseEntity.ok(store);

        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid certificate: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to update certificate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update certificate: " + e.getMessage()));
        }
    }

    // ========== Actions ==========

    /**
     * Set a certificate store as default
     */
    @PostMapping("/{id}/set-default")
    public ResponseEntity<CertificateStore> setAsDefault(@PathVariable Long id) {
        try {
            CertificateStore store = certificateService.setAsDefault(id);
            log.info("Set certificate {} as default", store.getName());
            return ResponseEntity.ok(store);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Activate a certificate store
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<CertificateStore> activate(@PathVariable Long id) {
        try {
            CertificateStore store = certificateService.activate(id);
            log.info("Activated certificate: {}", store.getName());
            return ResponseEntity.ok(store);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Deactivate a certificate store
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<CertificateStore> deactivate(@PathVariable Long id) {
        try {
            CertificateStore store = certificateService.deactivate(id);
            log.info("Deactivated certificate: {}", store.getName());
            return ResponseEntity.ok(store);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Validate a certificate store
     */
    @PostMapping("/{id}/validate")
    public ResponseEntity<?> validate(@PathVariable Long id) {
        try {
            certificateService.validateCertificateStore(id);
            return ResponseEntity.ok(new SuccessResponse("Certificate is valid"));
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Validation failed: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get certificate details/info
     */
    @GetMapping("/{id}/info")
    public ResponseEntity<?> getCertificateInfo(@PathVariable Long id) {
        try {
            CertificateInfo info = certificateService.getCertificateInfo(id);
            return ResponseEntity.ok(info);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Failed to get info: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ========== Delete ==========

    /**
     * Delete a certificate store
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCertificate(@PathVariable Long id) {
        try {
            certificateService.deleteCertificateStore(id);
            log.info("Deleted certificate: {}", id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ========== Expiration ==========

    /**
     * Get expiring certificates
     */
    @GetMapping("/expiring")
    public ResponseEntity<List<CertificateStore>> getExpiringCertificates(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(certificateService.getExpiringCertificates(days));
    }

    /**
     * Get expired certificates
     */
    @GetMapping("/expired")
    public ResponseEntity<List<CertificateStore>> getExpiredCertificates() {
        return ResponseEntity.ok(certificateService.getExpiredCertificates());
    }

    // ========== Partner Certificates ==========

    /**
     * Get certificates for a partner
     */
    @GetMapping("/partner/{partnerId}")
    public ResponseEntity<List<CertificateStore>> getPartnerCertificates(@PathVariable String partnerId) {
        return ResponseEntity.ok(certificateService.getPartnerCertificates(partnerId));
    }

    // ========== Create Empty Stores ==========

    /**
     * Create an empty keystore
     */
    @PostMapping("/keystores/create")
    public ResponseEntity<?> createEmptyKeystore(
            @RequestParam("name") String name,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "PKCS12") StoreFormat format,
            @RequestParam String storePassword,
            @RequestParam(defaultValue = "SERVER") CertificatePurpose purpose,
            @RequestParam(required = false) String partnerId,
            @RequestParam(defaultValue = "false") boolean isDefault) {

        try {
            CertificateStore store = certificateService.createEmptyKeystore(
                    name, description, format, storePassword, purpose, partnerId, isDefault, "api");
            return ResponseEntity.status(HttpStatus.CREATED).body(store);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Create an empty truststore
     */
    @PostMapping("/truststores/create")
    public ResponseEntity<?> createEmptyTruststore(
            @RequestParam("name") String name,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "PKCS12") StoreFormat format,
            @RequestParam String storePassword,
            @RequestParam(required = false) String partnerId,
            @RequestParam(defaultValue = "false") boolean isDefault) {

        try {
            CertificateStore store = certificateService.createEmptyTruststore(
                    name, description, format, storePassword, partnerId, isDefault, "api");
            return ResponseEntity.status(HttpStatus.CREATED).body(store);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    // ========== Add Certificates ==========

    /**
     * Add a certificate to a truststore
     */
    @PostMapping(value = "/{id}/certificates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addCertificate(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("alias") String alias) {

        try {
            CertificateStore store = certificateService.addCertificateToTruststore(id, file.getBytes(), alias);
            log.info("Added certificate '{}' to store {}", alias, id);
            return ResponseEntity.ok(store);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to add certificate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add certificate: " + e.getMessage()));
        }
    }

    /**
     * Add a key pair to a keystore
     */
    @PostMapping(value = "/{id}/keypair", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addKeyPair(
            @PathVariable Long id,
            @RequestParam("certificate") MultipartFile certificate,
            @RequestParam("privateKey") MultipartFile privateKey,
            @RequestParam("alias") String alias,
            @RequestParam(required = false) String keyPassword) {

        try {
            CertificateStore store = certificateService.addKeyPairToKeystore(
                    id, certificate.getBytes(), privateKey.getBytes(), alias, keyPassword);
            log.info("Added key pair '{}' to store {}", alias, id);
            return ResponseEntity.ok(store);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to add key pair", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add key pair: " + e.getMessage()));
        }
    }

    // ========== List & Remove Entries ==========

    /**
     * List entries in a certificate store
     */
    @GetMapping("/{id}/entries")
    public ResponseEntity<?> listEntries(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(certificateService.listStoreEntries(id));
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Remove an entry from a certificate store
     */
    @DeleteMapping("/{id}/entries/{alias}")
    public ResponseEntity<?> removeEntry(@PathVariable Long id, @PathVariable String alias) {
        try {
            CertificateStore store = certificateService.removeStoreEntry(id, alias);
            log.info("Removed entry '{}' from store {}", alias, id);
            return ResponseEntity.ok(store);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ========== Statistics ==========

    /**
     * Get certificate statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<CertificateStatistics> getStatistics() {
        return ResponseEntity.ok(certificateService.getStatistics());
    }

    // ========== Certificate Authority ==========

    /**
     * Initialize the private CA (creates self-signed CA certificate)
     */
    @PostMapping("/ca/initialize")
    public ResponseEntity<?> initializeCa() {
        try {
            CertificateStore caStore = caService.initializeCa("api");
            log.info("CA initialized: {}", caStore.getSubjectDn());
            return ResponseEntity.ok(caStore);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Get CA certificate in PEM format (for distribution to clients)
     */
    @GetMapping(value = "/ca/certificate", produces = "application/x-pem-file")
    public ResponseEntity<?> getCaCertificate() {
        try {
            String caCertPem = caService.getCaCertificatePem();
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=ca-certificate.pem")
                    .body(caCertPem);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Generate a CSR (Certificate Signing Request) for a new certificate
     */
    @PostMapping("/ca/csr")
    public ResponseEntity<?> generateCsr(
            @RequestParam String commonName,
            @RequestParam(required = false) String organizationalUnit,
            @RequestParam(required = false) String organization,
            @RequestParam(defaultValue = "CLIENT") CertificatePurpose purpose) {
        try {
            CertificateRequest request = caService.generateCertificateRequest(
                    commonName, organizationalUnit, organization, purpose);
            log.info("CSR generated for: {}", commonName);
            return ResponseEntity.ok(request);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Sign a CSR with the CA
     */
    @PostMapping("/ca/sign")
    public ResponseEntity<?> signCsr(
            @RequestParam String csrPem,
            @RequestParam(defaultValue = "CLIENT") CertificatePurpose purpose,
            @RequestParam(defaultValue = "365") int validityDays,
            @RequestParam(required = false) String partnerId) {
        try {
            SignedCertificate signed = caService.signCertificateRequest(
                    csrPem, purpose, validityDays, partnerId, "api");
            log.info("Certificate signed: {} (expires: {})", signed.getSubjectDn(), signed.getExpiresAt());
            return ResponseEntity.ok(signed);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Generate a complete certificate for a partner (key pair + signed cert)
     */
    @PostMapping("/ca/partner/{partnerId}/generate")
    public ResponseEntity<?> generatePartnerCertificate(
            @PathVariable String partnerId,
            @RequestParam String commonName,
            @RequestParam(defaultValue = "CLIENT") CertificatePurpose purpose,
            @RequestParam(defaultValue = "365") int validityDays) {
        try {
            CertificateStore store = caService.generatePartnerCertificate(
                    partnerId, commonName, purpose, validityDays, "api");
            log.info("Partner certificate generated: {} for {}", store.getName(), partnerId);
            return ResponseEntity.status(HttpStatus.CREATED).body(store);
        } catch (SslConfigurationException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Verify a certificate was signed by our CA
     */
    @PostMapping("/ca/verify")
    public ResponseEntity<?> verifyCertificate(@RequestParam String certificatePem) {
        boolean valid = caService.verifyCertificate(certificatePem);
        if (valid) {
            return ResponseEntity.ok(new SuccessResponse("Certificate is valid and signed by our CA"));
        } else {
            return ResponseEntity.ok(new ErrorResponse("Certificate is not valid or not signed by our CA"));
        }
    }

    // ========== Response DTOs ==========

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ErrorResponse {
        private String error;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class SuccessResponse {
        private String message;
    }
}
