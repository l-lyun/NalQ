package com.openmd.server.quiz.dto.request;

import java.util.List;

public record QuizResponseRequest(
	String questionId,
	String selectedChoiceId,
	List<BlankAnswerRequest> blankAnswers,
	String text
) {
}
