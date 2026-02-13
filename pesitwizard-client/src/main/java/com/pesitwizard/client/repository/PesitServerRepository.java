package com.pesitwizard.client.repository;

import com.pesitwizard.client.entity.PesitServer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PesitServerRepository extends JpaRepository<PesitServer, String> {

    Optional<PesitServer> findByName(String name);

    List<PesitServer> findByEnabledTrue();

    Optional<PesitServer> findByDefaultServerTrue();

    boolean existsByName(String name);

    List<PesitServer> findByHostAndPort(String host, Integer port);
}
