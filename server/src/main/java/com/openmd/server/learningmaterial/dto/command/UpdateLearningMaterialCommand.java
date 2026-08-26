package com.openmd.server.learningmaterial.dto.command;

public record UpdateLearningMaterialCommand(
	boolean titlePresent,
	String title,
	boolean contentPresent,
	String content
) {
}
