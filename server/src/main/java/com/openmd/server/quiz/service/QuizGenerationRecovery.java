package com.openmd.server.quiz.service;

import com.openmd.server.quiz.config.QuizGenerationProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"openmd.quiz.enabled", "openmd.quiz.generation.enabled"},
    havingValue = "true",
    matchIfMissing = true)
class QuizGenerationRecovery {
  private final QuizGenerationPersistenceService persistence;
  private final QuizGenerationProperties properties;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  QuizGenerationRecovery(
      QuizGenerationPersistenceService persistence, QuizGenerationProperties properties) {
    this(persistence, properties, Clock.systemUTC());
  }

  QuizGenerationRecovery(
      QuizGenerationPersistenceService persistence,
      QuizGenerationProperties properties,
      Clock clock) {
    this.persistence = persistence;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${openmd.quiz.generation.recovery-interval:1m}")
  void failStaleExecutions() {
    persistence.failStaleGenerations(clock.instant().minus(properties.staleExecution()));
  }
}
