package com.openmd.server.learningmaterial.infrastructure;

import com.openmd.server.learningmaterial.application.LearningMaterialCreationStore;
import com.openmd.server.learningmaterial.application.NewLearningMaterial;
import com.openmd.server.learningmaterial.application.StoredLearningMaterial;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "openmd.learning-material.enabled", havingValue = "true", matchIfMissing = true)
public class JpaLearningMaterialCreationStore implements LearningMaterialCreationStore {

	private static final int LOOKUP_ATTEMPTS = 3;

	private final LearningMaterialInsertTransaction inserts;
	private final LearningMaterialRepository materials;

	public JpaLearningMaterialCreationStore(
		LearningMaterialInsertTransaction inserts,
		LearningMaterialRepository materials
	) {
		this.inserts = inserts;
		this.materials = materials;
	}

	@Override
	public StoredLearningMaterial create(NewLearningMaterial material) {
		try {
			return inserts.insert(material);
		} catch (DataIntegrityViolationException exception) {
			for (int attempt = 0; attempt < LOOKUP_ATTEMPTS; attempt++) {
				var existing = materials.findByUserIdAndIdempotencyKeyHash(
					material.userId(), material.idempotencyKeyHash()
				);
				if (existing.isPresent()) {
					return StoredLearningMaterialMapper.from(existing.get());
				}
			}
			throw exception;
		}
	}
}
