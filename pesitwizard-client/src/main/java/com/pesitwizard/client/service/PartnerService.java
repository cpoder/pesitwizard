package com.pesitwizard.client.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pesitwizard.client.entity.Partner;
import com.pesitwizard.client.repository.PartnerRepository;
import com.pesitwizard.security.SecretsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing PeSIT partners with encrypted password storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final SecretsService secretsService;

    public List<Partner> getAllPartners() {
        return partnerRepository.findAll();
    }

    public Optional<Partner> getPartner(String id) {
        return partnerRepository.findById(id);
    }

    public Optional<Partner> getByPartnerId(String partnerId) {
        return partnerRepository.findByPartnerId(partnerId);
    }

    @Transactional
    public Partner createPartner(Partner partner) {
        log.info("Creating partner: {}", partner.getPartnerId());
        encryptPassword(partner);
        return partnerRepository.save(partner);
    }

    @Transactional
    public Optional<Partner> updatePartner(String id, Partner updated) {
        return partnerRepository.findById(id)
                .map(existing -> {
                    existing.setPartnerId(updated.getPartnerId());
                    existing.setDescription(updated.getDescription());
                    if (updated.getPassword() != null) {
                        existing.setPassword(updated.getPassword());
                        encryptPassword(existing);
                    }
                    log.info("Updated partner: {}", existing.getPartnerId());
                    return partnerRepository.save(existing);
                });
    }

    @Transactional
    public void deletePartner(String id) {
        log.info("Deleting partner: {}", id);
        partnerRepository.deleteById(id);
    }

    /**
     * Resolve the plaintext password for a given PeSIT partner ID.
     * Looks up the partner and decrypts the stored password.
     *
     * @return plaintext password or null if partner not found or no password set
     */
    public String resolvePassword(String partnerId) {
        if (partnerId == null || partnerId.isBlank()) {
            return null;
        }
        return partnerRepository.findByPartnerId(partnerId)
                .map(Partner::getPassword)
                .filter(pw -> pw != null && !pw.isBlank())
                .map(secretsService::decryptFromStorage)
                .orElse(null);
    }

    /**
     * Encrypt the password field if present and not already encrypted.
     */
    private void encryptPassword(Partner partner) {
        String password = partner.getPassword();
        if (password != null && !password.isBlank() && !secretsService.isEncrypted(password)) {
            String encrypted = secretsService.encryptForStorage(password, "partner", partner.getPartnerId(), "password");
            partner.setPassword(encrypted);
        }
    }
}
