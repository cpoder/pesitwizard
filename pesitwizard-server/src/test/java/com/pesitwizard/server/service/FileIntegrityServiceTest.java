package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.server.entity.FileChecksum;
import com.pesitwizard.server.entity.FileChecksum.HashAlgorithm;
import com.pesitwizard.server.entity.FileChecksum.TransferDirection;
import com.pesitwizard.server.entity.FileChecksum.VerificationStatus;
import com.pesitwizard.server.repository.FileChecksumRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileIntegrityService Tests")
class FileIntegrityServiceTest {

    @Mock private FileChecksumRepository checksumRepository;

    @InjectMocks private FileIntegrityService service;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "defaultAlgorithm", "SHA-256");
        ReflectionTestUtils.setField(service, "verificationIntervalDays", 7);
        ReflectionTestUtils.setField(service, "bufferSize", 8192);
    }

    @Nested
    @DisplayName("Checksum Computation Tests")
    class ChecksumComputationTests {

        @Test
        @DisplayName("Should compute SHA-256 checksum for file")
        void shouldComputeChecksumForFile() throws IOException {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "Hello, World!");

            String checksum = service.computeChecksum(testFile);

            assertNotNull(checksum);
            assertEquals(64, checksum.length()); // SHA-256 produces 64 hex chars
        }

        @Test
        @DisplayName("Should compute same checksum for identical content")
        void shouldComputeSameChecksumForIdenticalContent() throws IOException {
            Path file1 = tempDir.resolve("file1.txt");
            Path file2 = tempDir.resolve("file2.txt");
            Files.writeString(file1, "Same content");
            Files.writeString(file2, "Same content");

            String checksum1 = service.computeChecksum(file1);
            String checksum2 = service.computeChecksum(file2);

            assertEquals(checksum1, checksum2);
        }

        @Test
        @DisplayName("Should compute different checksum for different content")
        void shouldComputeDifferentChecksumForDifferentContent() throws IOException {
            Path file1 = tempDir.resolve("file1.txt");
            Path file2 = tempDir.resolve("file2.txt");
            Files.writeString(file1, "Content A");
            Files.writeString(file2, "Content B");

            String checksum1 = service.computeChecksum(file1);
            String checksum2 = service.computeChecksum(file2);

            assertNotEquals(checksum1, checksum2);
        }

        @Test
        @DisplayName("Should compute checksum for byte array")
        void shouldComputeChecksumForByteArray() {
            byte[] data = "Test data".getBytes();

            String checksum = service.computeChecksum(data);

            assertNotNull(checksum);
            assertEquals(64, checksum.length());
        }

        @Test
        @DisplayName("Should compute checksum with specific algorithm")
        void shouldComputeChecksumWithSpecificAlgorithm() throws IOException {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "Test");

            String sha256 = service.computeChecksum(testFile, HashAlgorithm.SHA_256);
            String sha512 = service.computeChecksum(testFile, HashAlgorithm.SHA_512);

            assertEquals(64, sha256.length());
            assertEquals(128, sha512.length());
        }
    }

    @Nested
    @DisplayName("Checksum Storage Tests")
    class ChecksumStorageTests {

        @Test
        @DisplayName("Should store checksum")
        void shouldStoreChecksum() {
            when(checksumRepository.findByChecksumHash(anyString())).thenReturn(List.of());
            when(checksumRepository.save(any(FileChecksum.class)))
                    .thenAnswer(
                            inv -> {
                                FileChecksum fc = inv.getArgument(0);
                                fc.setId(1L);
                                return fc;
                            });

            FileChecksum result =
                    service.storeChecksum(
                            "test.txt",
                            1024L,
                            "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                            "TXN001",
                            "PARTNER_A",
                            "SERVER_1",
                            TransferDirection.INBOUND,
                            "/data/test.txt");

            assertNotNull(result);
            assertEquals("test.txt", result.getFilename());
            assertEquals(1024L, result.getFileSize());
            assertEquals(
                    "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                    result.getChecksumHash());
            verify(checksumRepository).save(any(FileChecksum.class));
        }

        @Test
        @DisplayName("Should detect duplicate on store")
        void shouldDetectDuplicateOnStore() {
            FileChecksum existing =
                    FileChecksum.builder()
                            .id(1L)
                            .checksumHash(
                                    "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
                            .duplicateCount(0)
                            .build();

            when(checksumRepository.findByChecksumHash(
                            "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"))
                    .thenReturn(List.of(existing));
            when(checksumRepository.save(any(FileChecksum.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            FileChecksum result =
                    service.storeChecksum(
                            "test2.txt",
                            1024L,
                            "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                            "TXN002",
                            "PARTNER_A",
                            "SERVER_1",
                            TransferDirection.INBOUND,
                            "/data/test2.txt");

            assertEquals(1, result.getDuplicateCount());
            verify(checksumRepository, times(2)).save(any(FileChecksum.class));
        }

        @Test
        @DisplayName("Should compute and store checksum")
        void shouldComputeAndStore() throws IOException {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "Test content");

            when(checksumRepository.findByChecksumHash(anyString())).thenReturn(List.of());
            when(checksumRepository.save(any(FileChecksum.class)))
                    .thenAnswer(
                            inv -> {
                                FileChecksum fc = inv.getArgument(0);
                                fc.setId(1L);
                                return fc;
                            });

            FileChecksum result =
                    service.computeAndStore(
                            testFile, "TXN001", "PARTNER_A", "SERVER_1", TransferDirection.INBOUND);

            assertNotNull(result);
            assertEquals("test.txt", result.getFilename());
            assertNotNull(result.getChecksumHash());
        }
    }

    @Nested
    @DisplayName("Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("Should verify file successfully")
        void shouldVerifyFileSuccessfully() throws IOException {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "Test content");
            String expectedHash = service.computeChecksum(testFile);

            FileChecksum checksum =
                    FileChecksum.builder()
                            .id(1L)
                            .checksumHash(expectedHash)
                            .algorithm(HashAlgorithm.SHA_256)
                            .localPath(testFile.toString())
                            .build();

            when(checksumRepository.findById(1L)).thenReturn(Optional.of(checksum));
            when(checksumRepository.save(any(FileChecksum.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            FileIntegrityService.VerificationResult result = service.verifyFile(1L);

            assertTrue(result.isSuccess());
            assertEquals(VerificationStatus.VERIFIED, result.getStatus());
        }

        @Test
        @DisplayName("Should fail verification for modified file")
        void shouldFailVerificationForModifiedFile() throws IOException {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "Original content");

            FileChecksum checksum =
                    FileChecksum.builder()
                            .id(1L)
                            .checksumHash("wrong_hash_value_that_does_not_match")
                            .algorithm(HashAlgorithm.SHA_256)
                            .localPath(testFile.toString())
                            .filename("test.txt")
                            .build();

            when(checksumRepository.findById(1L)).thenReturn(Optional.of(checksum));
            when(checksumRepository.save(any(FileChecksum.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            FileIntegrityService.VerificationResult result = service.verifyFile(1L);

            assertFalse(result.isSuccess());
            assertEquals(VerificationStatus.FAILED, result.getStatus());
        }

        @Test
        @DisplayName("Should report missing file")
        void shouldReportMissingFile() {
            FileChecksum checksum =
                    FileChecksum.builder()
                            .id(1L)
                            .checksumHash("abc123")
                            .localPath("/nonexistent/path/file.txt")
                            .build();

            when(checksumRepository.findById(1L)).thenReturn(Optional.of(checksum));
            when(checksumRepository.save(any(FileChecksum.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            FileIntegrityService.VerificationResult result = service.verifyFile(1L);

            assertFalse(result.isSuccess());
            assertEquals(VerificationStatus.MISSING, result.getStatus());
        }

        @Test
        @DisplayName("Should handle null local path")
        void shouldHandleNullLocalPath() {
            FileChecksum checksum =
                    FileChecksum.builder().id(1L).checksumHash("abc123").localPath(null).build();

            when(checksumRepository.findById(1L)).thenReturn(Optional.of(checksum));

            FileIntegrityService.VerificationResult result = service.verifyFile(1L);

            assertFalse(result.isSuccess());
            assertEquals(VerificationStatus.FAILED, result.getStatus());
        }

        @Test
        @DisplayName("Should throw for non-existent checksum ID")
        void shouldThrowForNonExistentChecksumId() {
            when(checksumRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> service.verifyFile(99L));
        }

        @Test
        @DisplayName("Should verify pending files")
        void shouldVerifyPendingFiles() throws IOException {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "Content");
            String hash = service.computeChecksum(testFile);

            FileChecksum pending =
                    FileChecksum.builder()
                            .id(1L)
                            .checksumHash(hash)
                            .algorithm(HashAlgorithm.SHA_256)
                            .localPath(testFile.toString())
                            .status(VerificationStatus.PENDING)
                            .build();

            when(checksumRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.PENDING))
                    .thenReturn(List.of(pending));
            when(checksumRepository.save(any(FileChecksum.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            int verified = service.verifyPendingFiles();

            assertEquals(1, verified);
        }
    }

    @Nested
    @DisplayName("Duplicate Detection Tests")
    class DuplicateDetectionTests {

        @Test
        @DisplayName("Should detect duplicate")
        void shouldDetectDuplicate() {
            when(checksumRepository.existsByChecksumHashAndAlgorithm(
                            "abc123", HashAlgorithm.SHA_256))
                    .thenReturn(true);

            assertTrue(service.isDuplicate("abc123"));
        }

        @Test
        @DisplayName("Should not detect non-duplicate")
        void shouldNotDetectNonDuplicate() {
            when(checksumRepository.existsByChecksumHashAndAlgorithm(
                            "xyz789", HashAlgorithm.SHA_256))
                    .thenReturn(false);

            assertFalse(service.isDuplicate("xyz789"));
        }

        @Test
        @DisplayName("Should get duplicates")
        void shouldGetDuplicates() {
            List<FileChecksum> duplicates =
                    List.of(
                            FileChecksum.builder().id(1L).checksumHash("abc").build(),
                            FileChecksum.builder().id(2L).checksumHash("abc").build());
            when(checksumRepository.findByChecksumHash("abc")).thenReturn(duplicates);

            List<FileChecksum> result = service.getDuplicates("abc");

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should get all duplicates")
        void shouldGetAllDuplicates() {
            when(checksumRepository.findDuplicates()).thenReturn(List.of());

            List<FileChecksum> result = service.getAllDuplicates();

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should get most duplicated")
        void shouldGetMostDuplicated() {
            when(checksumRepository.findByDuplicateCountGreaterThanOrderByDuplicateCountDesc(5))
                    .thenReturn(List.of());

            List<FileChecksum> result = service.getMostDuplicated(5);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Should get checksum by ID")
        void shouldGetChecksumById() {
            FileChecksum checksum = FileChecksum.builder().id(1L).build();
            when(checksumRepository.findById(1L)).thenReturn(Optional.of(checksum));

            Optional<FileChecksum> result = service.getChecksum(1L);

            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Should get checksum by transfer ID")
        void shouldGetChecksumByTransferId() {
            FileChecksum checksum = FileChecksum.builder().id(1L).transferId("TXN001").build();
            when(checksumRepository.findByTransferId("TXN001")).thenReturn(Optional.of(checksum));

            Optional<FileChecksum> result = service.getChecksumByTransferId("TXN001");

            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Should get checksums by partner")
        void shouldGetChecksumsByPartner() {
            Page<FileChecksum> page = new PageImpl<>(List.of());
            when(checksumRepository.findByPartnerIdOrderByCreatedAtDesc(
                            eq("PARTNER_A"), any(PageRequest.class)))
                    .thenReturn(page);

            Page<FileChecksum> result = service.getChecksumsByPartner("PARTNER_A", 0, 10);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should get checksums by status")
        void shouldGetChecksumsByStatus() {
            Page<FileChecksum> page = new PageImpl<>(List.of());
            when(checksumRepository.findByStatusOrderByCreatedAtDesc(
                            eq(VerificationStatus.PENDING), any(PageRequest.class)))
                    .thenReturn(page);

            Page<FileChecksum> result =
                    service.getChecksumsByStatus(VerificationStatus.PENDING, 0, 10);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should search by filename")
        void shouldSearchByFilename() {
            Page<FileChecksum> page = new PageImpl<>(List.of());
            when(checksumRepository.searchByFilename(eq("test%"), any(PageRequest.class)))
                    .thenReturn(page);

            Page<FileChecksum> result = service.searchByFilename("test%", 0, 10);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should get statistics")
        void shouldGetStatistics() {
            when(checksumRepository.count()).thenReturn(100L);
            when(checksumRepository.countByStatus(VerificationStatus.PENDING)).thenReturn(10L);
            when(checksumRepository.countByStatus(VerificationStatus.VERIFIED)).thenReturn(80L);
            when(checksumRepository.countByStatus(VerificationStatus.FAILED)).thenReturn(5L);
            when(checksumRepository.countByStatus(VerificationStatus.MISSING)).thenReturn(5L);
            when(checksumRepository.countDistinctDuplicates()).thenReturn(15L);

            FileIntegrityService.IntegrityStatistics stats = service.getStatistics();

            assertEquals(100L, stats.getTotalFiles());
            assertEquals(10L, stats.getPendingVerification());
            assertEquals(80L, stats.getVerified());
            assertEquals(5L, stats.getFailed());
            assertEquals(5L, stats.getMissing());
            assertEquals(15L, stats.getDuplicateGroups());
        }
    }
}
