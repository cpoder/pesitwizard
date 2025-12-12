package com.vectis.server.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vectis.server.entity.FileChecksum;
import com.vectis.server.entity.FileChecksum.HashAlgorithm;
import com.vectis.server.entity.FileChecksum.TransferDirection;
import com.vectis.server.entity.FileChecksum.VerificationStatus;
import com.vectis.server.repository.FileChecksumRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for file integrity verification.
 * Computes and verifies SHA-256 checksums, detects duplicates.
 */
@SuppressWarnings("null") // Spring Data JPA methods never return null for save/findById
@Slf4j
@Service
@RequiredArgsConstructor
public class FileIntegrityService {

    private final FileChecksumRepository checksumRepository;

    @Value("${pesit.integrity.algorithm:SHA-256}")
    private String defaultAlgorithm;

    @Value("${pesit.integrity.verification-interval-days:7}")
    private int verificationIntervalDays;

    @Value("${pesit.integrity.buffer-size:8192}")
    private int bufferSize;

    // ========== Checksum Computation ==========

    /**
     * Compute checksum for a file
     */
    public String computeChecksum(Path filePath) throws IOException {
        return computeChecksum(filePath, getDefaultAlgorithm());
    }

    /**
     * Compute checksum for a file with specified algorithm
     */
    public String computeChecksum(Path filePath, HashAlgorithm algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithmToJavaName(algorithm));

            try (InputStream is = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unsupported algorithm: " + algorithm, e);
        }
    }

    /**
     * Compute checksum for byte array
     */
    public String computeChecksum(byte[] data) {
        return computeChecksum(data, getDefaultAlgorithm());
    }

    /**
     * Compute checksum for byte array with specified algorithm
     */
    public String computeChecksum(byte[] data, HashAlgorithm algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithmToJavaName(algorithm));
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unsupported algorithm: " + algorithm, e);
        }
    }

    // ========== Checksum Storage ==========

    /**
     * Store checksum for a transferred file
     */
    @Transactional
    public FileChecksum storeChecksum(String filename, long fileSize, String checksumHash,
            String transferId, String partnerId, String serverId, TransferDirection direction,
            String localPath) {

        HashAlgorithm algorithm = getDefaultAlgorithm();

        // Check for duplicates
        List<FileChecksum> existing = checksumRepository.findByChecksumHash(checksumHash);
        int duplicateCount = existing.size();

        // Update duplicate count on existing entries
        if (duplicateCount > 0) {
            for (FileChecksum fc : existing) {
                fc.setDuplicateCount(duplicateCount);
                checksumRepository.save(fc);
            }
            log.info("Duplicate file detected: {} (hash: {}, count: {})",
                    filename, checksumHash.substring(0, 16) + "...", duplicateCount + 1);
        }

        FileChecksum checksum = FileChecksum.builder()
                .checksumHash(checksumHash)
                .algorithm(algorithm)
                .filename(filename)
                .fileSize(fileSize)
                .transferId(transferId)
                .partnerId(partnerId)
                .serverId(serverId)
                .direction(direction)
                .localPath(localPath)
                .status(VerificationStatus.PENDING)
                .duplicateCount(duplicateCount)
                .build();

        checksum = checksumRepository.save(checksum);
        log.debug("Stored checksum for file: {} (hash: {})", filename, checksumHash.substring(0, 16) + "...");

        return checksum;
    }

    /**
     * Store checksum after computing from file
     */
    @Transactional
    public FileChecksum computeAndStore(Path filePath, String transferId, String partnerId,
            String serverId, TransferDirection direction) throws IOException {

        String checksumHash = computeChecksum(filePath);
        long fileSize = Files.size(filePath);
        String filename = filePath.getFileName().toString();

        return storeChecksum(filename, fileSize, checksumHash, transferId, partnerId,
                serverId, direction, filePath.toString());
    }

    // ========== Verification ==========

    /**
     * Verify a file's integrity
     */
    @Transactional
    public VerificationResult verifyFile(Long checksumId) {
        FileChecksum checksum = checksumRepository.findById(checksumId)
                .orElseThrow(() -> new IllegalArgumentException("Checksum not found: " + checksumId));

        return verifyFile(checksum);
    }

    /**
     * Verify a file's integrity
     */
    @Transactional
    public VerificationResult verifyFile(FileChecksum checksum) {
        if (checksum.getLocalPath() == null) {
            return new VerificationResult(false, "No local path stored", VerificationStatus.FAILED);
        }

        Path filePath = Path.of(checksum.getLocalPath());

        if (!Files.exists(filePath)) {
            checksum.setStatus(VerificationStatus.MISSING);
            checksum.setVerifiedAt(Instant.now());
            checksumRepository.save(checksum);
            return new VerificationResult(false, "File not found: " + filePath, VerificationStatus.MISSING);
        }

        try {
            String currentHash = computeChecksum(filePath, checksum.getAlgorithm());

            if (currentHash.equalsIgnoreCase(checksum.getChecksumHash())) {
                checksum.setStatus(VerificationStatus.VERIFIED);
                checksum.setVerifiedAt(Instant.now());
                checksumRepository.save(checksum);
                return new VerificationResult(true, "Checksum verified", VerificationStatus.VERIFIED);
            } else {
                checksum.setStatus(VerificationStatus.FAILED);
                checksum.setVerifiedAt(Instant.now());
                checksumRepository.save(checksum);
                log.warn("Checksum verification failed for file: {} (expected: {}, actual: {})",
                        checksum.getFilename(),
                        checksum.getChecksumHash().substring(0, 16) + "...",
                        currentHash.substring(0, 16) + "...");
                return new VerificationResult(false, "Checksum mismatch", VerificationStatus.FAILED);
            }
        } catch (IOException e) {
            log.error("Error verifying file {}: {}", checksum.getFilename(), e.getMessage());
            return new VerificationResult(false, "Error reading file: " + e.getMessage(), VerificationStatus.FAILED);
        }
    }

    /**
     * Verify all pending files
     */
    @Transactional
    public int verifyPendingFiles() {
        List<FileChecksum> pending = checksumRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.PENDING);
        int verified = 0;

        for (FileChecksum checksum : pending) {
            VerificationResult result = verifyFile(checksum);
            if (result.isSuccess()) {
                verified++;
            }
        }

        log.info("Verified {} of {} pending files", verified, pending.size());
        return verified;
    }

    /**
     * Re-verify files that haven't been checked recently
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    @Transactional
    public void reVerifyOldFiles() {
        Instant cutoff = Instant.now().minus(verificationIntervalDays, ChronoUnit.DAYS);
        List<FileChecksum> oldFiles = checksumRepository.findByVerifiedAtBeforeAndStatusOrderByVerifiedAtAsc(
                cutoff, VerificationStatus.VERIFIED);

        int reVerified = 0;
        for (FileChecksum checksum : oldFiles) {
            VerificationResult result = verifyFile(checksum);
            if (result.isSuccess()) {
                reVerified++;
            }
        }

        if (!oldFiles.isEmpty()) {
            log.info("Re-verified {} of {} old files", reVerified, oldFiles.size());
        }
    }

    // ========== Duplicate Detection ==========

    /**
     * Check if a file is a duplicate
     */
    public boolean isDuplicate(String checksumHash) {
        return checksumRepository.existsByChecksumHashAndAlgorithm(checksumHash, getDefaultAlgorithm());
    }

    /**
     * Get all duplicates of a file
     */
    public List<FileChecksum> getDuplicates(String checksumHash) {
        return checksumRepository.findByChecksumHash(checksumHash);
    }

    /**
     * Get all duplicate file groups
     */
    public List<FileChecksum> getAllDuplicates() {
        return checksumRepository.findDuplicates();
    }

    /**
     * Get files with highest duplicate counts
     */
    public List<FileChecksum> getMostDuplicated(int minCount) {
        return checksumRepository.findByDuplicateCountGreaterThanOrderByDuplicateCountDesc(minCount);
    }

    // ========== Queries ==========

    /**
     * Get checksum by ID
     */
    public Optional<FileChecksum> getChecksum(Long id) {
        return checksumRepository.findById(id);
    }

    /**
     * Get checksum by transfer ID
     */
    public Optional<FileChecksum> getChecksumByTransferId(String transferId) {
        return checksumRepository.findByTransferId(transferId);
    }

    /**
     * Get checksums by partner
     */
    public Page<FileChecksum> getChecksumsByPartner(String partnerId, int page, int size) {
        return checksumRepository.findByPartnerIdOrderByCreatedAtDesc(partnerId, PageRequest.of(page, size));
    }

    /**
     * Get checksums by status
     */
    public Page<FileChecksum> getChecksumsByStatus(VerificationStatus status, int page, int size) {
        return checksumRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size));
    }

    /**
     * Search by filename
     */
    public Page<FileChecksum> searchByFilename(String pattern, int page, int size) {
        return checksumRepository.searchByFilename(pattern, PageRequest.of(page, size));
    }

    // ========== Statistics ==========

    /**
     * Get integrity statistics
     */
    public IntegrityStatistics getStatistics() {
        IntegrityStatistics stats = new IntegrityStatistics();

        stats.setTotalFiles(checksumRepository.count());
        stats.setPendingVerification(checksumRepository.countByStatus(VerificationStatus.PENDING));
        stats.setVerified(checksumRepository.countByStatus(VerificationStatus.VERIFIED));
        stats.setFailed(checksumRepository.countByStatus(VerificationStatus.FAILED));
        stats.setMissing(checksumRepository.countByStatus(VerificationStatus.MISSING));
        stats.setDuplicateGroups(checksumRepository.countDistinctDuplicates());

        return stats;
    }

    // ========== Helper Methods ==========

    private HashAlgorithm getDefaultAlgorithm() {
        return switch (defaultAlgorithm.toUpperCase().replace("-", "_")) {
            case "MD5" -> HashAlgorithm.MD5;
            case "SHA_1", "SHA1" -> HashAlgorithm.SHA_1;
            case "SHA_512", "SHA512" -> HashAlgorithm.SHA_512;
            default -> HashAlgorithm.SHA_256;
        };
    }

    private String algorithmToJavaName(HashAlgorithm algorithm) {
        return switch (algorithm) {
            case MD5 -> "MD5";
            case SHA_1 -> "SHA-1";
            case SHA_256 -> "SHA-256";
            case SHA_512 -> "SHA-512";
        };
    }

    // ========== DTOs ==========

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class VerificationResult {
        private boolean success;
        private String message;
        private VerificationStatus status;
    }

    @lombok.Data
    public static class IntegrityStatistics {
        private long totalFiles;
        private long pendingVerification;
        private long verified;
        private long failed;
        private long missing;
        private long duplicateGroups;
    }
}
