package com.openmd.server.quiz.dto.request;

import java.util.List;

public record SubmitQuizAttemptRequest(List<QuizResponseRequest> responses) {
}
