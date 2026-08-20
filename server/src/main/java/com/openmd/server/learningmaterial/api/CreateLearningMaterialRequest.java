package com.openmd.server.learningmaterial.api;

import com.openmd.server.learningmaterial.application.CreateLearningMaterialCommand;

public record CreateLearningMaterialRequest(
	String title,
	String content,
	String sourceType
) {
	CreateLearningMaterialCommand toCommand() {
		return new CreateLearningMaterialCommand(title, content, sourceType);
	}
}
