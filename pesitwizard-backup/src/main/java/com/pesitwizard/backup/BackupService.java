package com.pesitwizard.backup;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackupService {
    private final BackupConfig config;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public BackupService(BackupConfig config) { this.config = config; }

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
            
            Files.writeString(Path.of(file + ".meta"), r.getDescription());
            r.setBackupPath(file.toString());
            r.setSizeBytes(Files.size(file));
            r.setSuccess(true);
            log.info("Backup: {} ({})", name, r.getBackupType());
            cleanupOldBackups();
        } catch (Exception e) {
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
            s.filter(p -> p.toString().endsWith(".zip") || p.toString().endsWith(".dump"))
             .sorted(Comparator.comparing(Path::getFileName).reversed())
             .forEach(p -> {
                 try {
                     BackupInfo i = new BackupInfo();
                     i.setFilename(p.getFileName().toString());
                     i.setSizeBytes(Files.size(p));
                     i.setCreatedAt(Files.getLastModifiedTime(p).toInstant());
                     i.setType(p.toString().endsWith(".zip") ? "H2" : "POSTGRESQL");
                     Path m = Path.of(p + ".meta");
                     if (Files.exists(m)) i.setDescription(Files.readString(m).trim());
                     list.add(i);
                 } catch (IOException ignored) {}
             });
        } catch (IOException e) { log.error("List failed", e); }
        return list;
    }

    public RestoreResult restoreBackup(String filename) {
        RestoreResult r = new RestoreResult();
        r.setBackupName(filename);
        r.setTimestamp(Instant.now());
        Path file = Path.of(config.getBackupDirectory(), filename);
        if (!Files.exists(file)) { r.setSuccess(false); r.setMessage("Not found"); return r; }
        try {
            if (filename.endsWith(".zip")) {
                restoreH2Backup(file);
                r.setSuccess(true);
                r.setMessage("H2 restored - restart required");
            } else if (filename.endsWith(".dump")) {
                int c = runPgRestore(file);
                r.setSuccess(c == 0);
                r.setMessage(c == 0 ? "PostgreSQL restored" : "pg_restore failed: " + c);
            } else {
                r.setSuccess(false);
                r.setMessage("Unsupported format");
            }
            if (r.isSuccess()) log.info("Restored: {}", filename);
        } catch (Exception e) {
            r.setSuccess(false);
            r.setMessage(e.getMessage());
            log.error("Restore failed", e);
        }
        return r;
    }

    public boolean deleteBackup(String filename) {
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
            List<Path> all = s.filter(p -> p.toString().endsWith(".zip") || p.toString().endsWith(".dump"))
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

    private Path ensureBackupDirectory() throws IOException {
        Path d = Path.of(config.getBackupDirectory());
        Files.createDirectories(d);
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

    private int runPgDump(Path out) throws Exception {
        DbInfo db = parsePostgresUrl();
        List<String> cmd = new ArrayList<>(List.of("pg_dump", "-h", db.host, "-p", db.port,
            "-U", config.getDbUser(), "-d", db.name, "-F", "c", "-f", out.toString()));
        if (config.getSchema() != null) { cmd.add("-n"); cmd.add(config.getSchema()); }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", config.getDbPassword());
        return pb.start().waitFor();
    }

    private int runPgRestore(Path in) throws Exception {
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
}
