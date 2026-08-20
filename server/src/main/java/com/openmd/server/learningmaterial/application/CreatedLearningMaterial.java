package com.openmd.server.learningmaterial.application;

import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import java.time.Instant;

public record CreatedLearningMaterial(
	String materialId,
	String title,
	int contentLength,
	ContentEditStatus contentEditStatus,
	Instant createdAt
) {
}
