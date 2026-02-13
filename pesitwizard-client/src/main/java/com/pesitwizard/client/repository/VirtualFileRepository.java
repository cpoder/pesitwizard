package com.pesitwizard.client.repository;

import com.pesitwizard.client.entity.VirtualFile;
import com.pesitwizard.client.entity.VirtualFile.Direction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualFileRepository extends JpaRepository<VirtualFile, String> {

    Optional<VirtualFile> findByName(String name);

    boolean existsByName(String name);

    List<VirtualFile> findByDirectionIn(List<Direction> directions);
}
