package com.openmd.server.quiz.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openmd.quiz.generation")
public record QuizGenerationProperties(
    String model,
    String reasoningEffort,
    Duration timeout,
    int networkRetry,
    int workerCount,
    int queueCapacity,
    Duration staleExecution,
    String promptVersion) {
  public QuizGenerationProperties {
    if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");
    if (reasoningEffort == null || reasoningEffort.isBlank()) {
      throw new IllegalArgumentException("reasoningEffort is required");
    }
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    if (networkRetry < 0 || workerCount < 1 || queueCapacity < 0) {
      throw new IllegalArgumentException("quiz generation capacity settings are invalid");
    }
    if (staleExecution == null || staleExecution.isZero() || staleExecution.isNegative()) {
      throw new IllegalArgumentException("staleExecution must be positive");
    }
    if (promptVersion == null || promptVersion.isBlank()) {
      throw new IllegalArgumentException("promptVersion is required");
    }
  }
}
