package com.openmd.server.learningmaterial.dto.response;

import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import com.openmd.server.learningmaterial.domain.SourceType;
import java.time.Instant;

public record LearningMaterialSummary(
	String materialId,
	String title,
	SourceType sourceType,
	ContentEditStatus contentEditStatus,
	Instant updatedAt
) {
}
