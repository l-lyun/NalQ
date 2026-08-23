package com.openmd.server.learningmaterial.repository;

import com.openmd.server.learningmaterial.dto.model.NewLearningMaterial;
import com.openmd.server.learningmaterial.dto.model.StoredLearningMaterial;

public interface LearningMaterialCreationStore {

	StoredLearningMaterial create(NewLearningMaterial material);
}
