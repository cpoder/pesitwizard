package com.pesitwizard.server.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for browsing the server's file system.
 * Used by the admin UI to navigate and select files/directories.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/filesystem")
public class FileSystemController {

    private static final String DEFAULT_BASE_PATH = "/data";

    /**
     * List files and directories at the given path.
     * For security, only paths under /data are allowed.
     */
    @GetMapping("/browse")
    public ResponseEntity<?> browse(
            @RequestParam(defaultValue = "/data") String path) {
        try {
            Path targetPath = Paths.get(path).normalize();

            // Security: only allow browsing under /data
            if (!targetPath.startsWith(DEFAULT_BASE_PATH)) {
                log.warn("Attempted to browse outside allowed path: {}", path);
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Access denied: path must be under " + DEFAULT_BASE_PATH));
            }

            if (!Files.exists(targetPath)) {
                // If path doesn't exist, try to create it (for /data subdirectories)
                if (targetPath.startsWith(DEFAULT_BASE_PATH)) {
                    try {
                        Files.createDirectories(targetPath);
                        log.info("Created directory: {}", targetPath);
                    } catch (IOException e) {
                        return ResponseEntity.badRequest()
                                .body(new ErrorResponse("Directory does not exist and could not be created: " + path));
                    }
                } else {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Path does not exist: " + path));
                }
            }

            if (!Files.isDirectory(targetPath)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Path is not a directory: " + path));
            }

            List<FileEntry> entries = new ArrayList<>();

            // Add parent directory entry if not at root
            if (!targetPath.equals(Paths.get(DEFAULT_BASE_PATH))) {
                entries.add(FileEntry.builder()
                        .name("..")
                        .path(targetPath.getParent().toString())
                        .isDirectory(true)
                        .build());
            }

            try (Stream<Path> stream = Files.list(targetPath)) {
                stream.sorted((a, b) -> {
                    // Directories first, then by name
                    boolean aDir = Files.isDirectory(a);
                    boolean bDir = Files.isDirectory(b);
                    if (aDir != bDir)
                        return aDir ? -1 : 1;
                    return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                }).forEach(p -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                        entries.add(FileEntry.builder()
                                .name(p.getFileName().toString())
                                .path(p.toString())
                                .isDirectory(Files.isDirectory(p))
                                .size(attrs.isDirectory() ? null : attrs.size())
                                .lastModified(attrs.lastModifiedTime().toInstant())
                                .readable(Files.isReadable(p))
                                .writable(Files.isWritable(p))
                                .build());
                    } catch (IOException e) {
                        log.warn("Could not read attributes for {}: {}", p, e.getMessage());
                    }
                });
            }

            return ResponseEntity.ok(BrowseResponse.builder()
                    .currentPath(targetPath.toString())
                    .basePath(DEFAULT_BASE_PATH)
                    .entries(entries)
                    .build());

        } catch (IOException e) {
            log.error("Error browsing path {}: {}", path, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Error browsing path: " + e.getMessage()));
        }
    }

    /**
     * Create a new directory.
     */
    @GetMapping("/mkdir")
    public ResponseEntity<?> mkdir(@RequestParam String path) {
        try {
            Path targetPath = Paths.get(path).normalize();

            // Security: only allow creating under /data
            if (!targetPath.startsWith(DEFAULT_BASE_PATH)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Access denied: path must be under " + DEFAULT_BASE_PATH));
            }

            if (Files.exists(targetPath)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Path already exists: " + path));
            }

            Files.createDirectories(targetPath);
            log.info("Created directory: {}", targetPath);

            return ResponseEntity.ok(new SuccessResponse("Directory created: " + path));

        } catch (IOException e) {
            log.error("Error creating directory {}: {}", path, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Error creating directory: " + e.getMessage()));
        }
    }

    @Data
    @Builder
    public static class FileEntry {
        private String name;
        private String path;
        private boolean isDirectory;
        private Long size;
        private Instant lastModified;
        private boolean readable;
        private boolean writable;
    }

    @Data
    @Builder
    public static class BrowseResponse {
        private String currentPath;
        private String basePath;
        private List<FileEntry> entries;
    }

    @Data
    public static class ErrorResponse {
        private final String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }

    @Data
    public static class SuccessResponse {
        private final String message;

        public SuccessResponse(String message) {
            this.message = message;
        }
    }
}
