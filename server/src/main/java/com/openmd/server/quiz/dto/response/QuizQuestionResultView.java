package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.*;
import java.util.List;

public record QuizQuestionResultView(
    String questionId,
    int number,
    QuestionType type,
    String topic,
    String prompt,
    List<ChoiceView> choices,
    List<BlankView> blanks,
    Object response,
    Object representativeAnswer,
    GradingOutcome outcome,
    String explanation,
    String sourceExcerpt) {}
