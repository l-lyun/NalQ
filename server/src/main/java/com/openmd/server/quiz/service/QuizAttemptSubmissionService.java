package com.openmd.server.quiz.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.*;
import com.openmd.server.quiz.domain.ShortAnswerGrader;
import com.openmd.server.quiz.domain.entity.*;
import com.openmd.server.quiz.domain.type.*;
import com.openmd.server.quiz.dto.model.QuizAttemptSubmissionResult;
import com.openmd.server.quiz.dto.request.*;
import com.openmd.server.quiz.dto.response.*;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizAttemptSubmissionService {
  private final QuizSetRepository sets;
  private final QuizQuestionRepository questions;
  private final QuizAttemptRepository attempts;
  private final QuizAttemptQuestionRepository attemptQuestions;
  private final QuizSubmittedAnswerRepository answers;
  private final QuizQuestionChoiceRepository choices;
  private final QuizShortAnswerAnswerRepository shortAnswers;
  private final QuizFillInTheBlankRepository blanks;
  private final QuizFillInTheBlankAnswerRepository blankAnswers;

  public QuizAttemptSubmissionService(
      QuizSetRepository sets,
      QuizQuestionRepository questions,
      QuizAttemptRepository attempts,
      QuizAttemptQuestionRepository attemptQuestions,
      QuizSubmittedAnswerRepository answers,
      QuizQuestionChoiceRepository choices,
      QuizShortAnswerAnswerRepository shortAnswers,
      QuizFillInTheBlankRepository blanks,
      QuizFillInTheBlankAnswerRepository blankAnswers) {
    this.sets = sets;
    this.questions = questions;
    this.attempts = attempts;
    this.attemptQuestions = attemptQuestions;
    this.answers = answers;
    this.choices = choices;
    this.shortAnswers = shortAnswers;
    this.blanks = blanks;
    this.blankAnswers = blankAnswers;
  }

  @Transactional
  public QuizAttemptSubmissionResult submit(
      long userId,
      String setPublicId,
      String requestedAttemptId,
      List<QuizResponseRequest> responses) {
    String attemptId = canonical(requestedAttemptId);
    QuizSet set = sets.findOwnedForUpdate(setPublicId, userId).orElseThrow(this::notFound);
    QuizAttempt existing = attempts.findByPublicId(attemptId).orElse(null);
    if (existing != null) {
      if (existing.getUserId() != userId
          || existing.getQuizSetId() != set.getId()
          || existing.getType() != QuizAttemptType.MAIN) throw conflictId();
      return new QuizAttemptSubmissionResult(false, response(existing));
    }
    if (set.getStatus() != QuizSetStatus.READY)
      throw new BusinessException(QuizErrorCode.ATTEMPT_CONFLICT);
    if (responses == null) throw invalid("responses", "responses가 필요합니다.");
    List<QuizQuestion> all = questions.findAllByQuizSetIdOrderByNumber(set.getId());
    Map<String, QuizResponseRequest> indexed = index(responses, all);
    QuizAttempt attempt = attempts.saveAndFlush(QuizAttempt.main(attemptId, set.getId(), userId));
    boolean pendingEssay = false;
    Instant now = Instant.now();
    for (QuizQuestion q : all) {
      QuizAttemptQuestion aq =
          attemptQuestions.saveAndFlush(
              QuizAttemptQuestion.main(attempt.getId(), q.getId(), q.getNumber()));
      QuizResponseRequest submitted = indexed.get(q.getPublicId());
      pendingEssay |= gradeAndStore(q, aq, submitted);
    }
    attempt.submitted(pendingEssay, now);
    attempts.flush();
    return new QuizAttemptSubmissionResult(true, response(attempt));
  }

  @Transactional
  public ReviewSubmission submitReview(
      long userId, String reviewId, List<QuizResponseRequest> responses) {
    QuizAttempt attempt = attempts.findOwnedForUpdate(reviewId, userId).orElseThrow(this::notFound);
    if (attempt.getType() != QuizAttemptType.REVIEW)
      throw new BusinessException(QuizErrorCode.ATTEMPT_CONFLICT);
    if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) return reviewResponse(attempt);
    if (responses == null) throw invalid("responses", "responses가 필요합니다.");
    List<QuizAttemptQuestion> snapshot =
        attemptQuestions.findAllByAttemptIdOrderBySequenceNumber(attempt.getId());
    List<QuizQuestion> all =
        snapshot.stream()
            .map(row -> questions.findById(row.getQuestionId()).orElseThrow())
            .toList();
    Map<String, QuizResponseRequest> indexed = index(responses, all);
    boolean pending = false;
    Instant now = Instant.now();
    for (int i = 0; i < snapshot.size(); i++) {
      QuizAttemptQuestion aq = snapshot.get(i);
      pending |= gradeAndStore(all.get(i), aq, indexed.get(all.get(i).getPublicId()));
      if (aq.getFinalGradingResult() == GradingOutcome.CORRECT
          && aq.getSourceAttemptQuestionId() != null)
        attemptQuestions.findById(aq.getSourceAttemptQuestionId()).orElseThrow().resolveReview(now);
    }
    attempt.submitted(pending, now);
    return reviewResponse(attempt);
  }

  private boolean gradeAndStore(QuizQuestion q, QuizAttemptQuestion aq, QuizResponseRequest r) {
    return switch (q.getType()) {
      case MULTIPLE_CHOICE -> {
        ensure(
            r == null
                || (r.selectedChoiceId() != null && r.blankAnswers() == null && r.text() == null));
        QuizQuestionChoice selected = null;
        if (r != null) {
          selected =
              choices
                  .findByPublicId(r.selectedChoiceId())
                  .orElseThrow(() -> invalid("responses", "알 수 없는 choiceId입니다."));
          if (selected.getQuestionId() != q.getId())
            throw invalid("responses", "choiceId가 해당 문제에 속하지 않습니다.");
          answers.save(QuizSubmittedAnswer.choice(aq.getId(), selected.getId()));
        }
        aq.automatic(
            selected != null && selected.isCorrect()
                ? GradingOutcome.CORRECT
                : GradingOutcome.INCORRECT);
        yield false;
      }
      case SHORT_ANSWER -> {
        ensure(r == null || (r.selectedChoiceId() == null && r.blankAnswers() == null));
        String text = r == null ? null : written(r.text());
        if (text != null) answers.save(QuizSubmittedAnswer.text(aq.getId(), text));
        aq.automatic(
            ShortAnswerGrader.grade(
                text,
                shortAnswers.findAllByQuestionIdOrderById(q.getId()).stream()
                    .map(QuizShortAnswerAnswer::getValue)
                    .toList()));
        yield false;
      }
      case FILL_IN_THE_BLANK -> {
        ensure(
            r == null
                || (r.selectedChoiceId() == null && r.text() == null && r.blankAnswers() != null));
        List<QuizFillInTheBlank> expected = blanks.findAllByQuestionIdOrderByNumber(q.getId());
        Map<Long, String> supplied = new HashMap<>();
        if (r != null)
          for (BlankAnswerRequest item : r.blankAnswers()) {
            if (item == null) throw invalid("responses", "blankAnswers가 올바르지 않습니다.");
            QuizFillInTheBlank blank =
                blanks
                    .findByPublicId(item.blankId())
                    .orElseThrow(() -> invalid("responses", "알 수 없는 blankId입니다."));
            if (blank.getQuestionId() != q.getId() || supplied.containsKey(blank.getId()))
              throw invalid("responses", "blankId가 중복되었거나 해당 문제에 속하지 않습니다.");
            supplied.put(blank.getId(), written(item.answer()));
            String value = supplied.get(blank.getId());
            if (value != null)
              answers.save(QuizSubmittedAnswer.blank(aq.getId(), blank.getId(), value));
          }
        boolean correct =
            expected.size() >= 1
                && supplied.size() == expected.size()
                && expected.stream()
                    .allMatch(
                        blank ->
                            ShortAnswerGrader.grade(
                                    supplied.get(blank.getId()),
                                    blankAnswers.findAllByBlankIdOrderById(blank.getId()).stream()
                                        .map(QuizFillInTheBlankAnswer::getValue)
                                        .toList())
                                == GradingOutcome.CORRECT);
        aq.automatic(correct ? GradingOutcome.CORRECT : GradingOutcome.INCORRECT);
        yield false;
      }
      case ESSAY -> {
        ensure(r == null || (r.selectedChoiceId() == null && r.blankAnswers() == null));
        String text = r == null ? null : written(r.text());
        if (text == null) {
          aq.selfAssess(GradingOutcome.INCORRECT);
          yield false;
        }
        answers.save(QuizSubmittedAnswer.text(aq.getId(), text));
        yield true;
      }
    };
  }

  private Map<String, QuizResponseRequest> index(
      List<QuizResponseRequest> values, List<QuizQuestion> all) {
    Set<String> known =
        all.stream().map(QuizQuestion::getPublicId).collect(java.util.stream.Collectors.toSet());
    Map<String, QuizResponseRequest> map = new HashMap<>();
    for (QuizResponseRequest r : values) {
      if (r == null
          || !known.contains(r.questionId())
          || map.putIfAbsent(r.questionId(), r) != null)
        throw invalid("responses", "알 수 없거나 중복된 questionId가 있습니다.");
    }
    return map;
  }

  private SubmittedQuizAttempt response(QuizAttempt a) {
    List<QuizAttemptQuestion> aq =
        attemptQuestions.findAllByAttemptIdOrderBySequenceNumber(a.getId());
    int graded = (int) aq.stream().filter(x -> x.getAutomaticGradingResult() != null).count();
    int correct =
        (int)
            aq.stream()
                .filter(x -> x.getAutomaticGradingResult() == GradingOutcome.CORRECT)
                .count();
    List<String> pending =
        aq.stream()
            .filter(x -> x.getFinalGradingResult() == null)
            .map(x -> questions.findById(x.getQuestionId()).orElseThrow().getPublicId())
            .toList();
    return new SubmittedQuizAttempt(
        a.getPublicId(),
        a.getStatus(),
        new GradingCount(correct, graded),
        pending,
        dbTime(a.getCreatedAt()));
  }

  private ReviewSubmission reviewResponse(QuizAttempt a) {
    SubmittedQuizAttempt value = response(a);
    return new ReviewSubmission(
        a.getPublicId(),
        a.getStatus().name(),
        value.automaticGrading(),
        value.pendingEssayQuestionIds(),
        dbTime(a.getSubmittedAt()));
  }

  private void ensure(boolean valid) {
    if (!valid) throw invalid("responses", "문제 유형과 답안 모양이 일치하지 않습니다.");
  }

  private String written(String v) {
    return v == null
            || v.codePoints()
                .allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))
        ? null
        : v;
  }

  private String canonical(String v) {
    try {
      UUID id = UUID.fromString(v);
      if (!id.toString().equalsIgnoreCase(v)) throw new IllegalArgumentException();
      return id.toString();
    } catch (Exception e) {
      throw invalid("attemptId", "attemptId는 UUID 형식이어야 합니다.");
    }
  }

  private Instant dbTime(Instant v) {
    return v == null ? null : v.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
  }

  private BusinessException invalid(String f, String m) {
    return new BusinessException(CommonErrorCode.INVALID_INPUT, List.of(new FieldError(f, m)));
  }

  private BusinessException notFound() {
    return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
  }

  private BusinessException conflictId() {
    return new BusinessException(
        QuizErrorCode.ATTEMPT_CONFLICT,
        List.of(new FieldError("attemptId", "이미 다른 사용자 또는 문제 세트에서 사용한 식별자입니다.")));
  }
}
