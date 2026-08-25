package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.type.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion extends BaseEntity {
  @Column(name = "public_id", nullable = false, updatable = false, length = 36, unique = true)
  private String publicId;

  @Column(name = "quiz_set_id", nullable = false, updatable = false)
  private long quizSetId;

  @Column(name = "question_number", nullable = false, updatable = false)
  private int number;

  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", nullable = false, updatable = false, length = 32)
  private QuestionType type;

  @Column(nullable = false, length = 255, updatable = false)
  private String topic;

  @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
  private String prompt;

  @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
  private String explanation;

  @Column(name = "source_excerpt", nullable = false, columnDefinition = "TEXT", updatable = false)
  private String sourceExcerpt;

  protected QuizQuestion() {}

  public static QuizQuestion create(
      long quizSetId,
      int number,
      QuestionType type,
      String topic,
      String prompt,
      String explanation,
      String sourceExcerpt) {
    QuizQuestion q = new QuizQuestion();
    q.publicId = UUID.randomUUID().toString();
    q.quizSetId = quizSetId;
    q.number = number;
    q.type = type;
    q.topic = topic;
    q.prompt = prompt;
    q.explanation = explanation;
    q.sourceExcerpt = sourceExcerpt;
    return q;
  }

  public String getPublicId() {
    return publicId;
  }

  public long getQuizSetId() {
    return quizSetId;
  }

  public int getNumber() {
    return number;
  }

  public QuestionType getType() {
    return type;
  }

  public String getTopic() {
    return topic;
  }

  public String getPrompt() {
    return prompt;
  }

  public String getExplanation() {
    return explanation;
  }

  public String getSourceExcerpt() {
    return sourceExcerpt;
  }
}
