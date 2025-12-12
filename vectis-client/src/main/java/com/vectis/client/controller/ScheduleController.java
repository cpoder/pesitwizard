package com.vectis.client.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.client.entity.ScheduledTransfer;
import com.vectis.client.entity.ScheduledTransfer.ScheduleType;
import com.vectis.client.service.TransferSchedulerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API for managing scheduled transfers
 */
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final TransferSchedulerService schedulerService;

    /**
     * Get all schedules
     */
    @GetMapping
    public List<ScheduledTransfer> getAllSchedules() {
        return schedulerService.getAllSchedules();
    }

    /**
     * Get a schedule by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ScheduledTransfer> getSchedule(@PathVariable String id) {
        return schedulerService.getSchedule(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new schedule
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledTransfer createSchedule(@Valid @RequestBody ScheduledTransfer schedule) {
        return schedulerService.createSchedule(schedule);
    }

    /**
     * Create a schedule from a favorite
     */
    @PostMapping("/from-favorite/{favoriteId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ScheduledTransfer> createFromFavorite(
            @PathVariable String favoriteId,
            @RequestParam ScheduleType type,
            @RequestParam(required = false) Instant scheduledAt,
            @RequestParam(required = false) Integer intervalMinutes) {
        return schedulerService.createFromFavorite(favoriteId, type, scheduledAt, intervalMinutes)
                .map(s -> ResponseEntity.status(HttpStatus.CREATED).body(s))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update a schedule
     */
    @PutMapping("/{id}")
    public ResponseEntity<ScheduledTransfer> updateSchedule(
            @PathVariable String id,
            @Valid @RequestBody ScheduledTransfer schedule) {
        return schedulerService.updateSchedule(id, schedule)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a schedule
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(@PathVariable String id) {
        schedulerService.deleteSchedule(id);
    }

    /**
     * Toggle schedule enabled/disabled
     */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<ScheduledTransfer> toggleEnabled(@PathVariable String id) {
        return schedulerService.toggleEnabled(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Run a schedule immediately
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<ScheduledTransfer> runNow(@PathVariable String id) {
        return schedulerService.runNow(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
