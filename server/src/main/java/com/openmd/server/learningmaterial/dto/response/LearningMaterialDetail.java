package com.openmd.server.learningmaterial.dto.response;

import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import com.openmd.server.learningmaterial.domain.SourceType;
import java.time.Instant;

public record LearningMaterialDetail(
	String materialId,
	String title,
	String content,
	int contentLength,
	SourceType sourceType,
	ContentEditStatus contentEditStatus,
	Instant createdAt,
	Instant updatedAt
) {
}
