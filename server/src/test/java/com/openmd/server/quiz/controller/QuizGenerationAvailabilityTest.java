package com.openmd.server.quiz.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.openmd.server.quiz.service.QuizGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class QuizGenerationAvailabilityTest {

  @Test
  void generationControllerIsAbsentWhenTheWorkerIsDisabled() {
    new WebApplicationContextRunner()
        .withBean(QuizGenerationService.class, () -> mock(QuizGenerationService.class))
        .withUserConfiguration(QuizGenerationController.class)
        .withPropertyValues(
            "openmd.quiz.enabled=true",
            "openmd.quiz.generation.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(QuizGenerationController.class));
  }
}
