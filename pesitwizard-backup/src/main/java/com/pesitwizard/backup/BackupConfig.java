package com.pesitwizard.backup;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BackupConfig {
    @Builder.Default
    private String backupDirectory = "./backups";
    @Builder.Default
    private String backupPrefix = "backup";
    @Builder.Default
    private int retentionDays = 30;
    @Builder.Default
    private int maxBackups = 10;
    
    // Database connection info
    private String datasourceUrl;
    private String dbUser;
    private String dbPassword;
    private String schema;
}
