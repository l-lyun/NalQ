package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "quiz_question_choices")
public class QuizQuestionChoice extends BaseEntity {
  @Column(name = "public_id", nullable = false, updatable = false, length = 36, unique = true)
  private String publicId;

  @Column(name = "question_id", nullable = false, updatable = false)
  private long questionId;

  @Column(name = "choice_value", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String value;

  @Column(name = "is_correct", nullable = false, updatable = false)
  private boolean correct;

  protected QuizQuestionChoice() {}

  public static QuizQuestionChoice of(long questionId, String value, boolean correct) {
    QuizQuestionChoice c = new QuizQuestionChoice();
    c.publicId = UUID.randomUUID().toString();
    c.questionId = questionId;
    c.value = value;
    c.correct = correct;
    return c;
  }

  public String getPublicId() {
    return publicId;
  }

  public long getQuestionId() {
    return questionId;
  }

  public String getValue() {
    return value;
  }

  public boolean isCorrect() {
    return correct;
  }
}
