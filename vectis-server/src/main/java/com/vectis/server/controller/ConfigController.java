package com.vectis.server.controller;

import java.util.List;

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

import com.vectis.server.entity.AuditEvent.AuditEventType;
import com.vectis.server.entity.Partner;
import com.vectis.server.entity.VirtualFile;
import com.vectis.server.service.AuditService;
import com.vectis.server.service.ConfigService;

import lombok.RequiredArgsConstructor;

/**
 * REST controller for managing PeSIT server configuration.
 * Provides APIs for partners and virtual files management.
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;
    private final AuditService auditService;

    // ==================== Partners ====================

    /**
     * Get all partners
     */
    @GetMapping("/partners")
    public ResponseEntity<List<Partner>> getAllPartners() {
        return ResponseEntity.ok(configService.getAllPartners());
    }

    /**
     * Get a partner by ID
     */
    @GetMapping("/partners/{id}")
    public ResponseEntity<Partner> getPartner(@PathVariable String id) {
        return configService.getPartner(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new partner
     */
    @PostMapping("/partners")
    public ResponseEntity<Partner> createPartner(@RequestBody Partner partner) {
        if (configService.partnerExists(partner.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        Partner created = configService.savePartner(partner);
        auditService.logConfigChange(AuditEventType.PARTNER_CREATED, "Partner", partner.getId(),
                null, "Created partner: " + partner.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update or create a partner
     */
    @PutMapping("/partners/{id}")
    public ResponseEntity<Partner> savePartner(@PathVariable String id, @RequestBody Partner partner) {
        boolean isNew = !configService.partnerExists(id);
        partner.setId(id);
        Partner saved = configService.savePartner(partner);
        auditService.logConfigChange(isNew ? AuditEventType.PARTNER_CREATED : AuditEventType.PARTNER_UPDATED,
                "Partner", id, null, (isNew ? "Created" : "Updated") + " partner: " + id);
        return ResponseEntity.ok(saved);
    }

    /**
     * Delete a partner
     */
    @DeleteMapping("/partners/{id}")
    public ResponseEntity<Void> deletePartner(@PathVariable String id) {
        if (!configService.partnerExists(id)) {
            return ResponseEntity.notFound().build();
        }
        configService.deletePartner(id);
        auditService.logConfigChange(AuditEventType.PARTNER_DELETED, "Partner", id,
                null, "Deleted partner: " + id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Virtual Files ====================

    /**
     * Get all virtual files
     */
    @GetMapping("/files")
    public ResponseEntity<List<VirtualFile>> getAllVirtualFiles() {
        return ResponseEntity.ok(configService.getAllVirtualFiles());
    }

    /**
     * Get a virtual file by ID
     */
    @GetMapping("/files/{id}")
    public ResponseEntity<VirtualFile> getVirtualFile(@PathVariable String id) {
        return configService.getVirtualFile(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new virtual file
     */
    @PostMapping("/files")
    public ResponseEntity<VirtualFile> createVirtualFile(@RequestBody VirtualFile file) {
        if (configService.virtualFileExists(file.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        VirtualFile created = configService.saveVirtualFile(file);
        auditService.logConfigChange(AuditEventType.VIRTUAL_FILE_CREATED, "VirtualFile", file.getId(),
                null, "Created virtual file: " + file.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update or create a virtual file
     */
    @PutMapping("/files/{id}")
    public ResponseEntity<VirtualFile> saveVirtualFile(@PathVariable String id, @RequestBody VirtualFile file) {
        boolean isNew = !configService.virtualFileExists(id);
        file.setId(id);
        VirtualFile saved = configService.saveVirtualFile(file);
        auditService.logConfigChange(isNew ? AuditEventType.VIRTUAL_FILE_CREATED : AuditEventType.VIRTUAL_FILE_UPDATED,
                "VirtualFile", id, null, (isNew ? "Created" : "Updated") + " virtual file: " + id);
        return ResponseEntity.ok(saved);
    }

    /**
     * Delete a virtual file
     */
    @DeleteMapping("/files/{id}")
    public ResponseEntity<Void> deleteVirtualFile(@PathVariable String id) {
        if (!configService.virtualFileExists(id)) {
            return ResponseEntity.notFound().build();
        }
        configService.deleteVirtualFile(id);
        auditService.logConfigChange(AuditEventType.VIRTUAL_FILE_DELETED, "VirtualFile", id,
                null, "Deleted virtual file: " + id);
        return ResponseEntity.noContent().build();
    }
}
