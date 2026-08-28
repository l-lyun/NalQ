package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.QuizTitlePolicy;
import com.openmd.server.quiz.domain.type.QuizSetFailureCode;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "quiz_sets")
public class QuizSet extends BaseEntity {

  @Column(name = "public_id", nullable = false, updatable = false, length = 36, unique = true)
  private String publicId;

  @Column(name = "user_id", nullable = false)
  private long userId;

  @Column(name = "learning_material_id", nullable = false)
  private long learningMaterialId;

  @Column(name = "quiz_title", nullable = false, length = 255)
  private String quizTitle;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private QuizSetStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_code", length = 32)
  private QuizSetFailureCode failureCode;

  protected QuizSet() {}

  private QuizSet(
      long userId, long learningMaterialId, String quizTitle, QuizSetStatus status) {
    this.publicId = UUID.randomUUID().toString();
    this.userId = userId;
    this.learningMaterialId = learningMaterialId;
    this.quizTitle = QuizTitlePolicy.normalize(quizTitle);
    this.status = status;
  }

  public static QuizSet ready(long userId, long learningMaterialId, String quizTitle) {
    return new QuizSet(userId, learningMaterialId, quizTitle, QuizSetStatus.READY);
  }

  public static QuizSet generating(long userId, long learningMaterialId, String quizTitle) {
    return new QuizSet(userId, learningMaterialId, quizTitle, QuizSetStatus.GENERATING);
  }

  public void ready() {
    status = QuizSetStatus.READY;
    failureCode = null;
  }

  public void fail(QuizSetFailureCode code) {
    if (code == null) throw new IllegalArgumentException("Failure code is required");
    status = QuizSetStatus.FAILED;
    failureCode = code;
  }

  public void rename(String quizTitle) {
    this.quizTitle = QuizTitlePolicy.normalize(quizTitle);
  }

  public String getPublicId() {
    return publicId;
  }

  public long getUserId() {
    return userId;
  }

  public long getLearningMaterialId() {
    return learningMaterialId;
  }

  public String getQuizTitle() {
    return quizTitle;
  }

  public QuizSetStatus getStatus() {
    return status;
  }

  public QuizSetFailureCode getFailureCode() {
    return failureCode;
  }
}
