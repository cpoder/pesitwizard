package com.pesitwizard.client.repository;

import com.pesitwizard.client.entity.Partner;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<Partner, String> {

    Optional<Partner> findByPartnerId(String partnerId);

    boolean existsByPartnerId(String partnerId);
}
