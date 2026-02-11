package com.pesitwizard.client.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pesitwizard.client.entity.Partner;

public interface PartnerRepository extends JpaRepository<Partner, String> {

    Optional<Partner> findByPartnerId(String partnerId);

    boolean existsByPartnerId(String partnerId);
}
