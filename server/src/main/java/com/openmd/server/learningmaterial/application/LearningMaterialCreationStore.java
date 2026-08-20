package com.openmd.server.learningmaterial.application;

public interface LearningMaterialCreationStore {

	StoredLearningMaterial create(NewLearningMaterial material);
}
