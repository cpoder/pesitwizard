package com.pesitwizard.client.repository;

import com.pesitwizard.client.entity.BusinessCalendar;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessCalendarRepository extends JpaRepository<BusinessCalendar, String> {
    Optional<BusinessCalendar> findByName(String name);

    Optional<BusinessCalendar> findByDefaultCalendarTrue();
}
