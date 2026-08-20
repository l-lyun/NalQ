package com.openmd.server.learningmaterial.infrastructure;

import com.openmd.server.learningmaterial.domain.LearningMaterial;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningMaterialRepository extends JpaRepository<LearningMaterial, Long> {

	Optional<LearningMaterial> findByUserIdAndIdempotencyKeyHash(long userId, byte[] idempotencyKeyHash);
}
