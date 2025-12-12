package com.vectis.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for sending a message via PeSIT
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequest {

    /** Server name or ID to use */
    @NotBlank(message = "Server is required")
    private String server;

    /** Message content */
    @NotBlank(message = "Message is required")
    @Size(max = 4096, message = "Message must not exceed 4096 characters")
    private String message;

    /** Message mode: FPDU, PI99, FILE */
    @Builder.Default
    private MessageMode mode = MessageMode.FPDU;

    /** Use PI_91 instead of PI_99 (for PI99 mode) */
    @Builder.Default
    private boolean usePi91 = true;

    /** Message name (for FILE mode) */
    private String messageName;

    /** Correlation ID for tracing */
    private String correlationId;

    public enum MessageMode {
        FPDU, // Use dedicated MSG FPDU
        PI99, // Use PI_99/PI_91 in CONNECT
        FILE // Send as file transfer
    }
}
