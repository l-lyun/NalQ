package com.openmd.server.learningmaterial.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.dto.command.UpdateLearningMaterialCommand;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialDetail;
import com.openmd.server.learningmaterial.error.LearningMaterialErrorCode;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.learning-material.enabled", havingValue = "true", matchIfMissing = true)
public class LearningMaterialUpdateService {

	private static final int MAX_TITLE_CODE_POINTS = 255;
	private static final int MAX_CONTENT_CODE_POINTS = 20_000;

	private final LearningMaterialRepository materials;
	private final QuizSetRepository quizSets;

	public LearningMaterialUpdateService(
		LearningMaterialRepository materials,
		QuizSetRepository quizSets
	) {
		this.materials = materials;
		this.quizSets = quizSets;
	}

	@Transactional
	public LearningMaterialDetail update(long userId, long materialId, UpdateLearningMaterialCommand command) {
		if (materialId < 1) {
			throw invalid("materialId", "materialId는 양의 정수여야 합니다.");
		}
		ValidatedUpdate update = validate(command);
		LearningMaterial material = materials.findOwnedForUpdate(materialId, userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		boolean generating = quizSets.existsByLearningMaterialIdAndUserIdAndStatus(
			materialId, userId, QuizSetStatus.GENERATING);
		if (update.contentPresent() && generating) {
			throw new BusinessException(LearningMaterialErrorCode.CONTENT_LOCKED_GENERATING);
		}

		if (update.titlePresent()) {
			material.updateTitle(update.title());
		}
		if (update.contentPresent()) {
			material.updateContent(update.content());
		}
		materials.flush();

		return detail(material, generating);
	}

	private ValidatedUpdate validate(UpdateLearningMaterialCommand command) {
		if (command == null || (!command.titlePresent() && !command.contentPresent())) {
			throw invalid("body", "title 또는 content 중 하나 이상을 보내야 합니다.");
		}

		String title = null;
		if (command.titlePresent()) {
			title = trimUnicodeWhitespace(command.title());
			if (title.isEmpty()) {
				throw invalid("title", "제목을 입력해야 합니다.");
			}
			if (codePointCount(title) > MAX_TITLE_CODE_POINTS) {
				throw invalid("title", "제목은 255자를 초과할 수 없습니다.");
			}
		}

		String content = null;
		if (command.contentPresent()) {
			content = command.content();
			if (content == null || isUnicodeBlank(content)) {
				throw invalid("content", "본문을 입력해야 합니다.");
			}
			if (codePointCount(content) > MAX_CONTENT_CODE_POINTS) {
				throw new BusinessException(
					LearningMaterialErrorCode.CONTENT_TOO_LONG,
					List.of(new FieldError("content", "본문은 20,000자를 초과할 수 없습니다."))
				);
			}
		}

		return new ValidatedUpdate(command.titlePresent(), title, command.contentPresent(), content);
	}

	private LearningMaterialDetail detail(LearningMaterial material, boolean generating) {
		return new LearningMaterialDetail(
			Long.toString(material.getId()),
			material.getTitle(),
			material.getContent(),
			codePointCount(material.getContent()),
			material.getSourceType(),
			generating ? ContentEditStatus.LOCKED_GENERATING : ContentEditStatus.EDITABLE,
			material.getCreatedAt(),
			material.getUpdatedAt()
		);
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

	private boolean isUnicodeBlank(String value) {
		return value.codePoints().allMatch(this::isUnicodeWhitespace);
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

	private record ValidatedUpdate(
		boolean titlePresent,
		String title,
		boolean contentPresent,
		String content
	) {
	}
}
