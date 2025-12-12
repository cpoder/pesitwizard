package com.vectis.client.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "business_calendars")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "Europe/Paris";

    @ElementCollection
    @CollectionTable(name = "calendar_working_days", joinColumns = @JoinColumn(name = "calendar_id"))
    @Column(name = "day_of_week")
    @Builder.Default
    private Set<Integer> workingDays = new HashSet<>(Set.of(1, 2, 3, 4, 5));

    @ElementCollection
    @CollectionTable(name = "calendar_holidays", joinColumns = @JoinColumn(name = "calendar_id"))
    @Column(name = "holiday_date")
    @Builder.Default
    private Set<LocalDate> holidays = new HashSet<>();

    @Builder.Default
    private LocalTime businessHoursStart = LocalTime.of(8, 0);

    @Builder.Default
    private LocalTime businessHoursEnd = LocalTime.of(18, 0);

    @Builder.Default
    private boolean restrictToBusinessHours = false;

    @Builder.Default
    private boolean defaultCalendar = false;

    @Column(updatable = false)
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isWorkingDay(LocalDate date) {
        if (holidays.contains(date))
            return false;
        return workingDays.contains(date.getDayOfWeek().getValue());
    }

    public boolean isWithinBusinessHours(Instant instant) {
        if (!restrictToBusinessHours)
            return true;
        LocalTime time = instant.atZone(ZoneId.of(timezone)).toLocalTime();
        return !time.isBefore(businessHoursStart) && !time.isAfter(businessHoursEnd);
    }

    public boolean isValidExecutionTime(Instant instant) {
        ZoneId zone = ZoneId.of(timezone);
        LocalDate date = instant.atZone(zone).toLocalDate();
        return isWorkingDay(date) && isWithinBusinessHours(instant);
    }
}
