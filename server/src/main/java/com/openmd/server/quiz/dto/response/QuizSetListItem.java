package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.QuizSetStatus;
import java.time.Instant;

public record QuizSetListItem(
    String quizSetId,
    String quizTitle,
    String materialId,
    String materialTitle,
    QuizSetStatus status,
    Integer questionCount,
    Instant createdAt,
    Instant updatedAt,
    String latestCompletedAttemptId,
    String pendingSelfAssessmentAttemptId,
    String activeReviewSessionId,
    int reviewQuestionCount,
    Instant lastLearningActivityAt) {}
