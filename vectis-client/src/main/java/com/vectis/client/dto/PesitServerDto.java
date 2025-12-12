package com.vectis.client.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for PeSIT server configuration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PesitServerDto {

    private String id;

    @NotBlank(message = "Server name is required")
    private String name;

    @NotBlank(message = "Host is required")
    private String host;

    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer port;

    @NotBlank(message = "Server ID is required")
    private String serverId;

    private String description;

    @NotBlank(message = "Client ID is required")
    private String clientId;

    private String password;

    @Builder.Default
    private boolean tlsEnabled = false;

    private String truststorePath;
    private String truststorePassword;
    private String keystorePath;
    private String keystorePassword;

    @Builder.Default
    private Integer connectionTimeout = 30000;

    @Builder.Default
    private Integer readTimeout = 60000;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private boolean defaultServer = false;
}
