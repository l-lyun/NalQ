package com.openmd.server.learningmaterial.infrastructure;

import com.openmd.server.learningmaterial.application.StoredLearningMaterial;
import com.openmd.server.learningmaterial.domain.LearningMaterial;

final class StoredLearningMaterialMapper {

	private StoredLearningMaterialMapper() {
	}

	static StoredLearningMaterial from(LearningMaterial material) {
		return new StoredLearningMaterial(
			material.getId(),
			material.getUserId(),
			material.getTitle(),
			material.getContent(),
			material.getSourceType(),
			material.getContentEditStatus(),
			material.getRequestFingerprint(),
			material.getCreatedAt()
		);
	}
}
