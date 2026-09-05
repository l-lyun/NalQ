package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.domain.type.QuizSetFailureCode;
import com.openmd.server.quiz.repository.QuizAttemptQuestionRepository;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import com.openmd.server.quiz.repository.QuizFillInTheBlankRepository;
import com.openmd.server.quiz.repository.QuizQuestionChoiceRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizSetQuestionCount;
import com.openmd.server.quiz.repository.QuizSetRepository;
import com.openmd.server.quiz.repository.ReviewCandidateCount;
import java.time.Instant;
import java.util.List;
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
  void failedQuizSetUsesThePublicMessagesFromTheApiContract() {
    QuizSet insufficient = QuizSet.generating(7L, 31L, "부족 퀴즈");
    insufficient.fail(QuizSetFailureCode.SOURCE_INSUFFICIENT);
    QuizSet failed = QuizSet.generating(7L, 32L, "실패 퀴즈");
    failed.fail(QuizSetFailureCode.GENERATION_FAILED);
    when(sets.findByPublicIdAndUserId(insufficient.getPublicId(), 7L))
        .thenReturn(java.util.Optional.of(insufficient));
    when(sets.findByPublicIdAndUserId(failed.getPublicId(), 7L))
        .thenReturn(java.util.Optional.of(failed));

    assertEquals(
        "학습 자료에서 충분한 문제를 만들지 못했어요.",
        service.get(7L, insufficient.getPublicId()).failure().message());
    assertEquals(
        "문제를 만드는 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.",
        service.get(7L, failed.getPublicId()).failure().message());
  }

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

    when(sets.findAllByUserIdAndStatusNot(
            org.mockito.ArgumentMatchers.eq(7L), eq(QuizSetStatus.FAILED), any()))
        .thenReturn(new PageImpl<>(List.of(set)));
    when(materials.findAllByIdInAndUserId(List.of(31L), 7L)).thenReturn(List.of(material));
    when(attempts.findAllByQuizSetIdInAndUserId(List.of(41L), 7L))
        .thenReturn(List.of(completed, pending, review));
    QuizSetQuestionCount questionCount = mock(QuizSetQuestionCount.class);
    when(questionCount.getQuizSetId()).thenReturn(41L);
    when(questionCount.getQuestionCount()).thenReturn(10L);
    when(questions.countByQuizSetIdIn(List.of(41L))).thenReturn(List.of(questionCount));
    ReviewCandidateCount reviewCount = mock(ReviewCandidateCount.class);
    when(reviewCount.getAttemptId()).thenReturn(51L);
    when(reviewCount.getReviewQuestionCount()).thenReturn(2L);
    when(attemptQuestions.countReviewCandidatesByAttemptIdIn(eq(List.of(51L)), any()))
        .thenReturn(List.of(reviewCount));

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
    when(sets.findAllByUserIdAndStatusNot(
            org.mockito.ArgumentMatchers.eq(7L), eq(QuizSetStatus.FAILED), any()))
        .thenReturn(new PageImpl<>(List.of(set)));
    when(materials.findAllByIdInAndUserId(List.of(31L), 7L)).thenReturn(List.of(material));
    when(attempts.findAllByQuizSetIdInAndUserId(List.of(41L), 7L)).thenReturn(List.of());
    when(questions.countByQuizSetIdIn(List.of(41L))).thenReturn(List.of());

    var item = service.list(7L, 1, 6, null).items().getFirst();

    assertNull(item.latestCompletedAttemptId());
    assertNull(item.pendingSelfAssessmentAttemptId());
    assertNull(item.activeReviewSessionId());
    assertNull(item.lastLearningActivityAt());
    assertNull(item.questionCount());
    assertEquals(0, item.reviewQuestionCount());
  }

  @Test
  void listLoadsAllSupportingDataOnceForTheWholePageInsteadOfOncePerItem() {
    QuizSet first = QuizSet.generating(7L, 31L, "운영체제 퀴즈");
    entity(first, 41L, "2026-08-26T00:10:00Z", "2026-08-28T00:10:00Z");
    QuizSet second = QuizSet.generating(7L, 32L, "네트워크 퀴즈");
    entity(second, 42L, "2026-08-26T00:20:00Z", "2026-08-28T00:20:00Z");
    LearningMaterial firstMaterial =
        LearningMaterial.create(
            7L, "운영체제", "본문", SourceType.PASTE, new byte[32], new byte[32]);
    entity(firstMaterial, 31L, "2026-08-20T00:00:00Z", "2026-08-27T00:00:00Z");
    LearningMaterial secondMaterial =
        LearningMaterial.create(
            7L, "네트워크", "본문", SourceType.PASTE, new byte[32], new byte[32]);
    entity(secondMaterial, 32L, "2026-08-20T00:00:00Z", "2026-08-27T00:00:00Z");

    when(sets.findAllByUserIdAndStatusNot(eq(7L), eq(QuizSetStatus.FAILED), any()))
        .thenReturn(new PageImpl<>(List.of(first, second)));
    when(materials.findAllByIdInAndUserId(List.of(31L, 32L), 7L))
        .thenReturn(List.of(firstMaterial, secondMaterial));
    when(attempts.findAllByQuizSetIdInAndUserId(List.of(41L, 42L), 7L))
        .thenReturn(List.of());
    when(questions.countByQuizSetIdIn(List.of(41L, 42L))).thenReturn(List.of());

    assertEquals(2, service.list(7L, 1, 6, null).items().size());

    verify(materials).findAllByIdInAndUserId(List.of(31L, 32L), 7L);
    verify(attempts).findAllByQuizSetIdInAndUserId(List.of(41L, 42L), 7L);
    verify(questions).countByQuizSetIdIn(List.of(41L, 42L));
    verify(materials, never()).findByIdAndUserId(anyLong(), eq(7L));
    verify(attempts, never())
        .findFirstByQuizSetIdAndUserIdOrderByUpdatedAtDesc(anyLong(), eq(7L));
    verify(questions, never()).countByQuizSetId(anyLong());
    verify(attemptQuestions, never()).findReviewCandidates(anyLong(), any());
  }

  @Test
  void listExcludesFailedQuizSetsAtTheRepositoryBoundary() {
    when(sets.findAllByUserIdAndStatusNot(eq(7L), eq(QuizSetStatus.FAILED), any()))
        .thenReturn(new PageImpl<>(List.of()));

    assertEquals(0, service.list(7L, 1, 6, null).totalElements());

    verify(sets).findAllByUserIdAndStatusNot(eq(7L), eq(QuizSetStatus.FAILED), any());
  }

  @Test
  void focusFindsThePageContainingTheOwnedReadyQuizSet() {
    QuizSet target = QuizSet.ready(7L, 31L, "운영체제 퀴즈");
    entity(target, 41L, "2026-08-26T00:10:00Z", "2026-08-28T00:10:00Z");
    when(sets.findByPublicIdAndUserId(target.getPublicId(), 7L))
        .thenReturn(java.util.Optional.of(target));
    when(sets.countVisibleBeforeFocus(
            7L, QuizSetStatus.FAILED, target.getUpdatedAt(), target.getPublicId()))
        .thenReturn(7L);
    when(sets.findAllByUserIdAndStatusNot(
            eq(7L), eq(QuizSetStatus.FAILED),
            org.mockito.ArgumentMatchers.argThat(page -> page.getPageNumber() == 1 && page.getPageSize() == 6)))
        .thenReturn(new PageImpl<>(List.of()));

    var result = service.list(7L, 1, 6, null, target.getPublicId());

    assertEquals(2, result.page());
  }

  @Test
  void focusRejectsFailedQuizSetsAndCannotBeCombinedWithSearch() {
    QuizSet failed = QuizSet.generating(7L, 31L, "실패 퀴즈");
    failed.fail(com.openmd.server.quiz.domain.type.QuizSetFailureCode.GENERATION_FAILED);
    when(sets.findByPublicIdAndUserId(failed.getPublicId(), 7L))
        .thenReturn(java.util.Optional.of(failed));

    assertThrows(
        BusinessException.class,
        () -> service.list(7L, 1, 6, null, failed.getPublicId()));
    assertThrows(
        BusinessException.class,
        () -> service.list(7L, 1, 6, "검색", "set-1"));
  }

  private void entity(Object entity, long id, String createdAt, String updatedAt) {
    ReflectionTestUtils.setField(entity, "id", id);
    ReflectionTestUtils.setField(entity, "createdAt", Instant.parse(createdAt));
    ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse(updatedAt));
  }
}
