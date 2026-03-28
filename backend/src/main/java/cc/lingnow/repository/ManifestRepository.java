package cc.lingnow.repository;

import cc.lingnow.model.ProjectManifestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ManifestRepository extends JpaRepository<ProjectManifestEntity, String> {
    List<ProjectManifestEntity> findByOwnerOrderByUpdatedAtDesc(String owner);
}
