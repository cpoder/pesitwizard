package com.pesitwizard.client.controller;

import com.pesitwizard.client.entity.VirtualFile;
import com.pesitwizard.client.entity.VirtualFile.Direction;
import com.pesitwizard.client.service.VirtualFileService;
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

/** REST API for managing virtual file definitions */
@RestController
@RequestMapping("/api/v1/virtual-files")
@RequiredArgsConstructor
public class VirtualFileController {

    private final VirtualFileService virtualFileService;

    @GetMapping
    public List<VirtualFile> getAllVirtualFiles(
            @RequestParam(required = false) Direction direction) {
        if (direction != null) {
            return virtualFileService.getByDirection(direction);
        }
        return virtualFileService.getAllVirtualFiles();
    }

    @GetMapping("/{id}")
    public ResponseEntity<VirtualFile> getVirtualFile(@PathVariable String id) {
        return virtualFileService
                .getVirtualFile(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VirtualFile createVirtualFile(@Valid @RequestBody VirtualFile virtualFile) {
        return virtualFileService.createVirtualFile(virtualFile);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VirtualFile> updateVirtualFile(
            @PathVariable String id, @Valid @RequestBody VirtualFile virtualFile) {
        return virtualFileService
                .updateVirtualFile(id, virtualFile)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVirtualFile(@PathVariable String id) {
        virtualFileService.deleteVirtualFile(id);
    }
}
