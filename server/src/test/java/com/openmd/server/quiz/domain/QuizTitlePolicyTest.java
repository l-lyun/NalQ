package com.openmd.server.quiz.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class QuizTitlePolicyTest {

  @Test
  void defaultTitlePreservesSuffixAndCountsUnicodeCodePoints() {
    String materialTitle = "😀".repeat(253);

    String result = QuizTitlePolicy.defaultTitle(materialTitle);

    assertEquals(255, result.codePointCount(0, result.length()));
    assertEquals("😀".repeat(252) + " 퀴즈", result);
  }

  @Test
  void customTitleTrimsUnicodeWhitespaceAndRejectsInvalidLengths() {
    assertEquals("기말 대비", QuizTitlePolicy.normalize("\u3000기말 대비\u00a0"));
    assertThrows(IllegalArgumentException.class, () -> QuizTitlePolicy.normalize("\u3000\u00a0"));
    assertThrows(
        IllegalArgumentException.class, () -> QuizTitlePolicy.normalize("😀".repeat(256)));
  }
}
