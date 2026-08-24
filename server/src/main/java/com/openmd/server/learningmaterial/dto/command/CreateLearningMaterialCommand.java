package com.openmd.server.learningmaterial.dto.command;

public record CreateLearningMaterialCommand(
	String title,
	String content,
	String sourceType
) {
}
