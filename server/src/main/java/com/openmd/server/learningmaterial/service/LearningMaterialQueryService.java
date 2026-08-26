package com.openmd.server.learningmaterial.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialDetail;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialPage;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialSummary;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.learning-material.enabled", havingValue = "true", matchIfMissing = true)
public class LearningMaterialQueryService {

	private static final int MAX_PAGE_SIZE = 20;
	private static final Sort MATERIAL_ORDER = Sort.by(
		Sort.Order.desc("updatedAt"),
		Sort.Order.desc("id")
	);

	private final LearningMaterialRepository materials;
	private final QuizSetRepository quizSets;

	public LearningMaterialQueryService(
		LearningMaterialRepository materials,
		QuizSetRepository quizSets
	) {
		this.materials = materials;
		this.quizSets = quizSets;
	}

	@Transactional(readOnly = true)
	public LearningMaterialPage list(long userId, int page, int size, String query) {
		validatePagination(page, size);
		String normalizedQuery = trimUnicodeWhitespace(query);
		PageRequest pageable = PageRequest.of(page - 1, size, MATERIAL_ORDER);
		Page<LearningMaterial> result = normalizedQuery.isEmpty()
			? materials.findAllByUserId(userId, pageable)
			: materials.findAllByUserIdAndTitleContaining(userId, normalizedQuery, pageable);

		List<Long> materialIds = result.getContent().stream().map(LearningMaterial::getId).toList();
		Set<Long> generatingMaterialIds = materialIds.isEmpty()
			? Set.of()
			: new HashSet<>(quizSets.findLearningMaterialIdsByUserIdAndStatusAndLearningMaterialIdIn(
				userId, QuizSetStatus.GENERATING, materialIds));
		List<LearningMaterialSummary> items = result.getContent().stream()
			.map(material -> summary(material, generatingMaterialIds.contains(material.getId())))
			.toList();

		return new LearningMaterialPage(
			items,
			page,
			size,
			result.getTotalElements(),
			result.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	public LearningMaterialDetail detail(long userId, long materialId) {
		if (materialId < 1) {
			throw invalid("materialId", "materialId는 양의 정수여야 합니다.");
		}
		LearningMaterial material = materials.findByIdAndUserId(materialId, userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		boolean generating = quizSets.existsByLearningMaterialIdAndUserIdAndStatus(
			materialId, userId, QuizSetStatus.GENERATING);

		return new LearningMaterialDetail(
			Long.toString(material.getId()),
			material.getTitle(),
			material.getContent(),
			codePointCount(material.getContent()),
			material.getSourceType(),
			status(generating),
			material.getCreatedAt(),
			material.getUpdatedAt()
		);
	}

	private LearningMaterialSummary summary(LearningMaterial material, boolean generating) {
		return new LearningMaterialSummary(
			Long.toString(material.getId()),
			material.getTitle(),
			material.getSourceType(),
			status(generating),
			material.getUpdatedAt()
		);
	}

	private ContentEditStatus status(boolean generating) {
		return generating ? ContentEditStatus.LOCKED_GENERATING : ContentEditStatus.EDITABLE;
	}

	private void validatePagination(int page, int size) {
		if (page < 1) {
			throw invalid("page", "page는 1 이상이어야 합니다.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw invalid("size", "size는 1 이상 20 이하여야 합니다.");
		}
	}

	private String trimUnicodeWhitespace(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		int start = 0;
		int end = value.length();
		while (start < end) {
			int codePoint = value.codePointAt(start);
			if (!isUnicodeWhitespace(codePoint)) {
				break;
			}
			start += Character.charCount(codePoint);
		}
		while (end > start) {
			int codePoint = value.codePointBefore(end);
			if (!isUnicodeWhitespace(codePoint)) {
				break;
			}
			end -= Character.charCount(codePoint);
		}
		return value.substring(start, end);
	}

	private boolean isUnicodeWhitespace(int codePoint) {
		return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
	}

	private int codePointCount(String value) {
		return value.codePointCount(0, value.length());
	}

	private BusinessException invalid(String field, String reason) {
		return new BusinessException(
			CommonErrorCode.INVALID_INPUT,
			List.of(new FieldError(field, reason))
		);
	}
}
