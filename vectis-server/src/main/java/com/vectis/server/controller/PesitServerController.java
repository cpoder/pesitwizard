package com.vectis.server.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.server.dto.ServerStatusResponse;
import com.vectis.server.entity.PesitServerConfig;
import com.vectis.server.service.PesitServerManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for managing PeSIT server instances.
 */
@Slf4j
@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class PesitServerController {

    private final PesitServerManager serverManager;

    /**
     * Get all server configurations
     */
    @GetMapping
    public List<PesitServerConfig> getAllServers() {
        return serverManager.getAllServers();
    }

    /**
     * Get a specific server configuration
     */
    @GetMapping("/{serverId}")
    public ResponseEntity<PesitServerConfig> getServer(@PathVariable String serverId) {
        return serverManager.getServer(serverId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new server configuration
     */
    @PostMapping
    public ResponseEntity<?> createServer(@RequestBody PesitServerConfig config) {
        try {
            PesitServerConfig created = serverManager.createServer(config);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update a server configuration
     */
    @PutMapping("/{serverId}")
    public ResponseEntity<?> updateServer(@PathVariable String serverId,
            @RequestBody PesitServerConfig config) {
        try {
            PesitServerConfig updated = serverManager.updateServer(serverId, config);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a server configuration
     */
    @DeleteMapping("/{serverId}")
    public ResponseEntity<?> deleteServer(@PathVariable String serverId) {
        try {
            serverManager.deleteServer(serverId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Start a server
     */
    @PostMapping("/{serverId}/start")
    public ResponseEntity<?> startServer(@PathVariable String serverId) {
        try {
            serverManager.startServer(serverId);
            return ResponseEntity.ok(Map.of(
                    "message", "Server started",
                    "serverId", serverId,
                    "status", "RUNNING"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Stop a server
     */
    @PostMapping("/{serverId}/stop")
    public ResponseEntity<?> stopServer(@PathVariable String serverId) {
        try {
            serverManager.stopServer(serverId);
            return ResponseEntity.ok(Map.of(
                    "message", "Server stopped",
                    "serverId", serverId,
                    "status", "STOPPED"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get server status
     */
    @GetMapping("/{serverId}/status")
    public ResponseEntity<ServerStatusResponse> getServerStatus(@PathVariable String serverId) {
        return serverManager.getServer(serverId)
                .map(config -> {
                    ServerStatusResponse response = new ServerStatusResponse();
                    response.setServerId(serverId);
                    response.setStatus(serverManager.getServerStatus(serverId));
                    response.setRunning(serverManager.isServerRunning(serverId));
                    response.setActiveConnections(serverManager.getActiveConnections(serverId));
                    response.setPort(config.getPort());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
