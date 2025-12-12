package com.vectis.server.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.server.entity.TransferRecord;
import com.vectis.server.entity.TransferRecord.TransferDirection;
import com.vectis.server.entity.TransferRecord.TransferStatus;
import com.vectis.server.service.TransferService;
import com.vectis.server.service.TransferService.DailyTransferStats;
import com.vectis.server.service.TransferService.PartnerTransferStatistics;
import com.vectis.server.service.TransferService.TransferStatistics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for transfer management.
 * Provides endpoints for viewing, searching, and managing file transfers.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    // ========== List & Search ==========

    /**
     * List all transfers with pagination
     */
    @GetMapping
    public ResponseEntity<Page<TransferRecord>> listTransfers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Page<TransferRecord> transfers = transferService.getAllTransfers(
                PageRequest.of(page, size, sort));

        return ResponseEntity.ok(transfers);
    }

    /**
     * Search transfers with filters
     */
    @GetMapping("/search")
    public ResponseEntity<Page<TransferRecord>> searchTransfers(
            @RequestParam(required = false) String partnerId,
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) TransferDirection direction,
            @RequestParam(required = false) String filename,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<TransferRecord> transfers = transferService.searchTransfers(
                partnerId, status, direction, filename, startDate, endDate, page, size);

        return ResponseEntity.ok(transfers);
    }

    /**
     * Get active transfers
     */
    @GetMapping("/active")
    public ResponseEntity<List<TransferRecord>> getActiveTransfers() {
        return ResponseEntity.ok(transferService.getActiveTransfers());
    }

    /**
     * Get active transfers for a specific server
     */
    @GetMapping("/active/server/{serverId}")
    public ResponseEntity<List<TransferRecord>> getActiveTransfersByServer(
            @PathVariable String serverId) {
        return ResponseEntity.ok(transferService.getActiveTransfersByServer(serverId));
    }

    /**
     * Get transfers by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<TransferRecord>> getTransfersByStatus(
            @PathVariable TransferStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(transferService.getTransfersByStatus(status, page, size));
    }

    /**
     * Get transfers by partner
     */
    @GetMapping("/partner/{partnerId}")
    public ResponseEntity<Page<TransferRecord>> getTransfersByPartner(
            @PathVariable String partnerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(transferService.getTransfersByPartner(partnerId, page, size));
    }

    /**
     * Get retryable transfers
     */
    @GetMapping("/retryable")
    public ResponseEntity<List<TransferRecord>> getRetryableTransfers() {
        return ResponseEntity.ok(transferService.getRetryableTransfers());
    }

    // ========== Single Transfer ==========

    /**
     * Get a specific transfer by ID
     */
    @GetMapping("/{transferId}")
    public ResponseEntity<TransferRecord> getTransfer(@PathVariable String transferId) {
        return transferService.getTransfer(transferId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get transfers for a session
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<TransferRecord>> getTransfersBySession(
            @PathVariable String sessionId) {
        return ResponseEntity.ok(transferService.getTransfersBySession(sessionId));
    }

    /**
     * Get retry history for a transfer
     */
    @GetMapping("/{transferId}/retries")
    public ResponseEntity<List<TransferRecord>> getRetryHistory(
            @PathVariable String transferId) {
        return ResponseEntity.ok(transferService.getRetryHistory(transferId));
    }

    // ========== Transfer Actions ==========

    /**
     * Cancel an active transfer
     */
    @PostMapping("/{transferId}/cancel")
    public ResponseEntity<TransferRecord> cancelTransfer(
            @PathVariable String transferId,
            @RequestParam(defaultValue = "Cancelled by user") String reason) {

        try {
            TransferRecord transfer = transferService.cancelTransfer(transferId, reason);
            log.info("Transfer {} cancelled via API: {}", transferId, reason);
            return ResponseEntity.ok(transfer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Pause an active transfer
     */
    @PostMapping("/{transferId}/pause")
    public ResponseEntity<TransferRecord> pauseTransfer(@PathVariable String transferId) {
        try {
            TransferRecord transfer = transferService.pauseTransfer(transferId);
            log.info("Transfer {} paused via API", transferId);
            return ResponseEntity.ok(transfer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Resume a paused transfer
     */
    @PostMapping("/{transferId}/resume")
    public ResponseEntity<TransferRecord> resumeTransfer(@PathVariable String transferId) {
        try {
            TransferRecord transfer = transferService.resumeTransfer(transferId);
            log.info("Transfer {} resumed via API", transferId);
            return ResponseEntity.ok(transfer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retry a failed transfer
     */
    @PostMapping("/{transferId}/retry")
    public ResponseEntity<TransferRecord> retryTransfer(@PathVariable String transferId) {
        try {
            TransferRecord transfer = transferService.retryTransfer(transferId);
            log.info("Transfer {} retry initiated via API: new transfer {}",
                    transferId, transfer.getTransferId());
            return ResponseEntity.ok(transfer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ========== Statistics ==========

    /**
     * Get overall transfer statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<TransferStatistics> getStatistics() {
        return ResponseEntity.ok(transferService.getStatistics());
    }

    /**
     * Get statistics for a specific partner
     */
    @GetMapping("/stats/partner/{partnerId}")
    public ResponseEntity<PartnerTransferStatistics> getPartnerStatistics(
            @PathVariable String partnerId) {
        return ResponseEntity.ok(transferService.getPartnerStatistics(partnerId));
    }

    /**
     * Get daily transfer statistics
     */
    @GetMapping("/stats/daily")
    public ResponseEntity<List<DailyTransferStats>> getDailyStatistics(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(transferService.getDailyStatistics(days));
    }

    // ========== Admin ==========

    /**
     * Trigger cleanup of old transfers
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Void> triggerCleanup() {
        log.info("Transfer cleanup triggered via API");
        transferService.cleanupOldTransfers();
        return ResponseEntity.ok().build();
    }
}
