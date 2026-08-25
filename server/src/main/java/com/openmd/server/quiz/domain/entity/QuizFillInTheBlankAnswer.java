package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.ShortAnswerGrader;
import jakarta.persistence.*;

@Entity
@Table(name = "quiz_fill_in_the_blank_answers")
public class QuizFillInTheBlankAnswer extends BaseEntity {
  @Column(name = "blank_id", nullable = false, updatable = false)
  private long blankId;

  @Column(name = "answer_value", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String value;

  @Column(name = "normalized_value", nullable = false, updatable = false, length = 1000)
  private String normalizedValue;

  protected QuizFillInTheBlankAnswer() {}

  public static QuizFillInTheBlankAnswer of(long blankId, String value) {
    QuizFillInTheBlankAnswer a = new QuizFillInTheBlankAnswer();
    a.blankId = blankId;
    a.value = value;
    a.normalizedValue = ShortAnswerGrader.normalize(value);
    return a;
  }

  public long getBlankId() {
    return blankId;
  }

  public String getValue() {
    return value;
  }

  public String getNormalizedValue() {
    return normalizedValue;
  }
}
