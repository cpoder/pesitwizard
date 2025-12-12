package com.vectis.client.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vectis.client.entity.FavoriteTransfer;
import com.vectis.client.entity.TransferHistory.TransferDirection;

public interface FavoriteTransferRepository extends JpaRepository<FavoriteTransfer, String> {

    List<FavoriteTransfer> findByServerIdOrderByUsageCountDesc(String serverId);

    List<FavoriteTransfer> findByDirectionOrderByUsageCountDesc(TransferDirection direction);

    List<FavoriteTransfer> findAllByOrderByUsageCountDesc();

    List<FavoriteTransfer> findAllByOrderByLastUsedAtDesc();

    List<FavoriteTransfer> findByNameContainingIgnoreCase(String name);

    boolean existsByNameAndServerId(String name, String serverId);
}
