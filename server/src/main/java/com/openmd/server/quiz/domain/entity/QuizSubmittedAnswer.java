package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "quiz_submitted_answers")
public class QuizSubmittedAnswer extends BaseEntity {
  @Column(name = "attempt_question_id", nullable = false, updatable = false)
  private long attemptQuestionId;

  @Column(name = "selected_choice_id", updatable = false)
  private Long selectedChoiceId;

  @Column(name = "blank_id", updatable = false)
  private Long blankId;

  @Column(name = "answer_value", columnDefinition = "TEXT", updatable = false)
  private String value;

  protected QuizSubmittedAnswer() {}

  public static QuizSubmittedAnswer choice(long aq, long choice) {
    QuizSubmittedAnswer a = new QuizSubmittedAnswer();
    a.attemptQuestionId = aq;
    a.selectedChoiceId = choice;
    return a;
  }

  public static QuizSubmittedAnswer blank(long aq, long blank, String value) {
    QuizSubmittedAnswer a = text(aq, value);
    a.blankId = blank;
    return a;
  }

  public static QuizSubmittedAnswer text(long aq, String value) {
    QuizSubmittedAnswer a = new QuizSubmittedAnswer();
    a.attemptQuestionId = aq;
    a.value = value;
    return a;
  }

  public long getAttemptQuestionId() {
    return attemptQuestionId;
  }

  public Long getSelectedChoiceId() {
    return selectedChoiceId;
  }

  public Long getBlankId() {
    return blankId;
  }

  public String getValue() {
    return value;
  }
}
