package com.vectis.client.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.client.dto.MessageRequest;
import com.vectis.client.dto.TransferRequest;
import com.vectis.client.dto.TransferResponse;
import com.vectis.client.dto.TransferStats;
import com.vectis.client.entity.TransferHistory;
import com.vectis.client.service.TransferService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API for file transfers
 */
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    /**
     * Send a file to a PeSIT server
     */
    @PostMapping("/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferResponse sendFile(@Valid @RequestBody TransferRequest request) {
        return transferService.sendFile(request);
    }

    /**
     * Receive a file from a PeSIT server
     */
    @PostMapping("/receive")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferResponse receiveFile(@Valid @RequestBody TransferRequest request) {
        return transferService.receiveFile(request);
    }

    /**
     * Send a message to a PeSIT server
     */
    @PostMapping("/message")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferResponse sendMessage(@Valid @RequestBody MessageRequest request) {
        return transferService.sendMessage(request);
    }

    /**
     * Get transfer history (sorted by startedAt descending by default)
     */
    @GetMapping("/history")
    public Page<TransferHistory> getHistory(
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transferService.getHistory(pageable);
    }

    /**
     * Get transfer by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransferHistory> getTransfer(@PathVariable String id) {
        return transferService.getTransferById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get transfers by correlation ID
     */
    @GetMapping("/correlation/{correlationId}")
    public ResponseEntity<?> getByCorrelationId(@PathVariable String correlationId) {
        var transfers = transferService.getByCorrelationId(correlationId);
        if (transfers.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(transfers);
    }

    /**
     * Get transfer statistics
     */
    @GetMapping("/stats")
    public TransferStats getStats() {
        return transferService.getStats();
    }

    /**
     * Replay a previous transfer.
     * Creates a new transfer with the same parameters as the original.
     */
    @PostMapping("/{id}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<TransferResponse> replayTransfer(@PathVariable String id) {
        return transferService.replayTransfer(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
