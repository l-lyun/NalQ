package com.openmd.server.learningmaterial.dto.response;

import java.util.List;

public record LearningMaterialPage(
	List<LearningMaterialSummary> items,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
	public LearningMaterialPage {
		items = List.copyOf(items);
	}
}
