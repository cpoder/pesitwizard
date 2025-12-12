package com.vectis.client.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.vectis.client.entity.TransferConfig;

@Repository
public interface TransferConfigRepository extends JpaRepository<TransferConfig, String> {

    Optional<TransferConfig> findByName(String name);

    Optional<TransferConfig> findByDefaultConfigTrue();

    boolean existsByName(String name);
}
