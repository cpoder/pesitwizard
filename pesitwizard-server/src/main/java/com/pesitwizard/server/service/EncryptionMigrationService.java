package com.pesitwizard.server.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pesitwizard.security.AbstractEncryptionMigrationService;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.server.entity.CertificateStore;
import com.pesitwizard.server.entity.Partner;
import com.pesitwizard.server.repository.CertificateStoreRepository;
import com.pesitwizard.server.repository.PartnerRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EncryptionMigrationService extends AbstractEncryptionMigrationService {

    private final PartnerRepository partnerRepository;
    private final CertificateStoreRepository certificateStoreRepository;

    public EncryptionMigrationService(SecretsService secretsService,
            PartnerRepository partnerRepository,
            CertificateStoreRepository certificateStoreRepository) {
        super(secretsService);
        this.partnerRepository = partnerRepository;
        this.certificateStoreRepository = certificateStoreRepository;
    }

    @Override
    @Transactional
    protected MigrationResult doMigration() {
        List<String> details = new ArrayList<>();
        int totalMigrated = 0, totalSkipped = 0;

        var p = migratePartners();
        totalMigrated += p.migrated();
        totalSkipped += p.skipped();
        details.add("Partners: " + p.migrated() + " migrated");

        var c = migrateCertificateStores();
        totalMigrated += c.migrated();
        totalSkipped += c.skipped();
        details.add("Certificates: " + c.migrated() + " migrated");

        return new MigrationResult(true, "Migration completed", totalMigrated, totalSkipped, details);
    }

    private MigrationCount migratePartners() {
        int migrated = 0, skipped = 0;
        for (Partner p : partnerRepository.findAll()) {
            if (p.getPassword() != null && !isVaultRef(p.getPassword())) {
                String plain = decryptIfNeeded(p.getPassword());
                p.setPassword(secretsService.encryptForStorage(plain, "partner", p.getId(), "password"));
                partnerRepository.save(p);
                migrated++;
            } else {
                skipped++;
            }
        }
        return new MigrationCount(migrated, skipped);
    }

    private MigrationCount migrateCertificateStores() {
        int migrated = 0, skipped = 0;
        for (CertificateStore c : certificateStoreRepository.findAll()) {
            boolean mod = false;
            if (c.getStorePassword() != null && !isVaultRef(c.getStorePassword())) {
                c.setStorePassword(secretsService.encryptForStorage(decryptIfNeeded(c.getStorePassword()), "certstore",
                        c.getId().toString(), "storePassword"));
                mod = true;
            }
            if (c.getKeyPassword() != null && !isVaultRef(c.getKeyPassword())) {
                c.setKeyPassword(secretsService.encryptForStorage(decryptIfNeeded(c.getKeyPassword()), "certstore",
                        c.getId().toString(), "keyPassword"));
                mod = true;
            }
            if (mod) {
                certificateStoreRepository.save(c);
                migrated++;
            } else {
                skipped++;
            }
        }
        return new MigrationCount(migrated, skipped);
    }

}
