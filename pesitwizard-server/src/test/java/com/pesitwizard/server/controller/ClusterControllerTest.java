package com.pesitwizard.server.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pesitwizard.server.cluster.ClusterProvider;

@WebMvcTest(ClusterController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ClusterController Tests")
class ClusterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClusterProvider clusterProvider;

    @Test
    @DisplayName("should get cluster status")
    void shouldGetClusterStatus() throws Exception {
        when(clusterProvider.isClusterEnabled()).thenReturn(true);
        when(clusterProvider.getNodeName()).thenReturn("node-1");
        when(clusterProvider.isLeader()).thenReturn(true);
        when(clusterProvider.isConnected()).thenReturn(true);
        when(clusterProvider.getClusterSize()).thenReturn(3);
        when(clusterProvider.getClusterMembers()).thenReturn(List.of("node-1", "node-2", "node-3"));
        when(clusterProvider.getAllServerOwnership()).thenReturn(Map.of("server-1", "node-1"));

        mockMvc.perform(get("/api/cluster/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterEnabled").value(true))
                .andExpect(jsonPath("$.nodeName").value("node-1"))
                .andExpect(jsonPath("$.leader").value(true))
                .andExpect(jsonPath("$.clusterSize").value(3));
    }

    @Test
    @DisplayName("should get cluster members")
    void shouldGetClusterMembers() throws Exception {
        when(clusterProvider.getClusterMembers()).thenReturn(List.of("node-1", "node-2"));
        when(clusterProvider.getClusterSize()).thenReturn(2);
        when(clusterProvider.isLeader()).thenReturn(false);

        mockMvc.perform(get("/api/cluster/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.members").isArray());
    }

    @Test
    @DisplayName("should get server ownership")
    void shouldGetServerOwnership() throws Exception {
        when(clusterProvider.getAllServerOwnership()).thenReturn(Map.of(
                "server-1", "node-1",
                "server-2", "node-2"));

        mockMvc.perform(get("/api/cluster/ownership"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server-1").value("node-1"));
    }

    @Test
    @DisplayName("should check leader status")
    void shouldCheckLeaderStatus() throws Exception {
        when(clusterProvider.getNodeName()).thenReturn("node-1");
        when(clusterProvider.isLeader()).thenReturn(true);

        mockMvc.perform(get("/api/cluster/leader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeName").value("node-1"))
                .andExpect(jsonPath("$.isLeader").value(true));
    }

    @Test
    @DisplayName("should return standalone status when not clustered")
    void shouldReturnStandaloneStatus() throws Exception {
        when(clusterProvider.isClusterEnabled()).thenReturn(false);
        when(clusterProvider.getNodeName()).thenReturn("standalone");
        when(clusterProvider.isLeader()).thenReturn(true);
        when(clusterProvider.isConnected()).thenReturn(false);
        when(clusterProvider.getClusterSize()).thenReturn(1);
        when(clusterProvider.getClusterMembers()).thenReturn(List.of("standalone"));
        when(clusterProvider.getAllServerOwnership()).thenReturn(Map.of());

        mockMvc.perform(get("/api/cluster/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterEnabled").value(false))
                .andExpect(jsonPath("$.clusterSize").value(1));
    }
}
