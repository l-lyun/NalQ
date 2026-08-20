package com.openmd.server.learningmaterial.application;

public record CreateLearningMaterialCommand(
	String title,
	String content,
	String sourceType
) {
}
