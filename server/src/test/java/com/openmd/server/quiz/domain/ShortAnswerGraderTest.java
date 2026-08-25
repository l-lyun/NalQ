package com.openmd.server.quiz.domain;

import com.openmd.server.quiz.domain.type.GradingOutcome;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ShortAnswerGraderTest {

	@Test
	void appliesOnlyTheApprovedDeterministicNormalization() {
		assertEquals(GradingOutcome.CORRECT, ShortAnswerGrader.grade("  F\u00cdFO\u2003queue  ", List.of("fi\u0301fo queue")));
		assertEquals(GradingOutcome.INCORRECT, ShortAnswerGrader.grade("선입선출", List.of("fifo")));
		assertEquals(GradingOutcome.INCORRECT, ShortAnswerGrader.grade("first in first out", List.of("fifo")));
		assertEquals(GradingOutcome.INCORRECT, ShortAnswerGrader.grade("fifo!", List.of("fifo")));
	}

	@Test
	void doesNotDependOnTheJvmDefaultLocaleAndTreatsBlankAsUnanswered() {
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr"));
			assertEquals(GradingOutcome.CORRECT, ShortAnswerGrader.grade("FIFO", List.of("fifo")));
			assertEquals(GradingOutcome.INCORRECT, ShortAnswerGrader.grade("\u2003\t", List.of("fifo")));
		} finally {
			Locale.setDefault(previous);
		}
	}
}
