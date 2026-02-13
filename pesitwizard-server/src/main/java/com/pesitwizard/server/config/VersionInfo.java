package com.pesitwizard.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Logs version information at application startup. */
@Slf4j
@Component
public class VersionInfo {

    @Value("${pesitwizard.version:unknown}")
    private String version;

    @Value("${pesitwizard.build.timestamp:unknown}")
    private String buildTimestamp;

    @Value("${pesitwizard.build.commit:unknown}")
    private String buildCommit;

    @Value("${pesitwizard.cluster.enabled:false}")
    private boolean clusterEnabled;

    @Value("${pesitwizard.cluster.node-name:standalone}")
    private String nodeName;

    @EventListener(ApplicationReadyEvent.class)
    public void logVersionInfo() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║            PeSIT Wizard Server                             ║");
        log.info("╠════════════════════════════════════════════════════════════╣");
        log.info("║  Version:     {}                                    ║", padRight(version, 10));
        log.info("║  Build:       {}                          ║", padRight(buildTimestamp, 20));
        log.info(
                "║  Commit:      {}                                    ║",
                padRight(buildCommit, 10));
        log.info(
                "║  Cluster:     {}                                    ║",
                padRight(clusterEnabled ? "enabled" : "disabled", 10));
        log.info("║  Node:        {}                          ║", padRight(nodeName, 20));
        log.info("╚════════════════════════════════════════════════════════════╝");
    }

    private String padRight(String s, int n) {
        if (s == null) s = "unknown";
        if (s.length() > n) return s.substring(0, n);
        return String.format("%-" + n + "s", s);
    }

    public String getVersion() {
        return version;
    }

    public String getBuildTimestamp() {
        return buildTimestamp;
    }

    public String getBuildCommit() {
        return buildCommit;
    }
}
