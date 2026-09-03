package com.openmd.server.quiz.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
class QuizGenerationStartupRecovery {
  private static final Logger log = LoggerFactory.getLogger(QuizGenerationStartupRecovery.class);

  private final QuizGenerationPersistenceService persistence;
  private final Instant startupAt;

  @Autowired
  QuizGenerationStartupRecovery(QuizGenerationPersistenceService persistence) {
    this(persistence, Instant.now());
  }

  QuizGenerationStartupRecovery(
      QuizGenerationPersistenceService persistence, Instant startupAt) {
    this.persistence = persistence;
    this.startupAt = startupAt;
  }

  @EventListener(ApplicationReadyEvent.class)
  void failInterruptedGenerationsOnStartup() {
    int failed = persistence.failInterruptedGenerations(startupAt);
    if (failed > 0) log.warn("Failed {} interrupted quiz generations", failed);
  }
}
