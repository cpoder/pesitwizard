package com.vectis.client.controller;

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

import com.vectis.client.dto.TransferResponse;
import com.vectis.client.entity.FavoriteTransfer;
import com.vectis.client.service.FavoriteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API for managing favorite transfers
 */
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * Get all favorites, optionally sorted by usage or last used
     */
    @GetMapping
    public List<FavoriteTransfer> getAllFavorites(
            @RequestParam(defaultValue = "usage") String sortBy) {
        return favoriteService.getAllFavorites(sortBy);
    }

    /**
     * Get a favorite by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<FavoriteTransfer> getFavorite(@PathVariable String id) {
        return favoriteService.getFavorite(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new favorite
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteTransfer createFavorite(@Valid @RequestBody FavoriteTransfer favorite) {
        return favoriteService.createFavorite(favorite);
    }

    /**
     * Create a favorite from an existing transfer history
     */
    @PostMapping("/from-history/{historyId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<FavoriteTransfer> createFromHistory(
            @PathVariable String historyId,
            @RequestParam String name) {
        return favoriteService.createFromHistory(historyId, name)
                .map(f -> ResponseEntity.status(HttpStatus.CREATED).body(f))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update a favorite
     */
    @PutMapping("/{id}")
    public ResponseEntity<FavoriteTransfer> updateFavorite(
            @PathVariable String id,
            @Valid @RequestBody FavoriteTransfer favorite) {
        return favoriteService.updateFavorite(id, favorite)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a favorite
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFavorite(@PathVariable String id) {
        favoriteService.deleteFavorite(id);
    }

    /**
     * Execute (replay) a favorite transfer
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<TransferResponse> executeFavorite(@PathVariable String id) {
        return favoriteService.executeFavorite(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
