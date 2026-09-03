package com.openmd.server.quiz.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.quiz.config.QuizGenerationProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizGenerationRecoveryTest {

  @Test
  void cancelsTrackedWorkAfterTheStaleRowsAreFailed() {
    QuizGenerationPersistenceService persistence = mock(QuizGenerationPersistenceService.class);
    QuizGenerationWorker worker = mock(QuizGenerationWorker.class);
    Instant now = Instant.parse("2026-09-03T12:00:00Z");
    QuizGenerationProperties properties = new QuizGenerationProperties(
        "gpt-5.6-luna",
        "low",
        Duration.ofSeconds(60),
        1,
        4,
        20,
        Duration.ofMinutes(10),
        "quiz-generation-v1");
    when(persistence.failStaleGenerations(now.minus(Duration.ofMinutes(10))))
        .thenReturn(List.of("quiz-set-1"));
    QuizGenerationRecovery recovery = new QuizGenerationRecovery(
        persistence, properties, worker, Clock.fixed(now, ZoneOffset.UTC));

    recovery.failStaleExecutions();

    verify(worker).cancel(List.of("quiz-set-1"));
  }
}
