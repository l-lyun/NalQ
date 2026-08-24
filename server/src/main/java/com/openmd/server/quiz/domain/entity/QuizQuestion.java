package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.ShortAnswerGrader;
import com.openmd.server.quiz.domain.type.QuestionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion extends BaseEntity {

	@Column(name = "public_id", nullable = false, updatable = false, length = 36, unique = true)
	private String publicId;

	@Column(name = "quiz_set_id", nullable = false)
	private long quizSetId;

	@Column(name = "question_number", nullable = false)
	private int number;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private QuestionType type;

	@Column(nullable = false, length = 255)
	private String topic;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String prompt;

	@Column(name = "representative_answer", nullable = false, columnDefinition = "TEXT")
	private String representativeAnswer;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String explanation;

	@Column(name = "source_excerpt", nullable = false, columnDefinition = "TEXT")
	private String sourceExcerpt;

	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "question_id", nullable = false, updatable = false)
	private List<ShortAnswerAcceptedAnswer> acceptedAnswers = new ArrayList<>();

	protected QuizQuestion() {
	}

	public static QuizQuestion shortAnswer(
		long quizSetId,
		int number,
		String topic,
		String prompt,
		String representativeAnswer,
		List<String> acceptedAnswers,
		String explanation,
		String sourceExcerpt
	) {
		if (acceptedAnswers == null || acceptedAnswers.isEmpty()) {
			throw new IllegalArgumentException("A short-answer question needs accepted answers");
		}
		LinkedHashMap<String, String> unique = new LinkedHashMap<>();
		for (String answer : acceptedAnswers) {
			String normalized = answer == null ? "" : ShortAnswerGrader.normalize(answer);
			if (normalized.isEmpty()) {
				throw new IllegalArgumentException("Accepted answers must not normalize to blank");
			}
			unique.putIfAbsent(normalized, answer);
		}
		QuizQuestion question = new QuizQuestion();
		question.publicId = UUID.randomUUID().toString();
		question.quizSetId = quizSetId;
		question.number = number;
		question.type = QuestionType.SHORT_ANSWER;
		question.topic = topic;
		question.prompt = prompt;
		question.representativeAnswer = representativeAnswer;
		question.explanation = explanation;
		question.sourceExcerpt = sourceExcerpt;
		question.acceptedAnswers = unique.entrySet().stream()
			.map(entry -> ShortAnswerAcceptedAnswer.of(entry.getValue(), entry.getKey()))
			.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		return question;
	}

	public String getPublicId() { return publicId; }
	public long getQuizSetId() { return quizSetId; }
	public int getNumber() { return number; }
	public QuestionType getType() { return type; }
	public String getTopic() { return topic; }
	public String getPrompt() { return prompt; }
	public String getRepresentativeAnswer() { return representativeAnswer; }
	public String getExplanation() { return explanation; }
	public String getSourceExcerpt() { return sourceExcerpt; }
	public List<String> acceptedAnswerValues() {
		return acceptedAnswers.stream().map(ShortAnswerAcceptedAnswer::getAnswer).toList();
	}
}
