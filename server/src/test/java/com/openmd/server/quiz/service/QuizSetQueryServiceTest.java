package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizAttemptQuestion;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.repository.QuizAttemptQuestionRepository;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import com.openmd.server.quiz.repository.QuizFillInTheBlankRepository;
import com.openmd.server.quiz.repository.QuizQuestionChoiceRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

class QuizSetQueryServiceTest {
  private final QuizSetRepository sets = mock(QuizSetRepository.class);
  private final QuizQuestionRepository questions = mock(QuizQuestionRepository.class);
  private final QuizQuestionChoiceRepository choices = mock(QuizQuestionChoiceRepository.class);
  private final QuizFillInTheBlankRepository blanks = mock(QuizFillInTheBlankRepository.class);
  private final LearningMaterialRepository materials = mock(LearningMaterialRepository.class);
  private final QuizAttemptRepository attempts = mock(QuizAttemptRepository.class);
  private final QuizAttemptQuestionRepository attemptQuestions =
      mock(QuizAttemptQuestionRepository.class);
  private final QuizSetQueryService service =
      new QuizSetQueryService(
          sets, questions, choices, blanks, materials, attempts, attemptQuestions);

  @Test
  void listProjectsTheIdsNeededToChooseTheNextLearningScreen() {
    QuizSet set = QuizSet.ready(7L, 31L, "운영체제 퀴즈");
    entity(set, 41L, "2026-08-26T00:10:00Z", "2026-08-28T00:10:00Z");
    LearningMaterial material =
        LearningMaterial.create(
            7L, "운영체제", "본문", SourceType.PASTE, new byte[32], new byte[32]);
    entity(material, 31L, "2026-08-20T00:00:00Z", "2026-08-27T00:00:00Z");
    QuizAttempt completed = QuizAttempt.main("main-complete", 41L, 7L);
    entity(completed, 51L, "2026-08-27T00:00:00Z", "2026-08-27T01:00:00Z");
    completed.submitted(false, Instant.parse("2026-08-27T01:00:00Z"));
    QuizAttempt pending = QuizAttempt.main("main-pending", 41L, 7L);
    entity(pending, 52L, "2026-08-28T00:00:00Z", "2026-08-28T01:00:00Z");
    pending.submitted(true, Instant.parse("2026-08-28T01:00:00Z"));
    QuizAttempt review = QuizAttempt.review(41L, 7L, 51L);
    entity(review, 53L, "2026-08-28T02:00:00Z", "2026-08-28T03:00:00Z");

    when(sets.findAllByUserId(org.mockito.ArgumentMatchers.eq(7L), any()))
        .thenReturn(new PageImpl<>(List.of(set)));
    when(materials.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(material));
    when(questions.countByQuizSetId(41L)).thenReturn(10L);
    when(attempts.findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByCompletedAtDesc(
            41L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.of(completed));
    when(attempts.findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByUpdatedAtDesc(
            41L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.SELF_ASSESSMENT_REQUIRED))
        .thenReturn(Optional.of(pending));
    when(attempts.findFirstByQuizSetIdAndUserIdAndTypeAndStatusNotOrderByUpdatedAtDesc(
            41L, 7L, QuizAttemptType.REVIEW, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.of(review));
    when(attempts.findFirstByQuizSetIdAndUserIdOrderByUpdatedAtDesc(41L, 7L))
        .thenReturn(Optional.of(review));
    when(attemptQuestions.findReviewCandidates(any(Long.class), any()))
        .thenReturn(List.of(mock(QuizAttemptQuestion.class), mock(QuizAttemptQuestion.class)));

    var item = service.list(7L, 1, 6, null).items().getFirst();

    assertEquals("main-complete", item.latestCompletedAttemptId());
    assertEquals("main-pending", item.pendingSelfAssessmentAttemptId());
    assertEquals(review.getPublicId(), item.activeReviewSessionId());
    assertEquals(2, item.reviewQuestionCount());
    assertEquals(Instant.parse("2026-08-28T03:00:00Z"), item.lastLearningActivityAt());
  }

  @Test
  void listUsesNullNavigationIdsAndZeroReviewCountBeforeTheFirstAttempt() {
    QuizSet set = QuizSet.generating(7L, 31L, "운영체제 퀴즈");
    entity(set, 41L, "2026-08-26T00:10:00Z", "2026-08-28T00:10:00Z");
    LearningMaterial material =
        LearningMaterial.create(
            7L, "운영체제", "본문", SourceType.PASTE, new byte[32], new byte[32]);
    entity(material, 31L, "2026-08-20T00:00:00Z", "2026-08-27T00:00:00Z");
    when(sets.findAllByUserId(org.mockito.ArgumentMatchers.eq(7L), any()))
        .thenReturn(new PageImpl<>(List.of(set)));
    when(materials.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(material));

    var item = service.list(7L, 1, 6, null).items().getFirst();

    assertNull(item.latestCompletedAttemptId());
    assertNull(item.pendingSelfAssessmentAttemptId());
    assertNull(item.activeReviewSessionId());
    assertNull(item.lastLearningActivityAt());
    assertNull(item.questionCount());
    assertEquals(0, item.reviewQuestionCount());
  }

  private void entity(Object entity, long id, String createdAt, String updatedAt) {
    ReflectionTestUtils.setField(entity, "id", id);
    ReflectionTestUtils.setField(entity, "createdAt", Instant.parse(createdAt));
    ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse(updatedAt));
  }
}
