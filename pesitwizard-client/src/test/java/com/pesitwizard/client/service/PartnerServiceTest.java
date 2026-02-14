package com.pesitwizard.client.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.client.entity.Partner;
import com.pesitwizard.client.repository.PartnerRepository;
import com.pesitwizard.security.SecretsService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PartnerServiceTest {

    @Mock private PartnerRepository partnerRepository;
    @Mock private SecretsService secretsService;

    private PartnerService partnerService;

    @BeforeEach
    void setUp() {
        partnerService = new PartnerService(partnerRepository, secretsService);
    }

    @Test
    void getAllPartners_returnsList() {
        Partner p = Partner.builder().id("1").partnerId("P1").build();
        when(partnerRepository.findAll()).thenReturn(List.of(p));

        List<Partner> result = partnerService.getAllPartners();
        assertThat(result).hasSize(1).first().extracting(Partner::getPartnerId).isEqualTo("P1");
    }

    @Test
    void getPartner_found() {
        Partner p = Partner.builder().id("1").partnerId("P1").build();
        when(partnerRepository.findById("1")).thenReturn(Optional.of(p));

        assertThat(partnerService.getPartner("1")).isPresent();
    }

    @Test
    void getPartner_notFound() {
        when(partnerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(partnerService.getPartner("missing")).isEmpty();
    }

    @Test
    void getByPartnerId_found() {
        Partner p = Partner.builder().id("1").partnerId("P1").build();
        when(partnerRepository.findByPartnerId("P1")).thenReturn(Optional.of(p));

        assertThat(partnerService.getByPartnerId("P1")).isPresent();
    }

    @Test
    void createPartner_encryptsPassword() {
        Partner p = Partner.builder().partnerId("P1").password("secret").build();
        when(secretsService.isEncrypted("secret")).thenReturn(false);
        when(secretsService.encryptForStorage(
                        eq("secret"), eq("partner"), eq("P1"), eq("password")))
                .thenReturn("ENC:xxx");
        when(partnerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Partner result = partnerService.createPartner(p);
        assertThat(result.getPassword()).isEqualTo("ENC:xxx");
    }

    @Test
    void updatePartner_withNewPassword_encryptsIt() {
        Partner existing = Partner.builder().id("1").partnerId("P1").password("old").build();
        Partner updated = Partner.builder().partnerId("P1-new").password("newpw").build();
        when(partnerRepository.findById("1")).thenReturn(Optional.of(existing));
        when(secretsService.isEncrypted("newpw")).thenReturn(false);
        when(secretsService.encryptForStorage(
                        eq("newpw"), eq("partner"), eq("P1-new"), eq("password")))
                .thenReturn("ENC:yyy");
        when(partnerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Partner> result = partnerService.updatePartner("1", updated);
        assertThat(result).isPresent();
        assertThat(result.get().getPassword()).isEqualTo("ENC:yyy");
    }

    @Test
    void updatePartner_withoutPassword_keepsExisting() {
        Partner existing = Partner.builder().id("1").partnerId("P1").password("old-enc").build();
        Partner updated = Partner.builder().partnerId("P1-new").build();
        when(partnerRepository.findById("1")).thenReturn(Optional.of(existing));
        when(partnerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Partner> result = partnerService.updatePartner("1", updated);
        assertThat(result).isPresent();
        assertThat(result.get().getPassword()).isEqualTo("old-enc");
    }

    @Test
    void updatePartner_notFound_returnsEmpty() {
        when(partnerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(partnerService.updatePartner("missing", new Partner())).isEmpty();
    }

    @Test
    void deletePartner_delegatesToRepository() {
        partnerService.deletePartner("1");
        verify(partnerRepository).deleteById("1");
    }

    @Test
    void resolvePassword_found_decrypts() {
        Partner p = Partner.builder().partnerId("P1").password("ENC:xxx").build();
        when(partnerRepository.findByPartnerId("P1")).thenReturn(Optional.of(p));
        when(secretsService.decryptFromStorage("ENC:xxx")).thenReturn("secret");

        assertThat(partnerService.resolvePassword("P1")).isEqualTo("secret");
    }

    @Test
    void resolvePassword_nullPartnerId_returnsNull() {
        assertThat(partnerService.resolvePassword(null)).isNull();
    }

    @Test
    void resolvePassword_notFound_returnsNull() {
        when(partnerRepository.findByPartnerId("missing")).thenReturn(Optional.empty());

        assertThat(partnerService.resolvePassword("missing")).isNull();
    }
}
