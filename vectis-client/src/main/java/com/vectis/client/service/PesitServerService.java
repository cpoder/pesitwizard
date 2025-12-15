package com.vectis.client.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vectis.client.dto.PesitServerDto;
import com.vectis.client.entity.PesitServer;
import com.vectis.client.repository.PesitServerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing PeSIT server configurations
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PesitServerService {

    private final PesitServerRepository serverRepository;

    @Transactional(readOnly = true)
    public List<PesitServer> getAllServers() {
        return serverRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<PesitServer> getEnabledServers() {
        return serverRepository.findByEnabledTrue();
    }

    @Transactional(readOnly = true)
    public Optional<PesitServer> getServerById(String id) {
        return serverRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<PesitServer> getServerByName(String name) {
        return serverRepository.findByName(name);
    }

    @Transactional(readOnly = true)
    public Optional<PesitServer> getDefaultServer() {
        return serverRepository.findByDefaultServerTrue();
    }

    /**
     * Find server by name or ID
     */
    @Transactional(readOnly = true)
    public Optional<PesitServer> findServer(String nameOrId) {
        // Try by name first
        Optional<PesitServer> server = serverRepository.findByName(nameOrId);
        if (server.isPresent()) {
            return server;
        }
        // Try by ID
        return serverRepository.findById(nameOrId);
    }

    @Transactional
    public PesitServer createServer(PesitServerDto dto) {
        if (serverRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Server with name '" + dto.getName() + "' already exists");
        }

        PesitServer server = mapToEntity(dto);

        // If this is set as default, clear other defaults
        if (server.isDefaultServer()) {
            clearDefaultServer();
        }

        server = serverRepository.save(server);
        log.info("Created PeSIT server: {} ({}:{})", server.getName(), server.getHost(), server.getPort());
        return server;
    }

    @Transactional
    public PesitServer updateServer(String id, PesitServerDto dto) {
        PesitServer server = serverRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + id));

        // Check name uniqueness if changed
        if (!server.getName().equals(dto.getName()) && serverRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Server with name '" + dto.getName() + "' already exists");
        }

        updateEntity(server, dto);

        // If this is set as default, clear other defaults
        if (server.isDefaultServer()) {
            clearDefaultServer();
            server.setDefaultServer(true);
        }

        server = serverRepository.save(server);
        log.info("Updated PeSIT server: {}", server.getName());
        return server;
    }

    @Transactional
    public void deleteServer(String id) {
        PesitServer server = serverRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + id));
        serverRepository.delete(server);
        log.info("Deleted PeSIT server: {}", server.getName());
    }

    @Transactional
    public PesitServer setDefaultServer(String id) {
        PesitServer server = serverRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + id));

        clearDefaultServer();
        server.setDefaultServer(true);
        server = serverRepository.save(server);
        log.info("Set default PeSIT server: {}", server.getName());
        return server;
    }

    private void clearDefaultServer() {
        serverRepository.findByDefaultServerTrue().ifPresent(s -> {
            s.setDefaultServer(false);
            serverRepository.save(s);
        });
    }

    private PesitServer mapToEntity(PesitServerDto dto) {
        return PesitServer.builder()
                .name(dto.getName())
                .host(dto.getHost())
                .port(dto.getPort())
                .serverId(dto.getServerId())
                .description(dto.getDescription())
                .tlsEnabled(dto.isTlsEnabled())
                .truststorePath(dto.getTruststorePath())
                .truststorePassword(dto.getTruststorePassword()) // TODO: encrypt
                .keystorePath(dto.getKeystorePath())
                .keystorePassword(dto.getKeystorePassword()) // TODO: encrypt
                .connectionTimeout(dto.getConnectionTimeout())
                .readTimeout(dto.getReadTimeout())
                .enabled(dto.isEnabled())
                .defaultServer(dto.isDefaultServer())
                .build();
    }

    private void updateEntity(PesitServer server, PesitServerDto dto) {
        server.setName(dto.getName());
        server.setHost(dto.getHost());
        server.setPort(dto.getPort());
        server.setServerId(dto.getServerId());
        server.setDescription(dto.getDescription());
        server.setTlsEnabled(dto.isTlsEnabled());
        server.setTruststorePath(dto.getTruststorePath());
        if (dto.getTruststorePassword() != null) {
            server.setTruststorePassword(dto.getTruststorePassword()); // TODO: encrypt
        }
        server.setKeystorePath(dto.getKeystorePath());
        if (dto.getKeystorePassword() != null) {
            server.setKeystorePassword(dto.getKeystorePassword()); // TODO: encrypt
        }
        server.setConnectionTimeout(dto.getConnectionTimeout());
        server.setReadTimeout(dto.getReadTimeout());
        server.setEnabled(dto.isEnabled());
        server.setDefaultServer(dto.isDefaultServer());
    }

    public PesitServerDto mapToDto(PesitServer server) {
        return PesitServerDto.builder()
                .id(server.getId())
                .name(server.getName())
                .host(server.getHost())
                .port(server.getPort())
                .serverId(server.getServerId())
                .description(server.getDescription())
                .tlsEnabled(server.isTlsEnabled())
                .truststorePath(server.getTruststorePath())
                // Don't expose truststore password
                .keystorePath(server.getKeystorePath())
                // Don't expose keystore password
                .connectionTimeout(server.getConnectionTimeout())
                .readTimeout(server.getReadTimeout())
                .enabled(server.isEnabled())
                .defaultServer(server.isDefaultServer())
                .build();
    }
}
