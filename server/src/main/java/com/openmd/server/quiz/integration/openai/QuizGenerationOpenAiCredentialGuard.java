package com.openmd.server.quiz.integration.openai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"openmd.quiz.enabled", "openmd.quiz.generation.enabled"},
    havingValue = "true",
    matchIfMissing = true)
class QuizGenerationOpenAiCredentialGuard {
  private static final String PLACEHOLDER = "no-key-configured";

  QuizGenerationOpenAiCredentialGuard(@Value("${spring.ai.openai.api-key:}") String apiKey) {
    if (apiKey == null || apiKey.isBlank() || PLACEHOLDER.equals(apiKey)) {
      throw new IllegalStateException(
          "OPENAI_API_KEY is required when quiz generation is enabled");
    }
  }
}
