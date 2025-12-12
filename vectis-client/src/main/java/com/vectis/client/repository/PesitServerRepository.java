package com.vectis.client.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.vectis.client.entity.PesitServer;

@Repository
public interface PesitServerRepository extends JpaRepository<PesitServer, String> {

    Optional<PesitServer> findByName(String name);

    List<PesitServer> findByEnabledTrue();

    Optional<PesitServer> findByDefaultServerTrue();

    boolean existsByName(String name);

    List<PesitServer> findByHostAndPort(String host, Integer port);
}
