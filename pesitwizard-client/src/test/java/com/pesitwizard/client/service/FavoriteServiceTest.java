package com.pesitwizard.client.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.client.dto.TransferResponse;
import com.pesitwizard.client.entity.FavoriteTransfer;
import com.pesitwizard.client.entity.ScheduledTransfer;
import com.pesitwizard.client.entity.TransferHistory;
import com.pesitwizard.client.entity.TransferHistory.TransferDirection;
import com.pesitwizard.client.repository.FavoriteTransferRepository;
import com.pesitwizard.client.repository.ScheduledTransferRepository;
import com.pesitwizard.client.repository.TransferHistoryRepository;
import com.pesitwizard.security.SecretsService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock private FavoriteTransferRepository favoriteRepository;
    @Mock private TransferHistoryRepository historyRepository;
    @Mock private ScheduledTransferRepository scheduleRepository;
    @Mock private TransferService transferService;
    @Mock private SecretsService secretsService;

    @InjectMocks private FavoriteService favoriteService;

    @Test
    void getAllFavorites_sortByLastUsed() {
        FavoriteTransfer fav = new FavoriteTransfer();
        fav.setName("test");
        when(favoriteRepository.findAllByOrderByLastUsedAtDesc()).thenReturn(List.of(fav));

        List<FavoriteTransfer> result = favoriteService.getAllFavorites("lastUsed");

        assertThat(result).hasSize(1);
        verify(favoriteRepository).findAllByOrderByLastUsedAtDesc();
    }

    @Test
    void getAllFavorites_sortByUsageCount() {
        when(favoriteRepository.findAllByOrderByUsageCountDesc()).thenReturn(List.of());

        List<FavoriteTransfer> result = favoriteService.getAllFavorites("usageCount");

        assertThat(result).isEmpty();
        verify(favoriteRepository).findAllByOrderByUsageCountDesc();
    }

    @Test
    void getFavorite_found() {
        FavoriteTransfer fav = new FavoriteTransfer();
        fav.setName("test");
        when(favoriteRepository.findById("1")).thenReturn(Optional.of(fav));

        assertThat(favoriteService.getFavorite("1")).isPresent();
    }

    @Test
    void getFavorite_notFound() {
        when(favoriteRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(favoriteService.getFavorite("missing")).isEmpty();
    }

    @Test
    void createFavorite_encryptsPassword() {
        FavoriteTransfer fav = new FavoriteTransfer();
        fav.setName("test");
        fav.setPassword("secret");
        when(secretsService.isEncrypted("secret")).thenReturn(false);
        when(secretsService.encryptForStorage("secret", "favorite", "test", "password"))
                .thenReturn("ENC:xxx");
        when(favoriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FavoriteTransfer result = favoriteService.createFavorite(fav);

        assertThat(result.getPassword()).isEqualTo("ENC:xxx");
    }

    @Test
    void createFavorite_alreadyEncryptedPassword() {
        FavoriteTransfer fav = new FavoriteTransfer();
        fav.setName("test");
        fav.setPassword("ENC:already");
        when(secretsService.isEncrypted("ENC:already")).thenReturn(true);
        when(favoriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FavoriteTransfer result = favoriteService.createFavorite(fav);

        assertThat(result.getPassword()).isEqualTo("ENC:already");
        verify(secretsService, never())
                .encryptForStorage(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createFromHistory_found() {
        TransferHistory history = new TransferHistory();
        history.setServerId("srv1");
        history.setServerName("Server1");
        history.setPartnerId("PART1");
        history.setDirection(TransferDirection.SEND);
        history.setLocalFilename("file.txt");
        history.setRemoteFilename("remote.txt");
        when(historyRepository.findById("h1")).thenReturn(Optional.of(history));
        when(favoriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<FavoriteTransfer> result = favoriteService.createFromHistory("h1", "My Fav");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("My Fav");
        assertThat(result.get().getServerId()).isEqualTo("srv1");
    }

    @Test
    void createFromHistory_notFound() {
        when(historyRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(favoriteService.createFromHistory("missing", "test")).isEmpty();
    }

    @Test
    void updateFavorite_found_updatesFields() {
        FavoriteTransfer existing = new FavoriteTransfer();
        existing.setId("1");
        existing.setName("old");
        existing.setServerId("srv1");
        existing.setDirection(TransferDirection.SEND);

        FavoriteTransfer updated = new FavoriteTransfer();
        updated.setName("new");
        updated.setServerId("srv2");
        updated.setPartnerId("PART2");
        updated.setDirection(TransferDirection.RECEIVE);

        when(favoriteRepository.findById("1")).thenReturn(Optional.of(existing));
        when(favoriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(scheduleRepository.findByFavoriteId("1")).thenReturn(List.of());

        Optional<FavoriteTransfer> result = favoriteService.updateFavorite("1", updated);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("new");
        assertThat(result.get().getServerId()).isEqualTo("srv2");
    }

    @Test
    void updateFavorite_notFound() {
        when(favoriteRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(favoriteService.updateFavorite("missing", new FavoriteTransfer())).isEmpty();
    }

    @Test
    void updateFavorite_syncsLinkedSchedules() {
        FavoriteTransfer existing = new FavoriteTransfer();
        existing.setId("1");
        existing.setName("fav");
        existing.setServerId("srv1");
        existing.setDirection(TransferDirection.SEND);

        FavoriteTransfer updated = new FavoriteTransfer();
        updated.setName("fav-updated");
        updated.setServerId("srv2");
        updated.setDirection(TransferDirection.SEND);

        ScheduledTransfer schedule = new ScheduledTransfer();
        schedule.setId("s1");
        schedule.setName("linked");
        schedule.setServerId("srv1");
        schedule.setDirection(TransferDirection.SEND);

        when(favoriteRepository.findById("1")).thenReturn(Optional.of(existing));
        when(favoriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(scheduleRepository.findByFavoriteId("1")).thenReturn(List.of(schedule));

        favoriteService.updateFavorite("1", updated);

        verify(scheduleRepository).save(schedule);
        assertThat(schedule.getServerId()).isEqualTo("srv2");
    }

    @Test
    void deleteFavorite() {
        favoriteService.deleteFavorite("1");

        verify(favoriteRepository).deleteById("1");
    }

    @Test
    void executeFavorite_send() {
        FavoriteTransfer fav = new FavoriteTransfer();
        fav.setId("1");
        fav.setName("test");
        fav.setServerId("srv1");
        fav.setPartnerId("PART1");
        fav.setDirection(TransferDirection.SEND);
        fav.setFilename("file.txt");

        when(favoriteRepository.findById("1")).thenReturn(Optional.of(fav));
        when(favoriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferResponse resp =
                TransferResponse.builder()
                        .transferId("t1")
                        .status(
                                com.pesitwizard.client.entity.TransferHistory.TransferStatus
                                        .PENDING)
                        .build();
        when(transferService.sendFile(any())).thenReturn(resp);

        Optional<TransferResponse> result = favoriteService.executeFavorite("1");

        assertThat(result).isPresent();
        verify(transferService).sendFile(any());
    }

    @Test
    void executeFavorite_receive() {
        FavoriteTransfer fav = new FavoriteTransfer();
        fav.setId("1");
        fav.setName("test");
        fav.setServerId("srv1");
        fav.setDirection(TransferDirection.RECEIVE);
        fav.setFilename("file.txt");

        when(favoriteRepository.findById("1")).thenReturn(Optional.of(fav));
        when(favoriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferResponse resp =
                TransferResponse.builder()
                        .transferId("t1")
                        .status(
                                com.pesitwizard.client.entity.TransferHistory.TransferStatus
                                        .PENDING)
                        .build();
        when(transferService.receiveFile(any())).thenReturn(resp);

        Optional<TransferResponse> result = favoriteService.executeFavorite("1");

        assertThat(result).isPresent();
        verify(transferService).receiveFile(any());
    }

    @Test
    void executeFavorite_decryptsPassword() {
        FavoriteTransfer fav = new FavoriteTransfer();
        fav.setId("1");
        fav.setName("test");
        fav.setServerId("srv1");
        fav.setDirection(TransferDirection.SEND);
        fav.setFilename("file.txt");
        fav.setPassword("ENC:xxx");

        when(favoriteRepository.findById("1")).thenReturn(Optional.of(fav));
        when(favoriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(secretsService.decryptFromStorage("ENC:xxx")).thenReturn("secret");
        when(transferService.sendFile(any()))
                .thenReturn(
                        TransferResponse.builder()
                                .transferId("t1")
                                .status(
                                        com.pesitwizard.client.entity.TransferHistory.TransferStatus
                                                .PENDING)
                                .build());

        favoriteService.executeFavorite("1");

        verify(secretsService).decryptFromStorage("ENC:xxx");
    }

    @Test
    void executeFavorite_notFound() {
        when(favoriteRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(favoriteService.executeFavorite("missing")).isEmpty();
    }

    @Test
    void executeFavorite_messageDirection_throws() {
        FavoriteTransfer fav = new FavoriteTransfer();
        fav.setId("1");
        fav.setName("test");
        fav.setServerId("srv1");
        fav.setDirection(TransferDirection.MESSAGE);

        when(favoriteRepository.findById("1")).thenReturn(Optional.of(fav));
        when(favoriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> favoriteService.executeFavorite("1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MESSAGE");
    }
}
