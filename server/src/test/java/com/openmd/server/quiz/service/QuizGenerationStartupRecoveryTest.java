package com.openmd.server.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class QuizGenerationStartupRecoveryTest {

  @Test
  void remainsAvailableWhenNewGenerationRequestsAreDisabled() {
    QuizGenerationPersistenceService persistence = mock(QuizGenerationPersistenceService.class);

    new ApplicationContextRunner()
        .withBean(QuizGenerationPersistenceService.class, () -> persistence)
        .withUserConfiguration(QuizGenerationStartupRecovery.class)
        .withPropertyValues(
            "openmd.quiz.enabled=true",
            "openmd.quiz.generation.enabled=false")
        .run(context -> assertThat(context).hasSingleBean(QuizGenerationStartupRecovery.class));
  }

  @Test
  void failsOnlyRowsCreatedBeforeThisProcessStarted() {
    QuizGenerationPersistenceService persistence = mock(QuizGenerationPersistenceService.class);
    Instant startupAt = Instant.parse("2026-09-03T00:00:00Z");
    QuizGenerationStartupRecovery recovery =
        new QuizGenerationStartupRecovery(persistence, startupAt);

    recovery.failInterruptedGenerationsOnStartup();

    verify(persistence).failInterruptedGenerations(startupAt);
  }
}
