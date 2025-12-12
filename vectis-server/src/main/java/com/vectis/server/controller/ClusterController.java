package com.vectis.server.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.server.cluster.ClusterService;
import com.vectis.server.dto.ClusterStatusResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for cluster management and status.
 */
@Slf4j
@RestController
@RequestMapping("/api/cluster")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;

    /**
     * Get cluster status
     */
    @GetMapping("/status")
    public ClusterStatusResponse getClusterStatus() {
        ClusterStatusResponse response = new ClusterStatusResponse();
        response.setClusterEnabled(clusterService.isClusterEnabled());
        response.setNodeName(clusterService.getNodeName());
        response.setLeader(clusterService.isLeader());
        response.setConnected(clusterService.isConnected());
        response.setClusterSize(clusterService.getClusterSize());
        response.setMembers(clusterService.getClusterMembers());
        response.setServerOwnership(clusterService.getAllServerOwnership());
        return response;
    }

    /**
     * Get cluster members
     */
    @GetMapping("/members")
    public ResponseEntity<?> getClusterMembers() {
        return ResponseEntity.ok(Map.of(
                "members", clusterService.getClusterMembers(),
                "size", clusterService.getClusterSize(),
                "leader", clusterService.isLeader()));
    }

    /**
     * Get server ownership across cluster
     */
    @GetMapping("/ownership")
    public ResponseEntity<?> getServerOwnership() {
        return ResponseEntity.ok(clusterService.getAllServerOwnership());
    }

    /**
     * Check if this node is the leader
     */
    @GetMapping("/leader")
    public ResponseEntity<?> isLeader() {
        return ResponseEntity.ok(Map.of(
                "nodeName", clusterService.getNodeName(),
                "isLeader", clusterService.isLeader()));
    }
}
