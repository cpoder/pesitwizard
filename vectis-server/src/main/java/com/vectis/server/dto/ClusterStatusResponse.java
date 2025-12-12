package com.vectis.server.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * DTO for cluster status response
 */
@Data
public class ClusterStatusResponse {
    private boolean clusterEnabled;
    private String nodeName;
    private boolean leader;
    private boolean connected;
    private int clusterSize;
    private List<String> members;
    private Map<String, String> serverOwnership;
}
