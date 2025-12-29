package com.pesitwizard.server.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pesitwizard.security.SecretsService;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.entity.Partner;
import com.pesitwizard.server.entity.VirtualFile;
import com.pesitwizard.server.repository.PartnerRepository;
import com.pesitwizard.server.repository.VirtualFileRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing partners and virtual files configuration.
 * Combines database storage with YAML-based defaults.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ConfigService {

    private final PartnerRepository partnerRepository;
    private final VirtualFileRepository virtualFileRepository;
    private final PesitServerProperties serverProperties;
    private final SecretsService secretsService;

    /**
     * Initialize database with YAML-configured partners and files if empty
     */
    @PostConstruct
    @Transactional
    public void initializeFromYaml() {
        // Import partners from YAML if database is empty
        if (partnerRepository.count() == 0 && !serverProperties.getPartners().isEmpty()) {
            log.info("Importing {} partners from YAML configuration", serverProperties.getPartners().size());
            serverProperties.getPartners().forEach((key, config) -> {
                Partner partner = Partner.builder()
                        .id(config.getId() != null ? config.getId() : key)
                        .description(config.getDescription())
                        .password(config.getPassword())
                        .enabled(config.isEnabled())
                        .accessType(Partner.AccessType.valueOf(config.getAccessType().name()))
                        .maxConnections(config.getMaxConnections())
                        .allowedFiles(
                                config.getAllowedFiles() != null ? String.join(",", config.getAllowedFiles()) : null)
                        .build();
                partnerRepository.save(partner);
                log.debug("Imported partner: {}", partner.getId());
            });
        }

        // Import virtual files from YAML if database is empty
        if (virtualFileRepository.count() == 0 && !serverProperties.getFiles().isEmpty()) {
            log.info("Importing {} virtual files from YAML configuration", serverProperties.getFiles().size());
            serverProperties.getFiles().forEach((key, config) -> {
                VirtualFile file = VirtualFile.builder()
                        .id(config.getId() != null ? config.getId() : key)
                        .description(config.getDescription())
                        .enabled(config.isEnabled())
                        .direction(VirtualFile.Direction.valueOf(config.getDirection().name()))
                        .receiveDirectory(config.getReceiveDirectory())
                        .sendDirectory(config.getSendDirectory())
                        .receiveFilenamePattern(config.getReceiveFilenamePattern())
                        .overwrite(config.isOverwrite())
                        .maxFileSize(config.getMaxFileSize())
                        .fileType(config.getFileType())
                        .build();
                virtualFileRepository.save(file);
                log.debug("Imported virtual file: {}", file.getId());
            });
        }
    }

    // ==================== Partner Management ====================

    public List<Partner> getAllPartners() {
        return partnerRepository.findAll();
    }

    public List<Partner> getEnabledPartners() {
        return partnerRepository.findByEnabled(true);
    }

    public Optional<Partner> getPartner(String id) {
        return partnerRepository.findById(id);
    }

    /**
     * Find partner by ID (case-insensitive)
     */
    public Optional<Partner> findPartner(String partnerId) {
        if (partnerId == null)
            return Optional.empty();

        // Try exact match first
        Optional<Partner> partner = partnerRepository.findById(partnerId);
        if (partner.isPresent())
            return partner;

        // Try case-insensitive match
        return partnerRepository.findAll().stream()
                .filter(p -> p.getId().equalsIgnoreCase(partnerId))
                .findFirst();
    }

    @Transactional
    public Partner savePartner(Partner partner) {
        log.info("Saving partner: {}", partner.getId());

        // Encrypt password for storage
        String password = partner.getPassword();
        if (password != null && !password.isBlank() && !secretsService.isEncrypted(password)) {
            String encrypted = secretsService.encryptForStorage(password);
            partner.setPassword(encrypted);
            log.debug("Partner password encrypted for storage");
        }

        return partnerRepository.save(partner);
    }

    @Transactional
    public void deletePartner(String id) {
        log.info("Deleting partner: {}", id);
        partnerRepository.deleteById(id);
    }

    public boolean partnerExists(String id) {
        return partnerRepository.existsById(id);
    }

    // ==================== Virtual File Management ====================

    public List<VirtualFile> getAllVirtualFiles() {
        return virtualFileRepository.findAll();
    }

    public List<VirtualFile> getEnabledVirtualFiles() {
        return virtualFileRepository.findByEnabled(true);
    }

    public Optional<VirtualFile> getVirtualFile(String id) {
        return virtualFileRepository.findById(id);
    }

    /**
     * Find virtual file by filename (supports pattern matching)
     */
    public Optional<VirtualFile> findVirtualFile(String filename) {
        if (filename == null)
            return Optional.empty();

        // Try exact match first
        Optional<VirtualFile> file = virtualFileRepository.findById(filename);
        if (file.isPresent())
            return file;

        // Try pattern match
        return virtualFileRepository.findByEnabled(true).stream()
                .filter(f -> f.matches(filename))
                .findFirst();
    }

    @Transactional
    public VirtualFile saveVirtualFile(VirtualFile file) {
        log.info("Saving virtual file: {}", file.getId());
        return virtualFileRepository.save(file);
    }

    @Transactional
    public void deleteVirtualFile(String id) {
        log.info("Deleting virtual file: {}", id);
        virtualFileRepository.deleteById(id);
    }

    public boolean virtualFileExists(String id) {
        return virtualFileRepository.existsById(id);
    }
}
