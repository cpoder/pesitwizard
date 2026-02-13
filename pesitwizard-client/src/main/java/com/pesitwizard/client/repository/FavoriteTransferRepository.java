package com.pesitwizard.client.repository;

import com.pesitwizard.client.entity.FavoriteTransfer;
import com.pesitwizard.client.entity.TransferHistory.TransferDirection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteTransferRepository extends JpaRepository<FavoriteTransfer, String> {

    List<FavoriteTransfer> findByServerIdOrderByUsageCountDesc(String serverId);

    List<FavoriteTransfer> findByDirectionOrderByUsageCountDesc(TransferDirection direction);

    List<FavoriteTransfer> findAllByOrderByUsageCountDesc();

    List<FavoriteTransfer> findAllByOrderByLastUsedAtDesc();

    List<FavoriteTransfer> findByNameContainingIgnoreCase(String name);

    boolean existsByNameAndServerId(String name, String serverId);
}
