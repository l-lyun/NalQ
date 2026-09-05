package com.openmd.server.quiz.domain.entity;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmd.server.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "quiz_essay_answer_guides")
public class QuizEssayAnswerGuide extends BaseTimeEntity {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Id
  @Column(name = "question_id")
  private long questionId;

  @Column(name = "model_answer", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String modelAnswer;

  @Column(name = "key_points", nullable = false, updatable = false, columnDefinition = "JSON")
  private String keyPointsJson;

  protected QuizEssayAnswerGuide() {}

  public static QuizEssayAnswerGuide of(
      long questionId, String modelAnswer, List<String> keyPoints) {
    QuizEssayAnswerGuide g = new QuizEssayAnswerGuide();
    g.questionId = questionId;
    g.modelAnswer = modelAnswer;
    try {
      g.keyPointsJson = JSON.writeValueAsString(keyPoints);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
    return g;
  }

  public long getQuestionId() {
    return questionId;
  }

  public String getModelAnswer() {
    return modelAnswer;
  }

  public List<String> getKeyPoints() {
    try {
      return JSON.readValue(keyPointsJson, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
