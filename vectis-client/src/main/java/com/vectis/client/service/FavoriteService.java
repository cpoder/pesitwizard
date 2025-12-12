package com.vectis.client.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vectis.client.dto.TransferRequest;
import com.vectis.client.dto.TransferResponse;
import com.vectis.client.entity.FavoriteTransfer;
import com.vectis.client.entity.ScheduledTransfer;
import com.vectis.client.entity.TransferHistory.TransferDirection;
import com.vectis.client.repository.FavoriteTransferRepository;
import com.vectis.client.repository.ScheduledTransferRepository;
import com.vectis.client.repository.TransferHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing favorite transfers
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteTransferRepository favoriteRepository;
    private final TransferHistoryRepository historyRepository;
    private final ScheduledTransferRepository scheduleRepository;
    private final TransferService transferService;

    /**
     * Get all favorites sorted by usage count or last used
     */
    public List<FavoriteTransfer> getAllFavorites(String sortBy) {
        if ("lastUsed".equals(sortBy)) {
            return favoriteRepository.findAllByOrderByLastUsedAtDesc();
        }
        return favoriteRepository.findAllByOrderByUsageCountDesc();
    }

    /**
     * Get a favorite by ID
     */
    public Optional<FavoriteTransfer> getFavorite(String id) {
        return favoriteRepository.findById(id);
    }

    /**
     * Create a new favorite
     */
    @Transactional
    public FavoriteTransfer createFavorite(FavoriteTransfer favorite) {
        log.info("Creating favorite: {}", favorite.getName());
        return favoriteRepository.save(favorite);
    }

    /**
     * Create a favorite from an existing transfer history entry
     */
    @Transactional
    public Optional<FavoriteTransfer> createFromHistory(String historyId, String name) {
        return historyRepository.findById(historyId)
                .map(history -> {
                    FavoriteTransfer favorite = FavoriteTransfer.builder()
                            .name(name)
                            .description("Created from transfer " + historyId)
                            .serverId(history.getServerId())
                            .serverName(history.getServerName())
                            .partnerId(history.getPartnerId())
                            .direction(history.getDirection())
                            .localPath(history.getLocalFilename())
                            .remoteFilename(history.getRemoteFilename())
                            .transferConfigId(history.getTransferConfigId())
                            .build();
                    log.info("Creating favorite '{}' from history {}", name, historyId);
                    return favoriteRepository.save(favorite);
                });
    }

    /**
     * Update a favorite and sync linked schedules
     */
    @Transactional
    public Optional<FavoriteTransfer> updateFavorite(String id, FavoriteTransfer updated) {
        return favoriteRepository.findById(id)
                .map(existing -> {
                    existing.setName(updated.getName());
                    existing.setDescription(updated.getDescription());
                    existing.setServerId(updated.getServerId());
                    existing.setServerName(updated.getServerName());
                    existing.setPartnerId(updated.getPartnerId());
                    existing.setDirection(updated.getDirection());
                    existing.setLocalPath(updated.getLocalPath());
                    existing.setRemoteFilename(updated.getRemoteFilename());
                    existing.setVirtualFile(updated.getVirtualFile());
                    existing.setTransferConfigId(updated.getTransferConfigId());

                    FavoriteTransfer saved = favoriteRepository.save(existing);

                    // Update all linked schedules
                    updateLinkedSchedules(saved);

                    log.info("Updated favorite: {}", saved.getName());
                    return saved;
                });
    }

    /**
     * Update all schedules linked to this favorite
     */
    private void updateLinkedSchedules(FavoriteTransfer favorite) {
        List<ScheduledTransfer> linkedSchedules = scheduleRepository.findByFavoriteId(favorite.getId());
        if (!linkedSchedules.isEmpty()) {
            log.info("Updating {} linked schedules for favorite {}", linkedSchedules.size(), favorite.getName());
            for (ScheduledTransfer schedule : linkedSchedules) {
                schedule.setServerId(favorite.getServerId());
                schedule.setServerName(favorite.getServerName());
                schedule.setPartnerId(favorite.getPartnerId());
                schedule.setDirection(favorite.getDirection());
                schedule.setLocalPath(favorite.getLocalPath());
                schedule.setRemoteFilename(favorite.getRemoteFilename());
                schedule.setVirtualFile(favorite.getVirtualFile());
                schedule.setTransferConfigId(favorite.getTransferConfigId());
                scheduleRepository.save(schedule);
            }
        }
    }

    /**
     * Delete a favorite
     */
    @Transactional
    public void deleteFavorite(String id) {
        log.info("Deleting favorite: {}", id);
        favoriteRepository.deleteById(id);
    }

    /**
     * Execute a favorite transfer
     */
    @Transactional
    public Optional<TransferResponse> executeFavorite(String id) {
        return favoriteRepository.findById(id)
                .map(favorite -> {
                    log.info("Executing favorite: {}", favorite.getName());

                    // Mark as used
                    favorite.markUsed();
                    favoriteRepository.save(favorite);

                    // Build transfer request
                    TransferRequest request = TransferRequest.builder()
                            .server(favorite.getServerId())
                            .partnerId(favorite.getPartnerId())
                            .localPath(favorite.getLocalPath())
                            .remoteFilename(favorite.getRemoteFilename())
                            .virtualFile(favorite.getVirtualFile())
                            .transferConfig(favorite.getTransferConfigId())
                            .build();

                    // Execute based on direction
                    if (favorite.getDirection() == TransferDirection.SEND) {
                        return transferService.sendFile(request);
                    } else if (favorite.getDirection() == TransferDirection.RECEIVE) {
                        return transferService.receiveFile(request);
                    } else {
                        throw new IllegalArgumentException("MESSAGE favorites cannot be executed");
                    }
                });
    }
}
