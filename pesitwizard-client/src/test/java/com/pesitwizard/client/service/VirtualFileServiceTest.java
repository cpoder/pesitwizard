package com.pesitwizard.client.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.client.entity.VirtualFile;
import com.pesitwizard.client.entity.VirtualFile.Direction;
import com.pesitwizard.client.repository.VirtualFileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualFileServiceTest {

    @Mock private VirtualFileRepository virtualFileRepository;

    private VirtualFileService virtualFileService;

    @BeforeEach
    void setUp() {
        virtualFileService = new VirtualFileService(virtualFileRepository);
    }

    @Test
    void getAllVirtualFiles_returnsList() {
        VirtualFile vf = VirtualFile.builder().id("1").name("DATA_FILE").build();
        when(virtualFileRepository.findAll()).thenReturn(List.of(vf));

        assertThat(virtualFileService.getAllVirtualFiles()).hasSize(1);
    }

    @Test
    void getByDirection_returnsBothAndMatching() {
        VirtualFile vf = VirtualFile.builder().id("1").name("F1").direction(Direction.SEND).build();
        when(virtualFileRepository.findByDirectionIn(List.of(Direction.SEND, Direction.BOTH)))
                .thenReturn(List.of(vf));

        List<VirtualFile> result = virtualFileService.getByDirection(Direction.SEND);
        assertThat(result).hasSize(1);
    }

    @Test
    void getVirtualFile_found() {
        VirtualFile vf = VirtualFile.builder().id("1").name("F1").build();
        when(virtualFileRepository.findById("1")).thenReturn(Optional.of(vf));

        assertThat(virtualFileService.getVirtualFile("1")).isPresent();
    }

    @Test
    void getVirtualFile_notFound() {
        when(virtualFileRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(virtualFileService.getVirtualFile("missing")).isEmpty();
    }

    @Test
    void getByName_found() {
        VirtualFile vf = VirtualFile.builder().id("1").name("DATA").build();
        when(virtualFileRepository.findByName("DATA")).thenReturn(Optional.of(vf));

        assertThat(virtualFileService.getByName("DATA")).isPresent();
    }

    @Test
    void createVirtualFile_saves() {
        VirtualFile vf = VirtualFile.builder().name("NEW_FILE").build();
        when(virtualFileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VirtualFile result = virtualFileService.createVirtualFile(vf);
        assertThat(result.getName()).isEqualTo("NEW_FILE");
    }

    @Test
    void updateVirtualFile_found_updates() {
        VirtualFile existing =
                VirtualFile.builder().id("1").name("OLD").direction(Direction.SEND).build();
        VirtualFile updated =
                VirtualFile.builder()
                        .name("NEW")
                        .direction(Direction.RECEIVE)
                        .description("desc")
                        .recordLength(512)
                        .build();
        when(virtualFileRepository.findById("1")).thenReturn(Optional.of(existing));
        when(virtualFileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<VirtualFile> result = virtualFileService.updateVirtualFile("1", updated);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("NEW");
        assertThat(result.get().getDirection()).isEqualTo(Direction.RECEIVE);
    }

    @Test
    void updateVirtualFile_notFound_returnsEmpty() {
        when(virtualFileRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(virtualFileService.updateVirtualFile("missing", new VirtualFile())).isEmpty();
    }

    @Test
    void deleteVirtualFile_delegatesToRepository() {
        virtualFileService.deleteVirtualFile("1");
        verify(virtualFileRepository).deleteById("1");
    }
}
