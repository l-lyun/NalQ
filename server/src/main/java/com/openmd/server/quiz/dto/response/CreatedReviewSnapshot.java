package com.openmd.server.quiz.dto.response;

public record CreatedReviewSnapshot(String reviewSessionId, long sourceSummaryRevision, int questionCount) {
}
