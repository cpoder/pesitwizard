package com.pesitwizard.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackupService {
    private final BackupConfig config;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // AES-256-GCM encryption for backup files
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final byte[] BACKUP_SALT = "PeSITWizardBackup".getBytes(StandardCharsets.UTF_8);
    private static final String ENCRYPTED_SUFFIX = ".enc";

    private final SecretKey encryptionKey;

    public BackupService(BackupConfig config) {
        this.config = config;
        this.encryptionKey = deriveEncryptionKey(config.getEncryptionKey());
    }

    public BackupResult createBackup(String description) {
        BackupResult r = new BackupResult();
        r.setTimestamp(Instant.now());
        r.setDescription(description != null ? description : "Manual backup");
        try {
            Path dir = ensureBackupDirectory();
            String name = config.getBackupPrefix() + "_" + LocalDateTime.now().format(TS);
            r.setBackupName(name);
            DatabaseType type = detectDatabaseType();
            Path file;
            
            if (type == DatabaseType.H2) {
                file = dir.resolve(name + ".zip");
                createH2Backup(file);
                r.setBackupType("H2");
            } else if (type == DatabaseType.POSTGRESQL) {
                file = dir.resolve(name + ".dump");
                int c = runPgDump(file);
                if (c != 0) { r.setSuccess(false); r.setMessage("pg_dump failed: " + c); return r; }
                r.setBackupType("POSTGRESQL");
            } else {
                file = dir.resolve(name + ".meta");
                Files.writeString(file, "timestamp=" + Instant.now());
                r.setBackupType("METADATA");
            }
            
            // Encrypt backup file if encryption key is configured
            if (encryptionKey != null) {
                Path encryptedFile = Path.of(file + ENCRYPTED_SUFFIX);
                encryptFile(file, encryptedFile);
                Files.delete(file); // Remove unencrypted backup
                file = encryptedFile;
                r.setEncrypted(true);
                log.info("Backup encrypted: {}", encryptedFile.getFileName());
            }

            // Set restrictive file permissions (owner-only)
            setRestrictivePermissions(file);

            Files.writeString(Path.of(file + ".meta"), r.getDescription());
            r.setBackupPath(file.toString());
            r.setSizeBytes(Files.size(file));
            r.setSuccess(true);
            log.info("Backup: {} ({})", name, r.getBackupType());
            cleanupOldBackups();
        } catch (java.io.IOException | java.security.GeneralSecurityException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Backup failed", e);
            r.setSuccess(false);
            r.setMessage(e.getMessage());
        }
        return r;
    }

    public List<BackupInfo> listBackups() {
        List<BackupInfo> list = new ArrayList<>();
        Path dir = Path.of(config.getBackupDirectory());
        if (!Files.exists(dir)) return list;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> isBackupFile(p.toString()))
             .sorted(Comparator.comparing(Path::getFileName).reversed())
             .forEach(p -> {
                 try {
                     String fname = p.toString();
                     BackupInfo i = new BackupInfo();
                     i.setFilename(p.getFileName().toString());
                     i.setSizeBytes(Files.size(p));
                     i.setCreatedAt(Files.getLastModifiedTime(p).toInstant());
                     boolean encrypted = fname.endsWith(ENCRYPTED_SUFFIX);
                     String baseName = encrypted ? fname.substring(0, fname.length() - ENCRYPTED_SUFFIX.length()) : fname;
                     i.setType(baseName.endsWith(".zip") ? "H2" : "POSTGRESQL");
                     i.setEncrypted(encrypted);
                     Path m = Path.of(p + ".meta");
                     if (Files.exists(m)) i.setDescription(Files.readString(m).trim());
                     list.add(i);
                 } catch (IOException ignored) {}
             });
        } catch (IOException e) { log.error("List failed", e); }
        return list;
    }

    public RestoreResult restoreBackup(String filename) {
        validateBackupFilename(filename);
        RestoreResult r = new RestoreResult();
        r.setBackupName(filename);
        r.setTimestamp(Instant.now());
        Path file = Path.of(config.getBackupDirectory(), filename);
        if (!Files.exists(file)) { r.setSuccess(false); r.setMessage("Not found"); return r; }
        try {
            // Decrypt if encrypted
            Path restoreFile = file;
            boolean needsCleanup = false;
            if (filename.endsWith(ENCRYPTED_SUFFIX)) {
                if (encryptionKey == null) {
                    r.setSuccess(false);
                    r.setMessage("Backup is encrypted but no encryption key configured");
                    return r;
                }
                restoreFile = Path.of(file + ".decrypted");
                decryptFile(file, restoreFile);
                needsCleanup = true;
                // Determine actual backup type from the name without .enc
                filename = filename.substring(0, filename.length() - ENCRYPTED_SUFFIX.length());
            }

            try {
                if (filename.endsWith(".zip")) {
                    restoreH2Backup(restoreFile);
                    r.setSuccess(true);
                    r.setMessage("H2 restored - restart required");
                } else if (filename.endsWith(".dump")) {
                    int c = runPgRestore(restoreFile);
                    r.setSuccess(c == 0);
                    r.setMessage(c == 0 ? "PostgreSQL restored" : "pg_restore failed: " + c);
                } else {
                    r.setSuccess(false);
                    r.setMessage("Unsupported format");
                }
            } finally {
                if (needsCleanup) {
                    Files.deleteIfExists(restoreFile);
                }
            }
            if (r.isSuccess()) log.info("Restored: {}", filename);
        } catch (java.io.IOException | java.security.GeneralSecurityException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            r.setSuccess(false);
            r.setMessage(e.getMessage());
            log.error("Restore failed", e);
        }
        return r;
    }

    public boolean deleteBackup(String filename) {
        validateBackupFilename(filename);
        try {
            Path f = Path.of(config.getBackupDirectory(), filename);
            Files.deleteIfExists(Path.of(f + ".meta"));
            boolean d = Files.deleteIfExists(f);
            if (d) log.info("Deleted: {}", filename);
            return d;
        } catch (IOException e) { return false; }
    }

    public int cleanupOldBackups() {
        Path dir = Path.of(config.getBackupDirectory());
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> all = s.filter(p -> isBackupFile(p.toString()))
                .sorted(Comparator.comparing((Path p) -> {
                    try { return Files.getLastModifiedTime(p).toInstant(); }
                    catch (IOException e) { return Instant.MIN; }
                }).reversed()).toList();
            int del = 0;
            Instant cutoff = Instant.now().minusSeconds(config.getRetentionDays() * 86400L);
            for (int i = 0; i < all.size(); i++) {
                Path p = all.get(i);
                boolean rm = i >= config.getMaxBackups();
                try { if (Files.getLastModifiedTime(p).toInstant().isBefore(cutoff)) rm = true; }
                catch (IOException ignored) {}
                if (rm) {
                    try { Files.deleteIfExists(Path.of(p + ".meta")); Files.delete(p); del++; }
                    catch (IOException ignored) {}
                }
            }
            if (del > 0) log.info("Cleaned {} backups", del);
            return del;
        } catch (IOException e) { return 0; }
    }

    /**
     * Validate backup filename to prevent path traversal (S3-03).
     */
    private void validateBackupFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Backup filename must not be empty");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid backup filename: path traversal not allowed");
        }
    }

    private Path ensureBackupDirectory() throws IOException {
        Path d = Path.of(config.getBackupDirectory());
        Files.createDirectories(d);
        setRestrictivePermissions(d);
        return d;
    }

    DatabaseType detectDatabaseType() {
        String url = config.getDatasourceUrl();
        if (url == null) return DatabaseType.UNKNOWN;
        if (url.contains("h2:")) return DatabaseType.H2;
        if (url.contains("postgresql")) return DatabaseType.POSTGRESQL;
        return DatabaseType.UNKNOWN;
    }

    private void createH2Backup(Path out) throws IOException {
        String path = extractH2Path();
        if (path == null) return;
        Path db = Path.of(path + ".mv.db");
        if (Files.exists(db)) Files.copy(db, out, StandardCopyOption.REPLACE_EXISTING);
    }

    private void restoreH2Backup(Path in) throws IOException {
        String path = extractH2Path();
        if (path == null) return;
        Path db = Path.of(path + ".mv.db");
        if (Files.exists(db)) Files.copy(db, Path.of(db + ".bak." + LocalDateTime.now().format(TS)));
        Files.copy(in, db, StandardCopyOption.REPLACE_EXISTING);
    }

    private String extractH2Path() {
        String url = config.getDatasourceUrl();
        if (url == null) return null;
        String p = url.contains("h2:file:") ? url.substring(url.indexOf("h2:file:") + 8)
            : url.contains("h2:") ? url.substring(url.indexOf("h2:") + 3) : null;
        if (p != null && p.contains(";")) p = p.substring(0, p.indexOf(";"));
        return p;
    }

    private int runPgDump(Path out) throws IOException, InterruptedException {
        DbInfo db = parsePostgresUrl();
        List<String> cmd = new ArrayList<>(List.of("pg_dump", "-h", db.host, "-p", db.port,
            "-U", config.getDbUser(), "-d", db.name, "-F", "c", "-f", out.toString()));
        if (config.getSchema() != null) { cmd.add("-n"); cmd.add(config.getSchema()); }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", config.getDbPassword());
        return pb.start().waitFor();
    }

    private int runPgRestore(Path in) throws IOException, InterruptedException {
        DbInfo db = parsePostgresUrl();
        List<String> cmd = new ArrayList<>(List.of("pg_restore", "-h", db.host, "-p", db.port,
            "-U", config.getDbUser(), "-d", db.name, "--clean", "--if-exists", in.toString()));
        if (config.getSchema() != null) { cmd.add("-n"); cmd.add(config.getSchema()); }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", config.getDbPassword());
        return pb.start().waitFor();
    }

    private DbInfo parsePostgresUrl() {
        String url = config.getDatasourceUrl().replace("jdbc:postgresql://", "");
        String[] p = url.split("[:/]");
        return new DbInfo(p[0], p.length > 1 ? p[1] : "5432", p.length > 2 ? p[2].split("\\?")[0] : "postgres");
    }

    record DbInfo(String host, String port, String name) {}

    // ===== Encryption helpers =====

    private static SecretKey deriveEncryptionKey(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            return null;
        }
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(masterKey.toCharArray(), BACKUP_SALT, PBKDF2_ITERATIONS, KEY_LENGTH);
            try {
                byte[] keyBytes = factory.generateSecret(spec).getEncoded();
                return new SecretKeySpec(keyBytes, "AES");
            } finally {
                spec.clearPassword();
            }
        } catch (java.security.GeneralSecurityException e) {
            log.error("Failed to derive backup encryption key", e);
            return null;
        }
    }

    private void encryptFile(Path input, Path output) throws IOException, java.security.GeneralSecurityException {
        byte[] plaintext = Files.readAllBytes(input);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Write IV + ciphertext
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        Files.write(output, combined);
    }

    private void decryptFile(Path input, Path output) throws IOException, java.security.GeneralSecurityException {
        byte[] combined = Files.readAllBytes(input);
        if (combined.length < GCM_IV_LENGTH) {
            throw new IOException("Encrypted backup file too small");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        Files.write(output, plaintext);
    }

    boolean isEncryptionEnabled() {
        return encryptionKey != null;
    }

    private static boolean isBackupFile(String path) {
        return path.endsWith(".zip") || path.endsWith(".dump")
                || path.endsWith(".zip" + ENCRYPTED_SUFFIX) || path.endsWith(".dump" + ENCRYPTED_SUFFIX);
    }

    private static void setRestrictivePermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.isDirectory(path)
                    ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
                    : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions
            log.debug("POSIX permissions not supported: {}", path);
        } catch (IOException e) {
            log.warn("Could not set restrictive permissions on {}: {}", path, e.getMessage());
        }
    }
}
