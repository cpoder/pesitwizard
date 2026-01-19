package com.pesitwizard.backup;

import java.time.Instant;
import lombok.Data;

@Data
public class BackupInfo {
    private String filename;
    private String type;
    private Long sizeBytes;
    private Instant createdAt;
    private String description;
}
