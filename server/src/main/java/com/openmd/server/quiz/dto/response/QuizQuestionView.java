package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.QuestionType;
import java.util.List;

public record QuizQuestionView(
    String questionId,
    int number,
    QuestionType type,
    String topic,
    String prompt,
    List<ChoiceView> choices,
    List<BlankView> blanks) {}
