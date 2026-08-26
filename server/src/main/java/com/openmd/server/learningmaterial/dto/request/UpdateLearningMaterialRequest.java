package com.openmd.server.learningmaterial.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.openmd.server.learningmaterial.dto.command.UpdateLearningMaterialCommand;

public final class UpdateLearningMaterialRequest {

	private boolean titlePresent;
	private String title;
	private boolean contentPresent;
	private String content;

	@JsonSetter("title")
	public void setTitle(String title) {
		this.titlePresent = true;
		this.title = title;
	}

	@JsonSetter("content")
	public void setContent(String content) {
		this.contentPresent = true;
		this.content = content;
	}

	public UpdateLearningMaterialCommand toCommand() {
		return new UpdateLearningMaterialCommand(titlePresent, title, contentPresent, content);
	}
}
