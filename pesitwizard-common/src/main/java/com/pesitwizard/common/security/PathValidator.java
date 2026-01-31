package com.pesitwizard.common.security;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Utility class for validating and sanitizing file paths.
 * Provides protection against path traversal attacks and other security issues.
 */
public final class PathValidator {

    private PathValidator() {
        // Utility class
    }

    /**
     * Pattern to detect path traversal attempts in the raw input.
     * Matches:
     * - "../" or "..\\" at any position
     * - Null bytes (common attack vector)
     * - Excessive dots
     */
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "(?:^|[/\\\\])\\.\\.(?:[/\\\\]|$)|\\.\\.\\.|\\x00");

    /**
     * Pattern for valid filename characters.
     * Allows alphanumeric, dots, dashes, underscores, and spaces.
     * Rejects special characters that could be dangerous.
     */
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._\\- ]+$");

    /**
     * Maximum filename length (most filesystems have 255 byte limit).
     */
    public static final int MAX_FILENAME_LENGTH = 255;

    /**
     * Maximum path length (common limit on Linux).
     */
    public static final int MAX_PATH_LENGTH = 4096;

    /**
     * Validates that a path is safe and contained within a base directory.
     *
     * @param userPath the user-provided path
     * @param basePath the allowed base directory
     * @return the normalized, validated path
     * @throws SecurityException if the path is invalid or attempts traversal
     */
    public static Path validatePath(String userPath, String basePath) {
        if (userPath == null || userPath.isBlank()) {
            throw new SecurityException("Path cannot be null or blank");
        }
        if (basePath == null || basePath.isBlank()) {
            throw new SecurityException("Base path cannot be null or blank");
        }

        // Check for obvious traversal attempts before normalization
        if (PATH_TRAVERSAL_PATTERN.matcher(userPath).find()) {
            throw new SecurityException("Path contains illegal traversal sequence");
        }

        // Check path length
        if (userPath.length() > MAX_PATH_LENGTH) {
            throw new SecurityException("Path exceeds maximum length of " + MAX_PATH_LENGTH);
        }

        try {
            Path base = Paths.get(basePath).toRealPath();
            Path target = base.resolve(userPath).normalize();

            // Resolve to real path to handle symlinks
            if (target.toFile().exists()) {
                target = target.toRealPath();
            }

            // Verify the resolved path is still under base
            if (!target.startsWith(base)) {
                throw new SecurityException("Path escapes base directory");
            }

            return target;
        } catch (IOException e) {
            throw new SecurityException("Invalid path: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that a path is safe for use, without checking against a base directory.
     * Use this for paths that will be validated by other means (e.g., storage connectors).
     *
     * @param path the path to validate
     * @return the normalized path
     * @throws SecurityException if the path is invalid
     */
    public static Path validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new SecurityException("Path cannot be null or blank");
        }

        if (PATH_TRAVERSAL_PATTERN.matcher(path).find()) {
            throw new SecurityException("Path contains illegal traversal sequence");
        }

        if (path.length() > MAX_PATH_LENGTH) {
            throw new SecurityException("Path exceeds maximum length");
        }

        return Paths.get(path).normalize();
    }

    /**
     * Validates a filename (without directory components).
     *
     * @param filename the filename to validate
     * @return the validated filename
     * @throws SecurityException if the filename is invalid
     */
    public static String validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new SecurityException("Filename cannot be null or blank");
        }

        // Check for null bytes before calling Paths.get (which throws InvalidPathException)
        if (filename.contains("\0")) {
            throw new SecurityException("Filename contains null byte");
        }

        // Remove any directory components
        String name = Paths.get(filename).getFileName().toString();

        if (name.length() > MAX_FILENAME_LENGTH) {
            throw new SecurityException("Filename exceeds maximum length of " + MAX_FILENAME_LENGTH);
        }

        return name;
    }

    /**
     * Checks if a filename contains only safe characters.
     * This is a strict check that rejects special characters.
     *
     * @param filename the filename to check
     * @return true if the filename is safe
     */
    public static boolean isSafeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        if (filename.length() > MAX_FILENAME_LENGTH) {
            return false;
        }
        return SAFE_FILENAME_PATTERN.matcher(filename).matches();
    }

    /**
     * Sanitizes a filename by removing or replacing unsafe characters.
     *
     * @param filename the filename to sanitize
     * @return a sanitized version of the filename
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed";
        }

        // Get just the filename part
        String name = Paths.get(filename).getFileName().toString();

        // Remove null bytes
        name = name.replace("\0", "");

        // Replace unsafe characters with underscore
        name = name.replaceAll("[^a-zA-Z0-9._\\- ]", "_");

        // Remove leading dots (hidden files)
        while (name.startsWith(".")) {
            name = name.substring(1);
        }

        // Truncate if too long
        if (name.length() > MAX_FILENAME_LENGTH) {
            String ext = "";
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < name.length() - 1) {
                ext = name.substring(dotIndex);
                if (ext.length() > 10) {
                    ext = ""; // Extension too long, ignore it
                }
            }
            name = name.substring(0, MAX_FILENAME_LENGTH - ext.length()) + ext;
        }

        if (name.isBlank()) {
            return "unnamed";
        }

        return name;
    }

    /**
     * Checks if a path attempts to traverse above the base directory.
     *
     * @param path the path to check
     * @return true if the path contains traversal attempts
     */
    public static boolean containsTraversal(String path) {
        if (path == null) {
            return false;
        }
        return PATH_TRAVERSAL_PATTERN.matcher(path).find();
    }
}
