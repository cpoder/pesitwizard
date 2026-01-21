package com.pesitwizard.client.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.pesitwizard.client.dto.TransferStats;
import com.pesitwizard.client.entity.TransferHistory;
import com.pesitwizard.client.entity.TransferHistory.TransferDirection;
import com.pesitwizard.client.entity.TransferHistory.TransferStatus;
import com.pesitwizard.client.pesit.PesitMessageService;
import com.pesitwizard.client.pesit.PesitReceiveService;
import com.pesitwizard.client.pesit.PesitSendService;
import com.pesitwizard.client.pesit.StorageConnectorFactory;
import com.pesitwizard.client.repository.TransferConfigRepository;
import com.pesitwizard.client.repository.TransferHistoryRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferService Tests")
class TransferServiceTest {

    @Mock
    private PesitSendService sendService;
    @Mock
    private PesitReceiveService receiveService;
    @Mock
    private PesitMessageService messageService;
    @Mock
    private PesitServerService serverService;
    @Mock
    private TransferConfigRepository configRepository;
    @Mock
    private TransferHistoryRepository historyRepository;
    @Mock
    private PathPlaceholderService placeholderService;
    @Mock
    private StorageConnectorFactory connectorFactory;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                sendService,
                receiveService,
                messageService,
                serverService,
                configRepository,
                historyRepository,
                placeholderService,
                connectorFactory);
    }

    @Nested
    @DisplayName("getStats")
    class GetStatsTests {

        @Test
        @DisplayName("should return transfer statistics")
        void shouldReturnTransferStatistics() {
            when(historyRepository.count()).thenReturn(100L);
            when(historyRepository.countByStatus(TransferStatus.COMPLETED)).thenReturn(90L);
            when(historyRepository.countByStatus(TransferStatus.FAILED)).thenReturn(5L);
            when(historyRepository.countByStatus(TransferStatus.IN_PROGRESS)).thenReturn(5L);
            when(historyRepository.sumBytesTransferredSince(eq(TransferStatus.COMPLETED), any(Instant.class)))
                    .thenReturn(1024000L);

            TransferStats stats = transferService.getStats();

            assertThat(stats.totalTransfers()).isEqualTo(100L);
            assertThat(stats.completedTransfers()).isEqualTo(90L);
            assertThat(stats.failedTransfers()).isEqualTo(5L);
            assertThat(stats.inProgressTransfers()).isEqualTo(5L);
            assertThat(stats.totalBytesTransferred()).isEqualTo(1024000L);
        }

        @Test
        @DisplayName("should handle null bytesTransferred")
        void shouldHandleNullBytesTransferred() {
            when(historyRepository.count()).thenReturn(0L);
            when(historyRepository.countByStatus(any())).thenReturn(0L);
            when(historyRepository.sumBytesTransferredSince(any(), any())).thenReturn(null);

            TransferStats stats = transferService.getStats();

            assertThat(stats.totalBytesTransferred()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("getHistory")
    class GetHistoryTests {

        @Test
        @DisplayName("should return paginated history")
        void shouldReturnPaginatedHistory() {
            TransferHistory h1 = createHistory("t1", TransferStatus.COMPLETED);
            TransferHistory h2 = createHistory("t2", TransferStatus.IN_PROGRESS);
            Page<TransferHistory> page = new PageImpl<>(List.of(h1, h2));
            Pageable pageable = PageRequest.of(0, 10);

            when(historyRepository.findAll(pageable)).thenReturn(page);

            Page<TransferHistory> result = transferService.getHistory(pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getId()).isEqualTo("t1");
        }
    }

    @Nested
    @DisplayName("getTransferById")
    class GetTransferByIdTests {

        @Test
        @DisplayName("should return transfer when found")
        void shouldReturnTransferWhenFound() {
            TransferHistory history = createHistory("t1", TransferStatus.COMPLETED);
            when(historyRepository.findById("t1")).thenReturn(Optional.of(history));

            Optional<TransferHistory> result = transferService.getTransferById("t1");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo("t1");
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(historyRepository.findById("unknown")).thenReturn(Optional.empty());

            Optional<TransferHistory> result = transferService.getTransferById("unknown");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getByCorrelationId")
    class GetByCorrelationIdTests {

        @Test
        @DisplayName("should return transfers by correlation ID")
        void shouldReturnTransfersByCorrelationId() {
            TransferHistory h1 = createHistory("t1", TransferStatus.COMPLETED);
            h1.setCorrelationId("corr-123");
            when(historyRepository.findByCorrelationId("corr-123")).thenReturn(List.of(h1));

            List<TransferHistory> result = transferService.getByCorrelationId("corr-123");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCorrelationId()).isEqualTo("corr-123");
        }
    }

    @Nested
    @DisplayName("cancelTransfer")
    class CancelTransferTests {

        @Test
        @DisplayName("should cancel in-progress transfer")
        void shouldCancelInProgressTransfer() {
            TransferHistory history = createHistory("t1", TransferStatus.IN_PROGRESS);
            when(historyRepository.findById("t1")).thenReturn(Optional.of(history));
            when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = transferService.cancelTransfer("t1");

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(TransferStatus.CANCELLED);
            assertThat(transferService.isCancelled("t1")).isTrue();
        }

        @Test
        @DisplayName("should not cancel completed transfer")
        void shouldNotCancelCompletedTransfer() {
            TransferHistory history = createHistory("t1", TransferStatus.COMPLETED);
            when(historyRepository.findById("t1")).thenReturn(Optional.of(history));

            var result = transferService.cancelTransfer("t1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for unknown transfer")
        void shouldReturnEmptyForUnknownTransfer() {
            when(historyRepository.findById("unknown")).thenReturn(Optional.empty());

            var result = transferService.cancelTransfer("unknown");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("isCancelled and clearCancellation")
    class CancellationStateTests {

        @Test
        @DisplayName("should track cancellation state")
        void shouldTrackCancellationState() {
            TransferHistory history = createHistory("t1", TransferStatus.IN_PROGRESS);
            when(historyRepository.findById("t1")).thenReturn(Optional.of(history));
            when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThat(transferService.isCancelled("t1")).isFalse();

            transferService.cancelTransfer("t1");
            assertThat(transferService.isCancelled("t1")).isTrue();

            transferService.clearCancellation("t1");
            assertThat(transferService.isCancelled("t1")).isFalse();
        }
    }

    @Nested
    @DisplayName("getResumableTransfers")
    class GetResumableTransfersTests {

        @Test
        @DisplayName("should return resumable transfers")
        void shouldReturnResumableTransfers() {
            TransferHistory h1 = createHistory("t1", TransferStatus.FAILED);
            h1.setLastSyncPoint(1000);
            Page<TransferHistory> page = new PageImpl<>(List.of(h1));
            Pageable pageable = PageRequest.of(0, 10);

            when(historyRepository.findResumableTransfers(pageable)).thenReturn(page);

            Page<TransferHistory> result = transferService.getResumableTransfers(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getLastSyncPoint()).isEqualTo(1000L);
        }
    }

    @Nested
    @DisplayName("resumeTransfer")
    class ResumeTransferTests {

        @Test
        @DisplayName("should not resume completed transfer")
        void shouldNotResumeCompletedTransfer() {
            TransferHistory history = createHistory("t1", TransferStatus.COMPLETED);
            history.setLastSyncPoint(1000);
            when(historyRepository.findById("t1")).thenReturn(Optional.of(history));

            var result = transferService.resumeTransfer("t1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should not resume transfer without sync point")
        void shouldNotResumeTransferWithoutSyncPoint() {
            TransferHistory history = createHistory("t1", TransferStatus.FAILED);
            history.setLastSyncPoint(null);
            when(historyRepository.findById("t1")).thenReturn(Optional.of(history));

            var result = transferService.resumeTransfer("t1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should not resume transfer with zero sync point")
        void shouldNotResumeTransferWithZeroSyncPoint() {
            TransferHistory history = createHistory("t1", TransferStatus.FAILED);
            history.setLastSyncPoint(0);
            when(historyRepository.findById("t1")).thenReturn(Optional.of(history));

            var result = transferService.resumeTransfer("t1");

            assertThat(result).isEmpty();
        }
    }

    private TransferHistory createHistory(String id, TransferStatus status) {
        return TransferHistory.builder()
                .id(id)
                .serverId("server-1")
                .serverName("TestServer")
                .partnerId("partner-1")
                .direction(TransferDirection.SEND)
                .localFilename("/tmp/test.txt")
                .remoteFilename("test.txt")
                .status(status)
                .startedAt(Instant.now())
                .build();
    }
}
