package com.vectis.client.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vectis.client.entity.ScheduledTransfer;

public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, String> {

    List<ScheduledTransfer> findByEnabledTrueOrderByNextRunAtAsc();

    List<ScheduledTransfer> findByFavoriteId(String favoriteId);

    @Query("SELECT s FROM ScheduledTransfer s WHERE s.enabled = true AND s.nextRunAt <= :now")
    List<ScheduledTransfer> findDueSchedules(@Param("now") Instant now);

    List<ScheduledTransfer> findAllByOrderByNextRunAtAsc();

    long countByEnabled(boolean enabled);
}
