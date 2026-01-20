package com.pesitwizard.server.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.pesitwizard.server.config.ObservabilityProperties;
import com.pesitwizard.server.config.ObservabilityProperties.MetricsConfig;
import com.pesitwizard.server.config.ObservabilityProperties.TracingConfig;

@WebMvcTest(ObservabilityController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ObservabilityController Tests")
class ObservabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObservabilityProperties observabilityProperties;

    @BeforeEach
    void setUp() {
        // Create real config objects instead of mocks
        TracingConfig tracingConfig = new TracingConfig();
        tracingConfig.setEnabled(true);
        tracingConfig.setEndpoint("http://jaeger:4317");

        MetricsConfig metricsConfig = new MetricsConfig();
        metricsConfig.setEnabled(true);

        when(observabilityProperties.getServiceName()).thenReturn("pesitwizard-server");
        when(observabilityProperties.getTracing()).thenReturn(tracingConfig);
        when(observabilityProperties.getMetrics()).thenReturn(metricsConfig);
    }

    @Test
    @DisplayName("should get observability config")
    void shouldGetConfig() throws Exception {
        mockMvc.perform(get("/api/v1/observability/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("pesitwizard-server"))
                .andExpect(jsonPath("$.tracingEnabled").value(true))
                .andExpect(jsonPath("$.tracingEndpoint").value("http://jaeger:4317"))
                .andExpect(jsonPath("$.metricsEnabled").value(true));
    }

    @Test
    @DisplayName("should update tracing enabled config")
    void shouldUpdateTracingEnabledConfig() throws Exception {
        mockMvc.perform(post("/api/v1/observability/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tracingEnabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Restart required for tracing changes to take effect"));
    }

    @Test
    @DisplayName("should update metrics config without restart message")
    void shouldUpdateMetricsConfigWithoutRestartMessage() throws Exception {
        mockMvc.perform(post("/api/v1/observability/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"metricsEnabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    @DisplayName("should update tracing endpoint")
    void shouldUpdateTracingEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/observability/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tracingEndpoint\": \"http://new-jaeger:4317\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Restart required for tracing changes to take effect"));
    }
}
