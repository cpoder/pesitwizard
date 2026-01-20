package com.pesitwizard.client.controller;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pesitwizard.client.dto.PesitServerDto;
import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.service.PesitServerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API for managing PeSIT server configurations
 */
@RestController
@RequestMapping("/api/v1/servers")
@RequiredArgsConstructor
public class ServerController {

    private final PesitServerService serverService;

    @GetMapping
    public List<PesitServerDto> getAllServers() {
        return serverService.getAllServers().stream()
                .map(serverService::mapToDto)
                .toList();
    }

    @GetMapping("/enabled")
    public List<PesitServerDto> getEnabledServers() {
        return serverService.getEnabledServers().stream()
                .map(serverService::mapToDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PesitServerDto> getServer(@PathVariable String id) {
        return serverService.getServerById(id)
                .map(serverService::mapToDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<PesitServerDto> getServerByName(@PathVariable String name) {
        return serverService.getServerByName(name)
                .map(serverService::mapToDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/default")
    public ResponseEntity<PesitServerDto> getDefaultServer() {
        return serverService.getDefaultServer()
                .map(serverService::mapToDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PesitServerDto createServer(@Valid @RequestBody PesitServerDto dto) {
        PesitServer server = serverService.createServer(dto);
        return serverService.mapToDto(server);
    }

    @PutMapping("/{id}")
    public PesitServerDto updateServer(@PathVariable String id, @Valid @RequestBody PesitServerDto dto) {
        PesitServer server = serverService.updateServer(id, dto);
        return serverService.mapToDto(server);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteServer(@PathVariable String id) {
        serverService.deleteServer(id);
    }

    @PostMapping("/{id}/default")
    public PesitServerDto setDefaultServer(@PathVariable String id) {
        PesitServer server = serverService.setDefaultServer(id);
        return serverService.mapToDto(server);
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<TestResult> testConnection(@PathVariable String id) {
        return serverService.getServerById(id)
                .map(server -> {
                    var result = serverService.testConnection(server);
                    return ResponseEntity.ok(new TestResult(result.success(), result.message()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    public record TestResult(boolean success, String message) {
    }
}
