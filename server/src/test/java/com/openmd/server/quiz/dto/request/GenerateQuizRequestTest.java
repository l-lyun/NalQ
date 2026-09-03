package com.openmd.server.quiz.dto.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class GenerateQuizRequestTest {

  @Test
  void trimsUnicodeBoundaryWhitespaceBeforeApplyingThePromptLimit() {
    assertNull(request("\u00a0\u2003").toCommand().generationPrompt());
    assertEquals(
        "가".repeat(300),
        request("\u00a0" + "가".repeat(300) + "\u2003").toCommand().generationPrompt());
  }

  private GenerateQuizRequest request(String prompt) {
    return new GenerateQuizRequest(List.of("SHORT_ANSWER"), "NORMAL", 5, prompt);
  }
}
