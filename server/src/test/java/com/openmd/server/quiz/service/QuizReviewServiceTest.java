package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizAttemptQuestion;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizAttemptQuestionRepository;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import com.openmd.server.quiz.repository.QuizFillInTheBlankRepository;
import com.openmd.server.quiz.repository.QuizQuestionChoiceRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizSetRepository;
import com.openmd.server.quiz.repository.ReviewCandidateCount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QuizReviewServiceTest {
  private final QuizAttemptRepository attempts = mock(QuizAttemptRepository.class);
  private final QuizAttemptQuestionRepository attemptQuestions =
      mock(QuizAttemptQuestionRepository.class);
  private final QuizSetRepository sets = mock(QuizSetRepository.class);
  private final LearningMaterialRepository materials = mock(LearningMaterialRepository.class);
  private final QuizQuestionRepository questions = mock(QuizQuestionRepository.class);
  private final QuizQuestionChoiceRepository choices = mock(QuizQuestionChoiceRepository.class);
  private final QuizFillInTheBlankRepository blanks = mock(QuizFillInTheBlankRepository.class);
  private final QuizReviewService service =
      new QuizReviewService(
          attempts, attemptQuestions, sets, materials, questions, choices, blanks);

  @Test
  void latestUsesTheStableCompletedAtAndIdOrdering() {
    QuizSet set = quizSet(20L, 102L, "quiz-latest", "최신 퀴즈");
    LearningMaterial material = material(102L, "최신 자료");
    QuizAttempt main =
        completed("main-latest", 20L, 7L, 210L, "2026-08-28T12:00:00Z");
    when(attempts.findFirstByUserIdAndTypeAndStatusOrderByCompletedAtDescIdDesc(
            7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.of(main));
    when(sets.findById(20L)).thenReturn(Optional.of(set));
    when(materials.findByIdAndUserId(102L, 7L)).thenReturn(Optional.of(material));
    when(questions.countByQuizSetId(20L)).thenReturn(1L);
    when(attemptQuestions.findReviewCandidates(eq(210L), any())).thenReturn(List.of());
    when(attempts.findFirstByUserIdAndSourceAttemptIdAndStatusNotOrderByCreatedAtDesc(
            7L, 210L, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.empty());
    when(attempts.countByQuizSetIdAndUserIdAndTypeAndStatus(
            20L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED))
        .thenReturn(1L);

    assertEquals("main-latest", service.latest(7L).sourceAttemptId());
    verify(attempts)
        .findFirstByUserIdAndTypeAndStatusOrderByCompletedAtDescIdDesc(
            7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED);
  }

  @Test
  void candidatesExcludeTheRecentQuizAndZeroCountsThenPreferActiveReviews() {
    QuizSet recent = quizSet(10L, 101L, "quiz-recent", "최근 퀴즈");
    QuizSet active = quizSet(20L, 102L, "quiz-active", "운영체제 퀴즈");
    QuizSet newerActivity = quizSet(30L, 103L, "quiz-newer", "네트워크 퀴즈");
    QuizSet resolved = quizSet(40L, 104L, "quiz-resolved", "자료구조 퀴즈");

    QuizAttempt recentMain = completed("main-recent", 10L, 7L, 110L, "2026-08-28T12:00:00Z");
    QuizAttempt activeMain = completed("main-active", 20L, 7L, 210L, "2026-08-28T10:00:00Z");
    QuizAttempt activeReview = QuizAttempt.review(20L, 7L, 210L);
    entity(activeReview, 211L, "2026-08-28T10:30:00Z", "2026-08-28T11:00:00Z");
    QuizAttempt activePending = pending("pending-active", 20L, 7L, 212L, "2026-08-28T12:30:00Z");
    QuizAttempt newerMain = completed("main-newer", 30L, 7L, 310L, "2026-08-28T09:00:00Z");
    QuizAttempt newerPending = pending("pending-newer", 30L, 7L, 311L, "2026-08-28T13:00:00Z");
    QuizAttempt resolvedMain = completed("main-resolved", 40L, 7L, 410L, "2026-08-28T08:00:00Z");

    when(sets.findAllByUserId(7L)).thenReturn(List.of(recent, active, newerActivity, resolved));
    when(attempts.findAllByQuizSetIdInAndUserId(List.of(10L, 20L, 30L, 40L), 7L))
        .thenReturn(
            List.of(
                recentMain,
                activeMain,
                activeReview,
                activePending,
                newerMain,
                newerPending,
                resolvedMain));
    when(attemptQuestions.countReviewCandidatesByAttemptIdIn(
            eq(List.of(110L, 210L, 310L, 410L)), any()))
        .thenReturn(List.of(count(210L, 2), count(310L, 1), count(410L, 0)));
    when(materials.findAllByIdInAndUserId(any(), eq(7L)))
        .thenReturn(
            List.of(
                material(101L, "최근 자료"),
                material(102L, "운영체제"),
                material(103L, "네트워크"),
                material(104L, "자료구조")));

    var candidates = service.candidates(7L, 2).items();

    assertEquals(
        List.of("quiz-active", "quiz-newer"),
        candidates.stream().map(candidate -> candidate.quizSetId()).toList());
    var first = candidates.getFirst();
    assertEquals("운영체제 퀴즈", first.quizTitle());
    assertEquals("운영체제", first.materialTitle());
    assertEquals("main-active", first.sourceAttemptId());
    assertEquals("pending-active", first.pendingSelfAssessmentAttemptId());
    assertEquals(activeReview.getPublicId(), first.activeReviewSessionId());
    assertEquals(2, first.reviewQuestionCount());
    assertEquals(Instant.parse("2026-08-28T12:30:00Z"), first.lastLearningActivityAt());
  }

  @Test
  void candidatesUseQuizSetIdAsAStableTieBreaker() {
    QuizSet recent = quizSet(10L, 101L, "quiz-recent", "최근 퀴즈");
    QuizSet second = quizSet(20L, 102L, "quiz-b", "B 퀴즈");
    QuizSet first = quizSet(30L, 103L, "quiz-a", "A 퀴즈");
    QuizAttempt recentMain = completed("main-recent", 10L, 7L, 110L, "2026-08-28T12:00:00Z");
    QuizAttempt secondMain = completed("main-b", 20L, 7L, 210L, "2026-08-28T10:00:00Z");
    QuizAttempt firstMain = completed("main-a", 30L, 7L, 310L, "2026-08-28T10:00:00Z");

    when(sets.findAllByUserId(7L)).thenReturn(List.of(recent, second, first));
    when(attempts.findAllByQuizSetIdInAndUserId(List.of(10L, 20L, 30L), 7L))
        .thenReturn(List.of(recentMain, secondMain, firstMain));
    when(attemptQuestions.countReviewCandidatesByAttemptIdIn(
            eq(List.of(110L, 210L, 310L)), any()))
        .thenReturn(List.of(count(210L, 1), count(310L, 1)));
    when(materials.findAllByIdInAndUserId(any(), eq(7L)))
        .thenReturn(
            List.of(
                material(101L, "최근 자료"),
                material(102L, "B 자료"),
                material(103L, "A 자료")));

    assertEquals("quiz-a", service.candidates(7L, 1).items().getFirst().quizSetId());
  }

  @Test
  void candidatesExcludeTheHigherAttemptIdWhenGlobalCompletionTimesTie() {
    QuizSet olderId = quizSet(10L, 101L, "quiz-a", "A 퀴즈");
    QuizSet higherId = quizSet(20L, 102L, "quiz-b", "B 퀴즈");
    QuizAttempt olderMain =
        completed("main-a", 10L, 7L, 110L, "2026-08-28T12:00:00Z");
    QuizAttempt higherMain =
        completed("main-b", 20L, 7L, 210L, "2026-08-28T12:00:00Z");

    when(sets.findAllByUserId(7L)).thenReturn(List.of(olderId, higherId));
    when(attempts.findAllByQuizSetIdInAndUserId(List.of(10L, 20L), 7L))
        .thenReturn(List.of(olderMain, higherMain));
    when(attemptQuestions.countReviewCandidatesByAttemptIdIn(
            eq(List.of(110L, 210L)), any()))
        .thenReturn(List.of(count(110L, 1), count(210L, 1)));
    when(materials.findAllByIdInAndUserId(any(), eq(7L)))
        .thenReturn(List.of(material(101L, "A 자료")));

    assertEquals("quiz-a", service.candidates(7L, 3).items().getFirst().quizSetId());
  }

  @Test
  void candidateDoesNotExposeAnActiveReviewFromAnOlderSourceAttempt() {
    QuizSet recent = quizSet(10L, 101L, "quiz-recent", "최근 퀴즈");
    QuizSet candidate = quizSet(20L, 102L, "quiz-candidate", "복습 퀴즈");
    QuizAttempt recentMain =
        completed("main-recent", 10L, 7L, 110L, "2026-08-28T12:00:00Z");
    QuizAttempt olderMain =
        completed("main-older", 20L, 7L, 209L, "2026-08-27T09:00:00Z");
    QuizAttempt latestMain =
        completed("main-latest", 20L, 7L, 210L, "2026-08-27T10:00:00Z");
    QuizAttempt olderReview = QuizAttempt.review(20L, 7L, 209L);
    entity(olderReview, 211L, "2026-08-28T12:30:00Z", "2026-08-28T13:00:00Z");

    when(sets.findAllByUserId(7L)).thenReturn(List.of(recent, candidate));
    when(attempts.findAllByQuizSetIdInAndUserId(List.of(10L, 20L), 7L))
        .thenReturn(List.of(recentMain, olderMain, latestMain, olderReview));
    when(attemptQuestions.countReviewCandidatesByAttemptIdIn(
            eq(List.of(110L, 210L)), any()))
        .thenReturn(List.of(count(210L, 1)));
    when(materials.findAllByIdInAndUserId(any(), eq(7L)))
        .thenReturn(List.of(material(102L, "복습 자료")));

    var item = service.candidates(7L, 3).items().getFirst();

    assertEquals("main-latest", item.sourceAttemptId());
    assertNull(item.activeReviewSessionId());
    assertEquals(Instant.parse("2026-08-28T13:00:00Z"), item.lastLearningActivityAt());
  }

  @Test
  void candidatesRejectLimitOutsideOneToThree() {
    BusinessException failure =
        assertThrows(BusinessException.class, () -> service.candidates(7L, 4));

    assertEquals(CommonErrorCode.INVALID_INPUT, failure.getErrorCode());
    assertEquals("limit", failure.getFields().getFirst().field());
  }

  @Test
  void startsFromTheLatestCompletedMainOfTheSelectedQuizSet() {
    QuizAttempt selected = completed("source-selected", 20L, 7L, 210L, "2026-08-27T10:00:00Z");
    QuizAttemptQuestion candidate = QuizAttemptQuestion.main(210L, 901L, 1);
    entity(candidate, 810L, "2026-08-27T10:00:00Z", "2026-08-27T10:00:00Z");

    when(attempts.findOwnedForUpdate("source-selected", 7L)).thenReturn(Optional.of(selected));
    when(attempts.findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByCompletedAtDescIdDesc(
            20L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.of(selected));
    when(attempts.findFirstByUserIdAndSourceAttemptIdAndStatusNotOrderByCreatedAtDesc(
            7L, 210L, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.empty());
    when(attemptQuestions.findReviewCandidates(eq(210L), any())).thenReturn(List.of(candidate));
    when(attempts.saveAndFlush(any(QuizAttempt.class)))
        .thenAnswer(
            invocation -> {
              QuizAttempt review = invocation.getArgument(0);
              entity(review, 710L, "2026-08-28T11:00:00Z", "2026-08-28T11:00:00Z");
              return review;
            });
    when(attempts.findById(210L)).thenReturn(Optional.of(selected));
    when(attemptQuestions.findAllByAttemptIdOrderBySequenceNumber(710L)).thenReturn(List.of());

    var started = service.start(7L, "source-selected");

    assertNotNull(started.reviewSession().reviewSessionId());
    assertEquals("source-selected", started.reviewSession().sourceAttemptId());
  }

  @Test
  void rejectsAnOlderCompletedMainFromTheSelectedQuizSet() {
    QuizAttempt requested = completed("source-old", 20L, 7L, 210L, "2026-08-26T10:00:00Z");
    QuizAttempt latest = completed("source-latest", 20L, 7L, 220L, "2026-08-27T10:00:00Z");
    when(attempts.findOwnedForUpdate("source-old", 7L)).thenReturn(Optional.of(requested));
    when(attempts.findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByCompletedAtDescIdDesc(
            20L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.of(latest));

    BusinessException failure =
        assertThrows(BusinessException.class, () -> service.start(7L, "source-old"));

    assertEquals(QuizErrorCode.REVIEW_UNAVAILABLE, failure.getErrorCode());
  }

  private QuizSet quizSet(long id, long materialId, String publicId, String title) {
    QuizSet set = QuizSet.ready(7L, materialId, title);
    ReflectionTestUtils.setField(set, "publicId", publicId);
    entity(set, id, "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z");
    return set;
  }

  private LearningMaterial material(long id, String title) {
    LearningMaterial material =
        LearningMaterial.create(7L, title, "본문", SourceType.PASTE, new byte[32], new byte[32]);
    entity(material, id, "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z");
    return material;
  }

  private QuizAttempt completed(
      String publicId, long quizSetId, long userId, long id, String completedAt) {
    QuizAttempt attempt = QuizAttempt.main(publicId, quizSetId, userId);
    entity(attempt, id, completedAt, completedAt);
    attempt.submitted(false, Instant.parse(completedAt));
    return attempt;
  }

  private QuizAttempt pending(
      String publicId, long quizSetId, long userId, long id, String updatedAt) {
    QuizAttempt attempt = QuizAttempt.main(publicId, quizSetId, userId);
    entity(attempt, id, updatedAt, updatedAt);
    attempt.submitted(true, Instant.parse(updatedAt));
    return attempt;
  }

  private ReviewCandidateCount count(long attemptId, long reviewQuestionCount) {
    return new ReviewCandidateCount() {
      @Override
      public Long getAttemptId() {
        return attemptId;
      }

      @Override
      public long getReviewQuestionCount() {
        return reviewQuestionCount;
      }
    };
  }

  private void entity(Object entity, long id, String createdAt, String updatedAt) {
    ReflectionTestUtils.setField(entity, "id", id);
    ReflectionTestUtils.setField(entity, "createdAt", Instant.parse(createdAt));
    ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse(updatedAt));
  }
}
