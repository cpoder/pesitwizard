package com.vectis.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.vectis.server.entity.PesitServerConfig;
import com.vectis.server.entity.PesitServerConfig.ServerStatus;

/**
 * Repository for PeSIT server configurations
 */
@Repository
public interface PesitServerConfigRepository extends JpaRepository<PesitServerConfig, Long> {

    Optional<PesitServerConfig> findByServerId(String serverId);

    Optional<PesitServerConfig> findByPort(int port);

    List<PesitServerConfig> findByStatus(ServerStatus status);

    List<PesitServerConfig> findByAutoStartTrue();

    boolean existsByServerId(String serverId);

    boolean existsByPort(int port);
}
