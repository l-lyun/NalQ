package com.openmd.server.quiz.repository;

public interface ReviewCandidateCount {
  Long getAttemptId();

  long getReviewQuestionCount();
}
