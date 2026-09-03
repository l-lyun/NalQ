package com.openmd.server.quiz.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.config.QuizGenerationProperties;
import com.openmd.server.quiz.domain.QuizTitlePolicy;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.dto.command.QuizGenerationConfig;
import com.openmd.server.quiz.repository.QuizSetRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
class QuizGenerationAcceptanceTransaction {
  private final LearningMaterialRepository materials;
  private final QuizSetRepository quizSets;
  private final ApplicationEventPublisher events;
  private final QuizGenerationProperties properties;

  QuizGenerationAcceptanceTransaction(
      LearningMaterialRepository materials,
      QuizSetRepository quizSets,
      ApplicationEventPublisher events,
      QuizGenerationProperties properties) {
    this.materials = materials;
    this.quizSets = quizSets;
    this.events = events;
    this.properties = properties;
  }

  @Transactional
  QuizSet accept(long userId, long materialId, QuizGenerationConfig config) {
    LearningMaterial material =
        materials
            .findOwnedForUpdate(materialId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    QuizSet quizSet =
        quizSets.saveAndFlush(
            QuizSet.generating(
                userId,
                materialId,
                QuizTitlePolicy.defaultTitle(material.getTitle()),
                properties.model(),
                properties.promptVersion()));
    events.publishEvent(
        new QuizGenerationRequested(
            userId,
            quizSet.getPublicId(),
            config.selectedTypes(),
            config.difficulty(),
            config.maxQuestionCount(),
            config.generationPrompt(),
            material.getContent()));
    return quizSet;
  }
}
