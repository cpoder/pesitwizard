package com.pesitwizard.backup;

import java.time.Instant;
import lombok.Data;

@Data
public class RestoreResult {
    private boolean success;
    private String backupName;
    private Instant timestamp;
    private String message;
}
