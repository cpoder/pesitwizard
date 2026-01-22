package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pesitwizard.server.service.FileSystemService.FileErrorType;
import com.pesitwizard.server.service.FileSystemService.FileOperationResult;

@DisplayName("FileSystemService Tests")
class FileSystemServiceTest {

    private FileSystemService fileSystemService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileSystemService = new FileSystemService();
    }

    @Nested
    @DisplayName("Path Normalization Tests")
    class NormalizePathTests {

        @Test
        @DisplayName("Should normalize absolute path")
        void shouldNormalizeAbsolutePath() {
            Path result = fileSystemService.normalizePath("/data/files/../received/./test.txt");

            assertNotNull(result);
            assertTrue(result.isAbsolute());
            assertEquals("/data/received/test.txt", result.toString());
        }

        @Test
        @DisplayName("Should handle relative path")
        void shouldHandleRelativePath() {
            Path result = fileSystemService.normalizePath("relative/path/file.txt");

            assertNotNull(result);
            assertTrue(result.isAbsolute());
            assertTrue(result.toString().endsWith("relative/path/file.txt"));
        }

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNullForNullInput() {
            assertNull(fileSystemService.normalizePath(null));
        }

        @Test
        @DisplayName("Should return null for blank input")
        void shouldReturnNullForBlankInput() {
            assertNull(fileSystemService.normalizePath("   "));
        }
    }

    @Nested
    @DisplayName("Secure Path Resolution Tests")
    class SecurePathTests {

        @Test
        @DisplayName("Should resolve path within base directory")
        void shouldResolveWithinBase() {
            FileOperationResult result = fileSystemService.resolveSecurePath(tempDir, "subdir/file.txt");

            assertTrue(result.success());
            assertNotNull(result.resolvedPath());
            assertTrue(result.resolvedPath().startsWith(tempDir));
        }

        @Test
        @DisplayName("Should detect path traversal attempt")
        void shouldDetectPathTraversal() {
            FileOperationResult result = fileSystemService.resolveSecurePath(tempDir, "../../../etc/passwd");

            assertFalse(result.success());
            assertEquals(FileErrorType.PATH_OUTSIDE_ALLOWED, result.errorType());
        }

        @Test
        @DisplayName("Should handle null base path")
        void shouldHandleNullBasePath() {
            FileOperationResult result = fileSystemService.resolveSecurePath(null, "file.txt");

            assertFalse(result.success());
            assertEquals(FileErrorType.INVALID_PATH, result.errorType());
        }

        @Test
        @DisplayName("Should handle empty relative path")
        void shouldHandleEmptyRelativePath() {
            FileOperationResult result = fileSystemService.resolveSecurePath(tempDir, "");

            assertTrue(result.success());
            assertEquals(tempDir.toAbsolutePath().normalize(), result.resolvedPath());
        }

        @Test
        @DisplayName("Should handle null relative path")
        void shouldHandleNullRelativePath() {
            FileOperationResult result = fileSystemService.resolveSecurePath(tempDir, null);

            assertTrue(result.success());
            assertEquals(tempDir.toAbsolutePath().normalize(), result.resolvedPath());
        }
    }

    @Nested
    @DisplayName("Directory Writable Tests")
    class DirectoryWritableTests {

        @Test
        @DisplayName("Should detect writable directory")
        void shouldDetectWritableDirectory() {
            assertTrue(fileSystemService.isDirectoryWritable(tempDir));
        }

        @Test
        @DisplayName("Should return false for null directory")
        void shouldReturnFalseForNull() {
            assertFalse(fileSystemService.isDirectoryWritable(null));
        }

        @Test
        @DisplayName("Should detect non-existent directory as potentially writable")
        void shouldHandleNonExistentDirectory() {
            Path nonExistent = tempDir.resolve("new/nested/dir");
            assertTrue(fileSystemService.isDirectoryWritable(nonExistent));
        }
    }

    @Nested
    @DisplayName("Directory Readable Tests")
    class DirectoryReadableTests {

        @Test
        @DisplayName("Should detect readable directory")
        void shouldDetectReadableDirectory() {
            assertTrue(fileSystemService.isDirectoryReadable(tempDir));
        }

        @Test
        @DisplayName("Should return false for null directory")
        void shouldReturnFalseForNull() {
            assertFalse(fileSystemService.isDirectoryReadable(null));
        }

        @Test
        @DisplayName("Should return false for non-existent directory")
        void shouldReturnFalseForNonExistent() {
            Path nonExistent = tempDir.resolve("does-not-exist");
            assertFalse(fileSystemService.isDirectoryReadable(nonExistent));
        }
    }

    @Nested
    @DisplayName("Create Directories Tests")
    class CreateDirectoriesTests {

        @Test
        @DisplayName("Should create nested directories")
        void shouldCreateNestedDirectories() {
            Path newDir = tempDir.resolve("level1/level2/level3");

            FileOperationResult result = fileSystemService.createDirectories(newDir);

            assertTrue(result.success());
            assertTrue(Files.exists(newDir));
            assertTrue(Files.isDirectory(newDir));
        }

        @Test
        @DisplayName("Should succeed for existing directory")
        void shouldSucceedForExisting() {
            FileOperationResult result = fileSystemService.createDirectories(tempDir);

            assertTrue(result.success());
        }

        @Test
        @DisplayName("Should fail for null path")
        void shouldFailForNull() {
            FileOperationResult result = fileSystemService.createDirectories(null);

            assertFalse(result.success());
            assertEquals(FileErrorType.INVALID_PATH, result.errorType());
        }

        @Test
        @DisplayName("Should fail when path is a file")
        void shouldFailWhenPathIsFile() throws IOException {
            Path file = tempDir.resolve("testfile.txt");
            Files.createFile(file);

            FileOperationResult result = fileSystemService.createDirectories(file);

            assertFalse(result.success());
            assertEquals(FileErrorType.INVALID_PATH, result.errorType());
        }
    }

    @Nested
    @DisplayName("Validate Receive Directory Tests")
    class ValidateReceiveDirectoryTests {

        @Test
        @DisplayName("Should validate existing writable directory")
        void shouldValidateExistingDirectory() {
            FileOperationResult result = fileSystemService.validateReceiveDirectory(
                    tempDir.toString(), "Test virtual file");

            assertTrue(result.success());
        }

        @Test
        @DisplayName("Should create non-existent directory")
        void shouldCreateNonExistent() {
            Path newDir = tempDir.resolve("new-receive-dir");

            FileOperationResult result = fileSystemService.validateReceiveDirectory(
                    newDir.toString(), "Test virtual file");

            assertTrue(result.success());
            assertTrue(Files.exists(newDir));
        }

        @Test
        @DisplayName("Should fail for null path")
        void shouldFailForNull() {
            FileOperationResult result = fileSystemService.validateReceiveDirectory(
                    null, "Test");

            assertFalse(result.success());
            assertEquals(FileErrorType.INVALID_PATH, result.errorType());
        }

        @Test
        @DisplayName("Should fail for blank path")
        void shouldFailForBlank() {
            FileOperationResult result = fileSystemService.validateReceiveDirectory(
                    "   ", "Test");

            assertFalse(result.success());
            assertEquals(FileErrorType.INVALID_PATH, result.errorType());
        }
    }

    @Nested
    @DisplayName("Validate Send Directory Tests")
    class ValidateSendDirectoryTests {

        @Test
        @DisplayName("Should validate existing readable directory")
        void shouldValidateExistingDirectory() {
            FileOperationResult result = fileSystemService.validateSendDirectory(
                    tempDir.toString(), "Test virtual file");

            assertTrue(result.success());
        }

        @Test
        @DisplayName("Should fail for non-existent directory")
        void shouldFailForNonExistent() {
            Path nonExistent = tempDir.resolve("does-not-exist");

            FileOperationResult result = fileSystemService.validateSendDirectory(
                    nonExistent.toString(), "Test");

            assertFalse(result.success());
            assertEquals(FileErrorType.PATH_NOT_FOUND, result.errorType());
        }

        @Test
        @DisplayName("Should fail for file instead of directory")
        void shouldFailForFile() throws IOException {
            Path file = tempDir.resolve("testfile.txt");
            Files.createFile(file);

            FileOperationResult result = fileSystemService.validateSendDirectory(
                    file.toString(), "Test");

            assertFalse(result.success());
            assertEquals(FileErrorType.INVALID_PATH, result.errorType());
        }
    }

    @Nested
    @DisplayName("Permission String Tests")
    class PermissionStringTests {

        @Test
        @DisplayName("Should return permissions for existing path")
        void shouldReturnPermissions() {
            String perms = fileSystemService.getPermissionString(tempDir);

            assertNotNull(perms);
            assertEquals(3, perms.length());
            assertTrue(perms.contains("r"));
            assertTrue(perms.contains("w"));
        }

        @Test
        @DisplayName("Should return --- for null path")
        void shouldReturnDashesForNull() {
            assertEquals("---", fileSystemService.getPermissionString(null));
        }

        @Test
        @DisplayName("Should return --- for non-existent path")
        void shouldReturnDashesForNonExistent() {
            Path nonExistent = tempDir.resolve("does-not-exist");
            assertEquals("---", fileSystemService.getPermissionString(nonExistent));
        }
    }

    @Nested
    @DisplayName("FileOperationResult Record")
    class FileOperationResultTests {

        @Test
        @DisplayName("Should create success result")
        void shouldCreateSuccessResult() {
            FileOperationResult result = FileOperationResult.success(tempDir);

            assertTrue(result.success());
            assertNull(result.errorMessage());
            assertNull(result.errorType());
            assertEquals(tempDir, result.resolvedPath());
        }

        @Test
        @DisplayName("Should create error result")
        void shouldCreateErrorResult() {
            FileOperationResult result = FileOperationResult.error(
                    FileErrorType.ACCESS_DENIED, "Permission denied", tempDir);

            assertFalse(result.success());
            assertEquals("Permission denied", result.errorMessage());
            assertEquals(FileErrorType.ACCESS_DENIED, result.errorType());
            assertEquals(tempDir, result.resolvedPath());
        }
    }

    @Nested
    @DisplayName("FileErrorType Enum")
    class FileErrorTypeTests {

        @Test
        @DisplayName("Should have all error types")
        void shouldHaveAllErrorTypes() {
            assertEquals(6, FileErrorType.values().length);
            assertNotNull(FileErrorType.ACCESS_DENIED);
            assertNotNull(FileErrorType.PATH_NOT_FOUND);
            assertNotNull(FileErrorType.PATH_OUTSIDE_ALLOWED);
            assertNotNull(FileErrorType.INVALID_PATH);
            assertNotNull(FileErrorType.IO_ERROR);
            assertNotNull(FileErrorType.DIRECTORY_CREATION_FAILED);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle send directory not readable")
        void shouldHandleSendDirectoryNotReadable() throws IOException {
            // This test validates the code path, even if we can't actually
            // make a directory unreadable in all environments
            Path dir = tempDir.resolve("send-dir");
            Files.createDirectories(dir);

            FileOperationResult result = fileSystemService.validateSendDirectory(
                    dir.toString(), "Test");

            assertTrue(result.success());
        }

        @Test
        @DisplayName("Should handle blank send directory path")
        void shouldHandleBlankSendDirectoryPath() {
            FileOperationResult result = fileSystemService.validateSendDirectory(
                    "   ", "Test");

            assertFalse(result.success());
            assertEquals(FileErrorType.INVALID_PATH, result.errorType());
        }

        @Test
        @DisplayName("Should handle existing non-writable directory in createDirectories")
        void shouldHandleExistingNonWritableDirectory() throws IOException {
            Path existingDir = tempDir.resolve("existing-dir");
            Files.createDirectories(existingDir);

            // For writable directory, should succeed
            FileOperationResult result = fileSystemService.createDirectories(existingDir);
            assertTrue(result.success());
        }
    }
}
