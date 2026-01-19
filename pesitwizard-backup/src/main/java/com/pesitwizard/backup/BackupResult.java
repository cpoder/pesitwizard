package com.pesitwizard.backup;

import java.time.Instant;
import lombok.Data;

@Data
public class BackupResult {
    private boolean success;
    private String backupName;
    private String backupPath;
    private String backupType;
    private Long sizeBytes;
    private Instant timestamp;
    private String description;
    private String message;
}
