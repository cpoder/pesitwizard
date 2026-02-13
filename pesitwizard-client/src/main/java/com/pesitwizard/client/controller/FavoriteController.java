package com.pesitwizard.client.controller;

import com.pesitwizard.client.dto.TransferResponse;
import com.pesitwizard.client.entity.FavoriteTransfer;
import com.pesitwizard.client.service.FavoriteService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

/** REST API for managing favorite transfers */
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** Get all favorites, optionally sorted by usage or last used */
    @GetMapping
    public List<FavoriteTransfer> getAllFavorites(
            @RequestParam(defaultValue = "usage") String sortBy) {
        List<FavoriteTransfer> favorites = favoriteService.getAllFavorites(sortBy);
        favorites.forEach(this::maskPassword);
        return favorites;
    }

    /** Get a favorite by ID */
    @GetMapping("/{id}")
    public ResponseEntity<FavoriteTransfer> getFavorite(@PathVariable String id) {
        return favoriteService
                .getFavorite(id)
                .map(
                        f -> {
                            maskPassword(f);
                            return f;
                        })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create a new favorite */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteTransfer createFavorite(@Valid @RequestBody FavoriteTransfer favorite) {
        FavoriteTransfer created = favoriteService.createFavorite(favorite);
        maskPassword(created);
        return created;
    }

    /** Create a favorite from an existing transfer history */
    @PostMapping("/from-history/{historyId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<FavoriteTransfer> createFromHistory(
            @PathVariable String historyId, @RequestParam String name) {
        return favoriteService
                .createFromHistory(historyId, name)
                .map(f -> ResponseEntity.status(HttpStatus.CREATED).body(f))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Update a favorite */
    @PutMapping("/{id}")
    public ResponseEntity<FavoriteTransfer> updateFavorite(
            @PathVariable String id, @Valid @RequestBody FavoriteTransfer favorite) {
        return favoriteService
                .updateFavorite(id, favorite)
                .map(
                        f -> {
                            maskPassword(f);
                            return f;
                        })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Delete a favorite */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFavorite(@PathVariable String id) {
        favoriteService.deleteFavorite(id);
    }

    /** Execute (replay) a favorite transfer */
    @PostMapping("/{id}/execute")
    public ResponseEntity<TransferResponse> executeFavorite(@PathVariable String id) {
        return favoriteService
                .executeFavorite(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private void maskPassword(FavoriteTransfer favorite) {
        if (favorite.getPassword() != null && !favorite.getPassword().isBlank()) {
            favorite.setPassword("****");
        }
    }
}
