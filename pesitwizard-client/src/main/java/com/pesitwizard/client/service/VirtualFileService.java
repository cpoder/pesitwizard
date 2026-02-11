package com.pesitwizard.client.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pesitwizard.client.entity.VirtualFile;
import com.pesitwizard.client.entity.VirtualFile.Direction;
import com.pesitwizard.client.repository.VirtualFileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing virtual file definitions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualFileService {

    private final VirtualFileRepository virtualFileRepository;

    public List<VirtualFile> getAllVirtualFiles() {
        return virtualFileRepository.findAll();
    }

    public List<VirtualFile> getByDirection(Direction direction) {
        return virtualFileRepository.findByDirectionIn(List.of(direction, Direction.BOTH));
    }

    public Optional<VirtualFile> getVirtualFile(String id) {
        return virtualFileRepository.findById(id);
    }

    public Optional<VirtualFile> getByName(String name) {
        return virtualFileRepository.findByName(name);
    }

    @Transactional
    public VirtualFile createVirtualFile(VirtualFile virtualFile) {
        log.info("Creating virtual file: {}", virtualFile.getName());
        return virtualFileRepository.save(virtualFile);
    }

    @Transactional
    public Optional<VirtualFile> updateVirtualFile(String id, VirtualFile updated) {
        return virtualFileRepository.findById(id)
                .map(existing -> {
                    existing.setName(updated.getName());
                    existing.setDescription(updated.getDescription());
                    existing.setDirection(updated.getDirection());
                    existing.setRecordLength(updated.getRecordLength());
                    log.info("Updated virtual file: {}", existing.getName());
                    return virtualFileRepository.save(existing);
                });
    }

    @Transactional
    public void deleteVirtualFile(String id) {
        log.info("Deleting virtual file: {}", id);
        virtualFileRepository.deleteById(id);
    }
}
