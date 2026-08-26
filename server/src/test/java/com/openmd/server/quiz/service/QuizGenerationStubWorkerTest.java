package com.openmd.server.quiz.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.quiz.domain.type.QuestionType;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class QuizGenerationStubWorkerTest {

  private final QuizGenerationPersistenceService persistence =
      mock(QuizGenerationPersistenceService.class);
  private final TaskScheduler scheduler = mock(TaskScheduler.class);
  private final QuizGenerationStubWorker worker =
      new QuizGenerationStubWorker(persistence, scheduler);

  @Test
  void marksTheQuizSetFailedWhenTheDelayedCompletionFails() {
    AtomicReference<Runnable> scheduled = new AtomicReference<>();
    when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
        .thenAnswer(
            invocation -> {
              scheduled.set(invocation.getArgument(0));
              return mock(ScheduledFuture.class);
            });
    TemporaryQuizGenerationRequested request = request();
    doThrow(new IllegalStateException("generation failed"))
        .when(persistence)
        .completeWithTemporaryStub(
            request.userId(), request.quizSetId(), request.selectedTypes(), request.maxQuestionCount());

    worker.scheduleAfterCommit(request);
    scheduled.get().run();

    verify(persistence).failGeneration(request.userId(), request.quizSetId());
  }

  @Test
  void marksTheQuizSetFailedWhenSchedulingIsRejected() {
    TemporaryQuizGenerationRequested request = request();
    doThrow(new IllegalStateException("scheduler stopped"))
        .when(scheduler)
        .schedule(any(Runnable.class), any(Instant.class));

    worker.scheduleAfterCommit(request);

    verify(persistence).failGeneration(request.userId(), request.quizSetId());
  }

  @Test
  void failsGeneratingQuizSetsLeftByAPreviousServerProcess() {
    worker.failInterruptedGenerationsOnStartup();

    verify(persistence).failInterruptedGenerations();
  }

  private TemporaryQuizGenerationRequested request() {
    return new TemporaryQuizGenerationRequested(
        7L, "quiz-set-1", List.of(QuestionType.ESSAY), 5);
  }
}
