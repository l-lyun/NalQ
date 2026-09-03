package com.openmd.server.quiz.integration.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class QuizGenerationOpenAiCredentialGuardTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(QuizGenerationOpenAiCredentialGuard.class)
          .withPropertyValues("openmd.quiz.enabled=true");

  @Test
  void rejectsThePlaceholderKeyWhenGenerationIsEnabled() {
    context
        .withPropertyValues(
            "openmd.quiz.generation.enabled=true",
            "spring.ai.openai.api-key=no-key-configured")
        .run(
            result ->
                assertThat(result)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseInstanceOf(IllegalStateException.class));
  }

  @Test
  void doesNotRequireAKeyWhenGenerationIsDisabled() {
    context
        .withPropertyValues(
            "openmd.quiz.generation.enabled=false",
            "spring.ai.openai.api-key=no-key-configured")
        .run(result -> assertThat(result).hasNotFailed());
  }
}
