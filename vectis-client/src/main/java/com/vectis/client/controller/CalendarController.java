package com.vectis.client.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.client.entity.BusinessCalendar;
import com.vectis.client.repository.BusinessCalendarRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/calendars")
@RequiredArgsConstructor
public class CalendarController {

    private final BusinessCalendarRepository calendarRepository;

    @GetMapping
    public List<BusinessCalendar> getAllCalendars() {
        return calendarRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessCalendar> getCalendar(@PathVariable String id) {
        return calendarRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessCalendar createCalendar(@Valid @RequestBody BusinessCalendar calendar) {
        return calendarRepository.save(calendar);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessCalendar> updateCalendar(
            @PathVariable String id,
            @Valid @RequestBody BusinessCalendar updated) {
        return calendarRepository.findById(id)
                .map(existing -> {
                    existing.setName(updated.getName());
                    existing.setDescription(updated.getDescription());
                    existing.setTimezone(updated.getTimezone());
                    existing.setWorkingDays(updated.getWorkingDays());
                    existing.setHolidays(updated.getHolidays());
                    existing.setBusinessHoursStart(updated.getBusinessHoursStart());
                    existing.setBusinessHoursEnd(updated.getBusinessHoursEnd());
                    existing.setRestrictToBusinessHours(updated.isRestrictToBusinessHours());
                    existing.setDefaultCalendar(updated.isDefaultCalendar());
                    return ResponseEntity.ok(calendarRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCalendar(@PathVariable String id) {
        calendarRepository.deleteById(id);
    }

    @PostMapping("/{id}/holidays")
    public ResponseEntity<BusinessCalendar> addHolidays(
            @PathVariable String id,
            @RequestBody Set<LocalDate> holidays) {
        return calendarRepository.findById(id)
                .map(calendar -> {
                    calendar.getHolidays().addAll(holidays);
                    return ResponseEntity.ok(calendarRepository.save(calendar));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/holidays")
    public ResponseEntity<BusinessCalendar> removeHolidays(
            @PathVariable String id,
            @RequestBody Set<LocalDate> holidays) {
        return calendarRepository.findById(id)
                .map(calendar -> {
                    calendar.getHolidays().removeAll(holidays);
                    return ResponseEntity.ok(calendarRepository.save(calendar));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/default")
    public ResponseEntity<BusinessCalendar> getDefaultCalendar() {
        return calendarRepository.findByDefaultCalendarTrue()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
