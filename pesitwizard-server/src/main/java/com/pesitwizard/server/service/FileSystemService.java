package com.pesitwizard.server.service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for filesystem operations with proper validation and error handling. Centralizes path
 * normalization and permission checks.
 */
@Slf4j
@Service
public class FileSystemService {

    /** Result of a filesystem operation with detailed error information. */
    public record FileOperationResult(
            boolean success, String errorMessage, FileErrorType errorType, Path resolvedPath) {

        public static FileOperationResult success(Path path) {
            return new FileOperationResult(true, null, null, path);
        }

        public static FileOperationResult error(FileErrorType type, String message, Path path) {
            return new FileOperationResult(false, message, type, path);
        }
    }

    public enum FileErrorType {
        ACCESS_DENIED,
        PATH_NOT_FOUND,
        PATH_OUTSIDE_ALLOWED,
        INVALID_PATH,
        IO_ERROR,
        DIRECTORY_CREATION_FAILED
    }

    /**
     * Resolve and normalize a path, removing artifacts like "./" and ensuring absolute path.
     *
     * @param pathString The path string to resolve
     * @return Normalized absolute path
     */
    public Path normalizePath(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            return null;
        }

        // Convert to Path and normalize (removes . and .. where possible)
        Path path = Paths.get(pathString).normalize();

        // If relative, resolve against current working directory
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath().normalize();
        }

        return path;
    }

    /**
     * Resolve a path relative to a base directory with normalization. Ensures the result stays
     * within the base directory (security).
     *
     * @param basePath Base directory
     * @param relativePath Relative path to resolve
     * @return Result with normalized path or error
     */
    public FileOperationResult resolveSecurePath(Path basePath, String relativePath) {
        if (basePath == null) {
            return FileOperationResult.error(FileErrorType.INVALID_PATH, "Base path is null", null);
        }

        Path normalizedBase = basePath.normalize().toAbsolutePath();
        Path resolved;

        if (relativePath == null || relativePath.isBlank()) {
            resolved = normalizedBase;
        } else {
            resolved = normalizedBase.resolve(relativePath).normalize();
        }

        // Security check: ensure resolved path is within base path
        if (!resolved.startsWith(normalizedBase)) {
            log.warn(
                    "Path traversal attempt detected: {} trying to escape {}",
                    relativePath,
                    normalizedBase);
            return FileOperationResult.error(
                    FileErrorType.PATH_OUTSIDE_ALLOWED,
                    "Path would escape allowed directory",
                    resolved);
        }

        return FileOperationResult.success(resolved);
    }

    /**
     * Check if a directory is writable.
     *
     * @param directory Directory to check
     * @return true if writable
     */
    public boolean isDirectoryWritable(Path directory) {
        if (directory == null) {
            return false;
        }

        Path normalized = directory.normalize();

        // Check if exists and is directory
        if (Files.exists(normalized)) {
            return Files.isDirectory(normalized) && Files.isWritable(normalized);
        }

        // Check parent directory writability for creation
        Path parent = normalized.getParent();
        while (parent != null && !Files.exists(parent)) {
            parent = parent.getParent();
        }

        return parent != null && Files.isWritable(parent);
    }

    /**
     * Check if a directory is readable.
     *
     * @param directory Directory to check
     * @return true if readable
     */
    public boolean isDirectoryReadable(Path directory) {
        if (directory == null) {
            return false;
        }

        Path normalized = directory.normalize();
        return Files.exists(normalized)
                && Files.isDirectory(normalized)
                && Files.isReadable(normalized);
    }

    /**
     * Create directories with proper error handling.
     *
     * @param directory Directory to create
     * @return Result with success or detailed error
     */
    public FileOperationResult createDirectories(Path directory) {
        if (directory == null) {
            return FileOperationResult.error(
                    FileErrorType.INVALID_PATH, "Directory path is null", null);
        }

        Path normalized = directory.normalize().toAbsolutePath();

        // Already exists?
        if (Files.exists(normalized)) {
            if (Files.isDirectory(normalized)) {
                if (Files.isWritable(normalized)) {
                    return FileOperationResult.success(normalized);
                } else {
                    return FileOperationResult.error(
                            FileErrorType.ACCESS_DENIED,
                            "Directory exists but is not writable: " + normalized,
                            normalized);
                }
            } else {
                return FileOperationResult.error(
                        FileErrorType.INVALID_PATH,
                        "Path exists but is not a directory: " + normalized,
                        normalized);
            }
        }

        // Check if we can create it (parent must be writable)
        Path parent = normalized.getParent();
        while (parent != null && !Files.exists(parent)) {
            parent = parent.getParent();
        }

        if (parent == null) {
            return FileOperationResult.error(
                    FileErrorType.PATH_NOT_FOUND,
                    "No existing parent directory found for: " + normalized,
                    normalized);
        }

        if (!Files.isWritable(parent)) {
            return FileOperationResult.error(
                    FileErrorType.ACCESS_DENIED,
                    "Cannot create directory, parent '" + parent + "' is not writable",
                    normalized);
        }

        // Try to create
        try {
            Files.createDirectories(normalized);
            log.debug("Created directory: {}", normalized);
            return FileOperationResult.success(normalized);
        } catch (AccessDeniedException e) {
            log.error("Access denied creating directory '{}': {}", normalized, e.getMessage());
            return FileOperationResult.error(
                    FileErrorType.ACCESS_DENIED, "Access denied: " + e.getMessage(), normalized);
        } catch (IOException e) {
            log.error("IO error creating directory '{}': {}", normalized, e.getMessage());
            return FileOperationResult.error(
                    FileErrorType.IO_ERROR, "IO error: " + e.getMessage(), normalized);
        }
    }

    /**
     * Validate that a receive directory is properly configured and accessible. Call this at startup
     * or config validation time.
     *
     * @param directoryPath The receive directory path
     * @param description Description for logging (e.g., "Virtual file 'DATA'")
     * @return Result with validation status
     */
    public FileOperationResult validateReceiveDirectory(String directoryPath, String description) {
        if (directoryPath == null || directoryPath.isBlank()) {
            return FileOperationResult.error(
                    FileErrorType.INVALID_PATH,
                    description + ": receive directory not configured",
                    null);
        }

        Path normalized = normalizePath(directoryPath);

        // Try to create if doesn't exist
        FileOperationResult createResult = createDirectories(normalized);
        if (!createResult.success()) {
            log.error(
                    "{}: cannot access receive directory '{}' - {}",
                    description,
                    normalized,
                    createResult.errorMessage());
            return createResult;
        }

        // Verify we can write
        if (!Files.isWritable(normalized)) {
            String msg = description + ": directory exists but is not writable: " + normalized;
            log.error(msg);
            return FileOperationResult.error(FileErrorType.ACCESS_DENIED, msg, normalized);
        }

        log.info("{}: receive directory validated: {}", description, normalized);
        return FileOperationResult.success(normalized);
    }

    /**
     * Validate that a send directory is properly configured and accessible.
     *
     * @param directoryPath The send directory path
     * @param description Description for logging
     * @return Result with validation status
     */
    public FileOperationResult validateSendDirectory(String directoryPath, String description) {
        if (directoryPath == null || directoryPath.isBlank()) {
            return FileOperationResult.error(
                    FileErrorType.INVALID_PATH,
                    description + ": send directory not configured",
                    null);
        }

        Path normalized = normalizePath(directoryPath);

        if (!Files.exists(normalized)) {
            String msg = description + ": send directory does not exist: " + normalized;
            log.error(msg);
            return FileOperationResult.error(FileErrorType.PATH_NOT_FOUND, msg, normalized);
        }

        if (!Files.isDirectory(normalized)) {
            String msg = description + ": send path is not a directory: " + normalized;
            log.error(msg);
            return FileOperationResult.error(FileErrorType.INVALID_PATH, msg, normalized);
        }

        if (!Files.isReadable(normalized)) {
            String msg = description + ": send directory is not readable: " + normalized;
            log.error(msg);
            return FileOperationResult.error(FileErrorType.ACCESS_DENIED, msg, normalized);
        }

        log.info("{}: send directory validated: {}", description, normalized);
        return FileOperationResult.success(normalized);
    }

    /**
     * Get a human-readable permission string for a path.
     *
     * @param path Path to check
     * @return String like "rwx" or "r--" etc.
     */
    public String getPermissionString(Path path) {
        if (path == null || !Files.exists(path)) {
            return "---";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(Files.isReadable(path) ? "r" : "-");
        sb.append(Files.isWritable(path) ? "w" : "-");
        sb.append(Files.isExecutable(path) ? "x" : "-");
        return sb.toString();
    }
}
