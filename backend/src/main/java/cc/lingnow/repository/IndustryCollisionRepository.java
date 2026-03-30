package cc.lingnow.repository;

import cc.lingnow.model.IndustryCollisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IndustryCollisionRepository extends JpaRepository<IndustryCollisionEntity, Long> {

    /**
     * Find existing collision by semantic hash to support hit-count increment.
     */
    Optional<IndustryCollisionEntity> findByIntentHash(String intentHash);

    /**
     * Get all collisions ordered by hit count to prioritize evolutionary patching.
     */
    java.util.List<IndustryCollisionEntity> findAllByOrderByHitCountDesc();
}
