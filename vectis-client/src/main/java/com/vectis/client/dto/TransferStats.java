package com.vectis.client.dto;

/**
 * Transfer statistics DTO
 */
public record TransferStats(
        long totalTransfers,
        long completedTransfers,
        long failedTransfers,
        long inProgressTransfers,
        Long totalBytesTransferred) {
}
