package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.QuizSetStatus;
import java.util.List;

public record QuizSetView(
    String quizSetId,
    String materialId,
    String quizTitle,
    QuizSetStatus status,
    Integer pollAfterSeconds,
    List<QuizQuestionView> questions,
    QuizFailureView failure) {}
