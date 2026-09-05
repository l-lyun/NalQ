package com.openmd.server.learningmaterial.dto.request;

import com.openmd.server.learningmaterial.dto.command.CreateLearningMaterialCommand;

public record CreateLearningMaterialRequest(
	String title,
	String content,
	String sourceType
) {
	public CreateLearningMaterialCommand toCommand() {
		return new CreateLearningMaterialCommand(title, content, sourceType);
	}
}
