package com.vectis.server.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.server.entity.FileChecksum;
import com.vectis.server.entity.FileChecksum.VerificationStatus;
import com.vectis.server.service.FileIntegrityService;
import com.vectis.server.service.FileIntegrityService.IntegrityStatistics;
import com.vectis.server.service.FileIntegrityService.VerificationResult;

import lombok.RequiredArgsConstructor;

/**
 * REST API for file integrity management.
 */
@RestController
@RequestMapping("/api/v1/integrity")
@RequiredArgsConstructor
public class FileIntegrityController {

    private final FileIntegrityService integrityService;

    /**
     * Get checksum by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<FileChecksum> getChecksum(@PathVariable Long id) {
        return integrityService.getChecksum(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get checksum by transfer ID
     */
    @GetMapping("/transfer/{transferId}")
    @PreAuthorize("hasAnyRole('USER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<FileChecksum> getChecksumByTransfer(@PathVariable String transferId) {
        return integrityService.getChecksumByTransferId(transferId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get checksums by partner
     */
    @GetMapping("/partner/{partnerId}")
    @PreAuthorize("hasAnyRole('USER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<Page<FileChecksum>> getChecksumsByPartner(
            @PathVariable String partnerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(integrityService.getChecksumsByPartner(partnerId, page, size));
    }

    /**
     * Get checksums by status
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<Page<FileChecksum>> getChecksumsByStatus(
            @PathVariable VerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(integrityService.getChecksumsByStatus(status, page, size));
    }

    /**
     * Search by filename
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<Page<FileChecksum>> searchByFilename(
            @RequestParam String filename,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(integrityService.searchByFilename(filename, page, size));
    }

    /**
     * Verify a specific file
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<VerificationResult> verifyFile(@PathVariable Long id) {
        return ResponseEntity.ok(integrityService.verifyFile(id));
    }

    /**
     * Verify all pending files
     */
    @PostMapping("/verify-pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> verifyPendingFiles() {
        int verified = integrityService.verifyPendingFiles();
        return ResponseEntity.ok(Map.of(
                "verified", verified,
                "message", "Verified " + verified + " pending files"));
    }

    /**
     * Check if a hash is a duplicate
     */
    @GetMapping("/duplicate-check")
    @PreAuthorize("hasAnyRole('USER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> checkDuplicate(@RequestParam String hash) {
        boolean isDuplicate = integrityService.isDuplicate(hash);
        List<FileChecksum> duplicates = isDuplicate ? integrityService.getDuplicates(hash) : List.of();
        return ResponseEntity.ok(Map.of(
                "isDuplicate", isDuplicate,
                "count", duplicates.size(),
                "duplicates", duplicates));
    }

    /**
     * Get all duplicate file groups
     */
    @GetMapping("/duplicates")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<List<FileChecksum>> getAllDuplicates() {
        return ResponseEntity.ok(integrityService.getAllDuplicates());
    }

    /**
     * Get most duplicated files
     */
    @GetMapping("/most-duplicated")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<List<FileChecksum>> getMostDuplicated(
            @RequestParam(defaultValue = "1") int minCount) {
        return ResponseEntity.ok(integrityService.getMostDuplicated(minCount));
    }

    /**
     * Get integrity statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<IntegrityStatistics> getStatistics() {
        return ResponseEntity.ok(integrityService.getStatistics());
    }
}
