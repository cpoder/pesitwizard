package com.pesitwizard.client.repository;

import com.pesitwizard.client.entity.TransferConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferConfigRepository extends JpaRepository<TransferConfig, String> {

    Optional<TransferConfig> findByName(String name);

    Optional<TransferConfig> findByDefaultConfigTrue();

    boolean existsByName(String name);
}
