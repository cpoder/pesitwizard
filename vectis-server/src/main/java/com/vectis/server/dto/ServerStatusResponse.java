package com.vectis.server.dto;

import com.vectis.server.entity.PesitServerConfig.ServerStatus;

import lombok.Data;

/**
 * DTO for server status response
 */
@Data
public class ServerStatusResponse {
    private String serverId;
    private ServerStatus status;
    private boolean running;
    private int activeConnections;
    private int port;
}
