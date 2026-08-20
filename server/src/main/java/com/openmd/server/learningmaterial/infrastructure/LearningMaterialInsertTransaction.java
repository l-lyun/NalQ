package com.openmd.server.learningmaterial.infrastructure;

import com.openmd.server.learningmaterial.application.NewLearningMaterial;
import com.openmd.server.learningmaterial.application.StoredLearningMaterial;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "openmd.learning-material.enabled", havingValue = "true", matchIfMissing = true)
public class LearningMaterialInsertTransaction {

	private final LearningMaterialRepository materials;
	private final EntityManager entityManager;

	public LearningMaterialInsertTransaction(
		LearningMaterialRepository materials,
		EntityManager entityManager
	) {
		this.materials = materials;
		this.entityManager = entityManager;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public StoredLearningMaterial insert(NewLearningMaterial input) {
		LearningMaterial saved = materials.saveAndFlush(LearningMaterial.create(
			input.userId(),
			input.title(),
			input.content(),
			input.sourceType(),
			input.idempotencyKeyHash(),
			input.requestFingerprint()
		));
		// The first response must use the same MySQL TIMESTAMP(6) value that a replay reads.
		entityManager.refresh(saved);
		return StoredLearningMaterialMapper.from(saved);
	}
}
