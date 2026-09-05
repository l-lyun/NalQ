package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.quiz.domain.QuizGenerationCandidate;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import com.openmd.server.quiz.domain.type.QuizSetFailureCode;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

class QuizGenerationWorkerTest {
  private final QuizGenerator generator = mock(QuizGenerator.class);
  private final QuizGenerationPersistenceService persistence =
      mock(QuizGenerationPersistenceService.class);
  private final QuizGenerationCapacity capacity = new QuizGenerationCapacity(1);
  private final Executor direct = Runnable::run;
  private final QuizGenerationWorker worker =
      new QuizGenerationWorker(
          generator, persistence, direct, new QuizGenerationTaskRegistry(capacity));

  @Test
  void supplementsOnceAndCompletesWhenCombinedCandidatesReachEightyPercent() {
    capacity.tryAcquire();
    QuizGenerationRequested request = request();
    when(persistence.markStarted(7L, "quiz-set-1")).thenReturn(true);
    when(generator.generate(any()))
        .thenReturn(
            QuizGeneratedBatch.generated(List.of(candidate("1"), candidate("2"), candidate("3"))),
            QuizGeneratedBatch.generated(List.of(candidate("4"))));

    worker.scheduleAfterCommit(request);

    verify(generator, org.mockito.Mockito.times(2)).generate(any());
    verify(persistence).complete(eq(7L), eq("quiz-set-1"), any(), eq(5));
    verify(persistence, never()).failGeneration(any(Long.class), any(), any());
    assertEquals(1, capacity.availablePermits());
  }

  @Test
  void usesSourceInsufficientOnlyWhenBothNormalResponsesAgree() {
    capacity.tryAcquire();
    when(persistence.markStarted(7L, "quiz-set-1")).thenReturn(true);
    when(generator.generate(any()))
        .thenReturn(QuizGeneratedBatch.sourceInsufficient(), QuizGeneratedBatch.sourceInsufficient());

    worker.scheduleAfterCommit(request());

    verify(persistence)
        .failGeneration(7L, "quiz-set-1", QuizSetFailureCode.SOURCE_INSUFFICIENT);
    assertEquals(1, capacity.availablePermits());
  }

  @Test
  void mapsProviderFailureToGenericGenerationFailureWithoutQualityRetry() {
    capacity.tryAcquire();
    when(persistence.markStarted(7L, "quiz-set-1")).thenReturn(true);
    when(generator.generate(any())).thenReturn(QuizGeneratedBatch.failed());

    worker.scheduleAfterCommit(request());

    verify(generator).generate(any());
    verify(persistence)
        .failGeneration(7L, "quiz-set-1", QuizSetFailureCode.GENERATION_FAILED);
    assertEquals(1, capacity.availablePermits());
  }

  @Test
  void finalizesFailureAndReleasesCapacityWhenQueueRejectsTheTask() {
    QuizGenerationCapacity rejectedCapacity = new QuizGenerationCapacity(1);
    rejectedCapacity.tryAcquire();
    Executor rejected = task -> { throw new TaskRejectedException("queue full"); };
    QuizGenerationWorker rejectedWorker =
        new QuizGenerationWorker(
            generator,
            persistence,
            rejected,
            new QuizGenerationTaskRegistry(rejectedCapacity));

    rejectedWorker.scheduleAfterCommit(request());

    verify(persistence)
        .failGeneration(7L, "quiz-set-1", QuizSetFailureCode.GENERATION_FAILED);
    verify(generator, never()).generate(any());
    assertEquals(1, rejectedCapacity.availablePermits());
  }

  private QuizGenerationRequested request() {
    return new QuizGenerationRequested(
        7L,
        "quiz-set-1",
        List.of(QuestionType.SHORT_ANSWER),
        QuizDifficulty.NORMAL,
        5,
        "동시성에 집중",
        "근거 1 근거 2 근거 3 근거 4");
  }

  private QuizGenerationCandidate candidate(String suffix) {
    return new QuizGenerationCandidate(
        QuestionType.SHORT_ANSWER,
        "주제 " + suffix,
        "문제 " + suffix,
        "해설 " + suffix,
        "근거 " + suffix,
        List.of(),
        List.of("답 " + suffix),
        List.of(),
        "",
        List.of());
  }
}
