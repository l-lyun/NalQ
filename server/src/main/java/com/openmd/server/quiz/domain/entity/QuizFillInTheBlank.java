package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "quiz_fill_in_the_blanks")
public class QuizFillInTheBlank extends BaseEntity {
  @Column(name = "public_id", nullable = false, updatable = false, length = 36, unique = true)
  private String publicId;

  @Column(name = "question_id", nullable = false, updatable = false)
  private long questionId;

  @Column(name = "blank_number", nullable = false, updatable = false)
  private int number;

  protected QuizFillInTheBlank() {}

  public static QuizFillInTheBlank of(long questionId, int number) {
    QuizFillInTheBlank b = new QuizFillInTheBlank();
    b.publicId = UUID.randomUUID().toString();
    b.questionId = questionId;
    b.number = number;
    return b;
  }

  public String getPublicId() {
    return publicId;
  }

  public long getQuestionId() {
    return questionId;
  }

  public int getNumber() {
    return number;
  }
}
