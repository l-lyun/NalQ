package com.openmd.server.quiz.dto.response;

import java.time.Instant;

public record ReviewCandidateItem(
    String quizSetId,
    String quizTitle,
    String materialTitle,
    String sourceAttemptId,
    String pendingSelfAssessmentAttemptId,
    String activeReviewSessionId,
    int reviewQuestionCount,
    Instant lastLearningActivityAt) {}
