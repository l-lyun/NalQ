package com.openmd.server.quiz.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizGenerationStubWorker {
  private static final Logger log = LoggerFactory.getLogger(QuizGenerationStubWorker.class);
  private static final int DELAY_SECONDS = 3;

  private final QuizGenerationPersistenceService persistence;
  private final TaskScheduler scheduler;

  public QuizGenerationStubWorker(
      QuizGenerationPersistenceService persistence,
      @Qualifier("quizGenerationTaskScheduler") TaskScheduler scheduler) {
    this.persistence = persistence;
    this.scheduler = scheduler;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void scheduleAfterCommit(TemporaryQuizGenerationRequested request) {
    try {
      scheduler.schedule(
          () -> completeOrFail(request), Instant.now().plusSeconds(DELAY_SECONDS));
    } catch (RuntimeException exception) {
      fail(request, exception);
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void failInterruptedGenerationsOnStartup() {
    int failed = persistence.failInterruptedGenerations();
    if (failed > 0) {
      log.warn("Failed {} quiz generations interrupted by a previous server process", failed);
    }
  }

  private void completeOrFail(TemporaryQuizGenerationRequested request) {
    try {
      persistence.completeWithTemporaryStub(
          request.userId(),
          request.quizSetId(),
          request.selectedTypes(),
          request.maxQuestionCount());
    } catch (RuntimeException exception) {
      fail(request, exception);
    }
  }

  private void fail(TemporaryQuizGenerationRequested request, RuntimeException cause) {
    log.error("Temporary quiz generation failed for quizSetId={}", request.quizSetId(), cause);
    try {
      persistence.failGeneration(request.userId(), request.quizSetId());
    } catch (RuntimeException failure) {
      cause.addSuppressed(failure);
      log.error(
          "Could not finalize failed temporary quiz generation for quizSetId={}",
          request.quizSetId(),
          failure);
    }
  }
}
