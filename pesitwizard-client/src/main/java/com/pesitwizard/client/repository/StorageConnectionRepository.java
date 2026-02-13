package com.pesitwizard.client.repository;

import com.pesitwizard.client.entity.StorageConnection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageConnectionRepository extends JpaRepository<StorageConnection, String> {

    Optional<StorageConnection> findByName(String name);

    List<StorageConnection> findByConnectorType(String connectorType);

    List<StorageConnection> findByEnabled(boolean enabled);

    boolean existsByName(String name);
}
