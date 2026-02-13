package com.pesitwizard.server.repository;

import com.pesitwizard.server.entity.PesitServerConfig;
import com.pesitwizard.server.entity.PesitServerConfig.ServerStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for PeSIT server configurations */
@Repository
public interface PesitServerConfigRepository extends JpaRepository<PesitServerConfig, Long> {

    Optional<PesitServerConfig> findByServerId(String serverId);

    Optional<PesitServerConfig> findByPort(int port);

    List<PesitServerConfig> findByStatus(ServerStatus status);

    List<PesitServerConfig> findByAutoStartTrue();

    boolean existsByServerId(String serverId);

    boolean existsByPort(int port);
}
