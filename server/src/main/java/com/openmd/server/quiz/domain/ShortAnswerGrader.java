package com.openmd.server.quiz.domain;

import com.openmd.server.quiz.domain.type.GradingOutcome;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public final class ShortAnswerGrader {

	private ShortAnswerGrader() {
	}

	public static GradingOutcome grade(String submittedAnswer, List<String> acceptedAnswers) {
		if (submittedAnswer == null || acceptedAnswers == null) {
			return GradingOutcome.INCORRECT;
		}
		String normalizedSubmission = normalize(submittedAnswer);
		if (normalizedSubmission.isEmpty()) {
			return GradingOutcome.INCORRECT;
		}
		return acceptedAnswers.stream()
			.map(ShortAnswerGrader::normalize)
			.anyMatch(normalizedSubmission::equals)
			? GradingOutcome.CORRECT
			: GradingOutcome.INCORRECT;
	}

	public static String normalize(String value) {
		String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
		StringBuilder collapsed = new StringBuilder(nfc.length());
		boolean pendingSpace = false;
		for (int offset = 0; offset < nfc.length();) {
			int codePoint = nfc.codePointAt(offset);
			offset += Character.charCount(codePoint);
			if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
				pendingSpace = collapsed.length() > 0;
				continue;
			}
			if (pendingSpace) {
				collapsed.append(' ');
				pendingSpace = false;
			}
			collapsed.appendCodePoint(codePoint);
		}
		return collapsed.toString().toLowerCase(Locale.ROOT);
	}
}
