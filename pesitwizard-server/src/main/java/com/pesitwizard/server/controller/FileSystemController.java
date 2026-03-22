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
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for browsing the server's file system. Used by the admin UI to navigate and
 * select files/directories.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/filesystem")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public class FileSystemController {

    private final String basePath;

    public FileSystemController(
            @Value("${pesit.server.filesystem.base-path:${java.io.tmpdir}/pesitwizard}")
                    String basePath) {
        this.basePath = basePath;
    }

    /**
     * Validate and resolve a user-provided path to a safe path under the base directory. Returns
     * the validated path, or throws {@link SecurityException} if the path escapes the base
     * directory.
     */
    private Path validatePath(String userPath) throws IOException {
        Path realBasePath = Paths.get(basePath).toRealPath();
        Path candidate = realBasePath.resolve(userPath).normalize();

        // Pre-check: normalized path must be under base path
        if (!candidate.startsWith(realBasePath)) {
            throw new SecurityException("Path escapes base directory");
        }

        // Post-check: resolve symlinks if path exists, then re-verify
        if (Files.exists(candidate)) {
            candidate = candidate.toRealPath();
            if (!candidate.startsWith(realBasePath)) {
                throw new SecurityException("Symlink escapes base directory");
            }
        }

        return candidate;
    }

    /**
     * List files and directories at the given path. For security, only paths under the configured
     * base path are allowed.
     */
    @GetMapping("/browse")
    public ResponseEntity<?> browse(@RequestParam(required = false) String path) {
        // Use base path as default if no path specified
        String effectivePath = (path == null || path.isEmpty()) ? basePath : path;
        try {
            Path targetPath;
            try {
                targetPath = validatePath(effectivePath);
            } catch (SecurityException e) {
                log.warn("Attempted to browse outside allowed path");
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Access denied: path must be under " + basePath));
            }

            if (!Files.exists(targetPath)) {
                try {
                    Files.createDirectories(targetPath);
                    log.info("Created directory under base path");
                } catch (IOException e) {
                    return ResponseEntity.badRequest()
                            .body(
                                    new ErrorResponse(
                                            "Directory does not exist and could not be created"));
                }
            }

            if (!Files.isDirectory(targetPath)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Path is not a directory"));
            }

            List<FileEntry> entries = new ArrayList<>();

            // Add parent directory entry if not at root
            if (!targetPath.equals(Paths.get(basePath))) {
                Path parentPath = targetPath.getParent();
                if (parentPath != null) {
                    entries.add(
                            FileEntry.builder()
                                    .name("..")
                                    .path(parentPath.toString())
                                    .isDirectory(true)
                                    .build());
                }
            }

            try (Stream<Path> stream = Files.list(targetPath)) {
                stream.sorted(
                                (a, b) -> {
                                    // Directories first, then by name
                                    boolean aDir = Files.isDirectory(a);
                                    boolean bDir = Files.isDirectory(b);
                                    if (aDir != bDir) return aDir ? -1 : 1;
                                    return a.getFileName()
                                            .toString()
                                            .compareToIgnoreCase(b.getFileName().toString());
                                })
                        .forEach(
                                p -> {
                                    try {
                                        BasicFileAttributes attrs =
                                                Files.readAttributes(p, BasicFileAttributes.class);
                                        entries.add(
                                                FileEntry.builder()
                                                        .name(p.getFileName().toString())
                                                        .path(p.toString())
                                                        .isDirectory(Files.isDirectory(p))
                                                        .size(
                                                                attrs.isDirectory()
                                                                        ? null
                                                                        : attrs.size())
                                                        .lastModified(
                                                                attrs.lastModifiedTime()
                                                                        .toInstant())
                                                        .readable(Files.isReadable(p))
                                                        .writable(Files.isWritable(p))
                                                        .build());
                                    } catch (IOException e) {
                                        log.warn(
                                                "Could not read attributes for {}: {}",
                                                p,
                                                e.getMessage());
                                    }
                                });
            }

            return ResponseEntity.ok(
                    BrowseResponse.builder()
                            .currentPath(targetPath.toString())
                            .basePath(basePath)
                            .entries(entries)
                            .build());

        } catch (IOException e) {
            log.error("Error browsing path: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Error browsing path: " + e.getMessage()));
        }
    }

    /** Create a new directory. */
    @GetMapping("/mkdir")
    public ResponseEntity<?> mkdir(@RequestParam String path) {
        try {
            Path targetPath;
            try {
                targetPath = validatePath(path);
            } catch (SecurityException e) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Access denied: path must be under " + basePath));
            }

            if (Files.exists(targetPath)) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Path already exists"));
            }

            Files.createDirectories(targetPath);
            log.info("Created directory under base path");

            return ResponseEntity.ok(new SuccessResponse("Directory created successfully"));

        } catch (IOException e) {
            log.error("Error creating directory: {}", e.getMessage());
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
