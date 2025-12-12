package com.vectis.server.observability;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.vectis.server.cluster.ClusterService;
import com.vectis.server.service.PesitServerManager;

import lombok.RequiredArgsConstructor;

/**
 * Health indicator for PeSIT server components.
 * Exposes health status via /actuator/health endpoint.
 */
@Component
@RequiredArgsConstructor
public class PesitHealthIndicator implements HealthIndicator {

    private final ClusterService clusterService;
    private final PesitServerManager serverManager;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();

        // Server status - get actual running count from server manager
        int runningServers = (int) serverManager.getRunningServers().size();
        int activeConnections = serverManager.getActiveConnectionCount();
        details.put("runningServers", runningServers);
        details.put("activeConnections", activeConnections);

        // Cluster status
        boolean clusterEnabled = clusterService.isClusterEnabled();
        boolean clusterConnected = clusterService.isConnected();
        int clusterSize = clusterService.getClusterSize();
        String nodeName = clusterService.getNodeName();
        details.put("clusterEnabled", clusterEnabled);
        details.put("clusterConnected", clusterConnected);
        details.put("clusterSize", clusterSize);
        details.put("nodeName", nodeName);

        // Determine overall health
        // In standalone mode (cluster disabled), we're always "connected"
        if (clusterEnabled && !clusterConnected) {
            return Health.down()
                    .withDetails(details)
                    .withDetail("reason", "Cluster not connected")
                    .build();
        }

        if (runningServers == 0) {
            return Health.up()
                    .withDetails(details)
                    .withDetail("status", "No servers running")
                    .build();
        }

        return Health.up()
                .withDetails(details)
                .build();
    }
}
