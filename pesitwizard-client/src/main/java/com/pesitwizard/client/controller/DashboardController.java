package com.pesitwizard.client.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.entity.TransferHistory;
import com.pesitwizard.client.repository.PesitServerRepository;
import com.pesitwizard.client.repository.ScheduledTransferRepository;
import com.pesitwizard.client.repository.TransferHistoryRepository;
import com.pesitwizard.client.service.TransferService;
import com.pesitwizard.security.SecretsService;

import lombok.RequiredArgsConstructor;

/**
 * Dashboard controller providing comprehensive overview of the PeSIT client
 * status.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

        private final TransferService transferService;
        private final TransferHistoryRepository transferHistoryRepository;
        private final PesitServerRepository serverRepository;
        private final ScheduledTransferRepository scheduledTransferRepository;
        private final SecretsService secretsService;

        @GetMapping
        public ResponseEntity<Map<String, Object>> getDashboard() {
                Map<String, Object> dashboard = new HashMap<>();

                // Transfer statistics
                var stats = transferService.getStats();
                dashboard.put("transfers", Map.of(
                                "total", stats.totalTransfers(),
                                "completed", stats.completedTransfers(),
                                "failed", stats.failedTransfers(),
                                "inProgress", stats.inProgressTransfers(),
                                "bytesTransferred",
                                stats.totalBytesTransferred() != null ? stats.totalBytesTransferred() : 0L));

                // In-progress transfers
                List<TransferHistory> inProgress = transferHistoryRepository.findByStatus(
                                TransferHistory.TransferStatus.IN_PROGRESS);
                dashboard.put("activeTransfers", inProgress.stream().map(t -> Map.of(
                                "id", t.getId(),
                                "filename", t.getRemoteFilename() != null ? t.getRemoteFilename() : "Unknown",
                                "direction", t.getDirection().name(),
                                "serverName", t.getServerName() != null ? t.getServerName() : "Unknown",
                                "progress", t.getBytesTransferred() != null ? t.getBytesTransferred() : 0,
                                "startedAt", t.getStartedAt() != null ? t.getStartedAt().toString() : null)).toList());

                // Recent transfers (last 5)
                List<TransferHistory> recent = transferHistoryRepository.findTop10ByOrderByStartedAtDesc();
                dashboard.put("recentTransfers", recent.stream().limit(5).map(t -> Map.of(
                                "id", t.getId(),
                                "filename", t.getRemoteFilename() != null ? t.getRemoteFilename() : "Unknown",
                                "direction", t.getDirection().name(),
                                "status", t.getStatus().name(),
                                "startedAt", t.getStartedAt() != null ? t.getStartedAt().toString() : null)).toList());

                // Server statistics
                List<PesitServer> servers = serverRepository.findAll();
                long enabledServers = servers.stream().filter(PesitServer::isEnabled).count();
                dashboard.put("servers", Map.of(
                                "total", servers.size(),
                                "enabled", enabledServers,
                                "disabled", servers.size() - enabledServers,
                                "list", servers.stream().map(s -> Map.of(
                                                "id", s.getId(),
                                                "name", s.getName(),
                                                "host", s.getHost(),
                                                "port", s.getPort(),
                                                "enabled", s.isEnabled(),
                                                "defaultServer", s.isDefaultServer())).toList()));

                // Security status
                var securityStatus = secretsService.getStatus();
                dashboard.put("security", Map.of(
                                "encryptionEnabled", securityStatus.available(),
                                "encryptionMode", securityStatus.providerType(),
                                "message", securityStatus.message(),
                                "vaultAvailable", secretsService.isVaultAvailable()));

                // Scheduled transfers count
                dashboard.put("scheduledTransfers", scheduledTransferRepository.countByEnabled(true));

                // System info
                Runtime runtime = Runtime.getRuntime();
                dashboard.put("system", Map.of(
                                "javaVersion", System.getProperty("java.version"),
                                "memoryUsed", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                                "memoryMax", runtime.maxMemory() / (1024 * 1024),
                                "processors", runtime.availableProcessors()));

                return ResponseEntity.ok(dashboard);
        }
}
