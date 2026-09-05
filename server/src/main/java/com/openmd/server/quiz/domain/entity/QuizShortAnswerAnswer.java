package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.ShortAnswerGrader;
import jakarta.persistence.*;

@Entity
@Table(name = "quiz_short_answer_answers")
public class QuizShortAnswerAnswer extends BaseEntity {
  @Column(name = "question_id", nullable = false, updatable = false)
  private long questionId;

  @Column(name = "answer_value", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String value;

  @Column(name = "normalized_value", nullable = false, updatable = false, length = 1000)
  private String normalizedValue;

  protected QuizShortAnswerAnswer() {}

  public static QuizShortAnswerAnswer of(long questionId, String value) {
    QuizShortAnswerAnswer a = new QuizShortAnswerAnswer();
    a.questionId = questionId;
    a.value = value;
    a.normalizedValue = ShortAnswerGrader.normalize(value);
    return a;
  }

  public long getQuestionId() {
    return questionId;
  }

  public String getValue() {
    return value;
  }

  public String getNormalizedValue() {
    return normalizedValue;
  }
}
