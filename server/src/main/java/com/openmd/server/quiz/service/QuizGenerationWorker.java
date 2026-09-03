package com.openmd.server.quiz.service;

import com.openmd.server.quiz.domain.QuizGenerationCandidate;
import com.openmd.server.quiz.domain.QuizGenerationCandidateValidator;
import com.openmd.server.quiz.domain.QuizGenerationPolicy;
import com.openmd.server.quiz.domain.ValidatedQuizQuestion;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizSetFailureCode;
import com.openmd.server.quiz.service.QuizGenerationWork.ExcludedQuestion;
import com.openmd.server.quiz.service.QuizGenerationWork.QuizSpec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@ConditionalOnProperty(
    name = {"openmd.quiz.enabled", "openmd.quiz.generation.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class QuizGenerationWorker {
  private static final Logger log = LoggerFactory.getLogger(QuizGenerationWorker.class);

  private final QuizGenerator generator;
  private final QuizGenerationPersistenceService persistence;
  private final Executor executor;
  private final QuizGenerationTaskRegistry tasks;
  private final Instant startupAt;
  private final QuizGenerationCandidateValidator validator = new QuizGenerationCandidateValidator();
  private final QuizGenerationPolicy policy = new QuizGenerationPolicy();

  public QuizGenerationWorker(
      QuizGenerator generator,
      QuizGenerationPersistenceService persistence,
      @Qualifier("quizGenerationTaskExecutor") Executor executor,
      QuizGenerationTaskRegistry tasks) {
    this.generator = generator;
    this.persistence = persistence;
    this.executor = executor;
    this.tasks = tasks;
    this.startupAt = Instant.now();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void scheduleAfterCommit(QuizGenerationRequested request) {
    try {
      tasks.execute(executor, request.quizSetId(), () -> generate(request));
    } catch (TaskRejectedException exception) {
      log.warn("Quiz generation queue rejected quizSetId={}", request.quizSetId());
      failSafely(request, QuizSetFailureCode.GENERATION_FAILED);
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void failInterruptedGenerationsOnStartup() {
    int failed = persistence.failInterruptedGenerations(startupAt);
    if (failed > 0) log.warn("Failed {} interrupted quiz generations", failed);
  }

  private void generate(QuizGenerationRequested request) {
    try {
      if (!persistence.markStarted(request.userId(), request.quizSetId())) return;
      Map<QuestionType, Integer> preferred =
          policy.targetByType(request.selectedTypes(), request.maxQuestionCount());
      int minimum = policy.minimumAcceptableTotal(request.maxQuestionCount());
      QuizGeneratedBatch first =
          generator.generate(
              work(request, QuizGenerationWork.Task.INITIAL, request.maxQuestionCount(), minimum,
                  preferred, List.of()));
      if (first.outcome() == QuizGeneratedBatch.Outcome.FAILED) {
        failSafely(request, QuizSetFailureCode.GENERATION_FAILED);
        return;
      }
      List<ValidatedQuizQuestion> valid = validate(request, first.candidates());
      if (valid.size() >= minimum) {
        complete(request, valid);
        return;
      }

      int supplementTotal = request.maxQuestionCount() - valid.size();
      int supplementMinimum = minimum - valid.size();
      QuizGeneratedBatch second =
          generator.generate(
              work(
                  request,
                  QuizGenerationWork.Task.SUPPLEMENT,
                  supplementTotal,
                  supplementMinimum,
                  supplementTargets(request.selectedTypes(), preferred, valid, supplementTotal),
                  exclusions(valid)));
      if (second.outcome() == QuizGeneratedBatch.Outcome.FAILED) {
        failSafely(request, QuizSetFailureCode.GENERATION_FAILED);
        return;
      }
      List<QuizGenerationCandidate> combined = new ArrayList<>();
      valid.forEach(question -> combined.add(question.candidate()));
      combined.addAll(second.candidates());
      List<ValidatedQuizQuestion> finalValid = validate(request, combined);
      if (finalValid.size() >= minimum) {
        complete(request, finalValid);
      } else {
        QuizSetFailureCode code =
            first.outcome() == QuizGeneratedBatch.Outcome.SOURCE_INSUFFICIENT
                    && second.outcome() == QuizGeneratedBatch.Outcome.SOURCE_INSUFFICIENT
                ? QuizSetFailureCode.SOURCE_INSUFFICIENT
                : QuizSetFailureCode.GENERATION_FAILED;
        failSafely(request, code);
      }
    } catch (RuntimeException exception) {
      log.error("Quiz generation failed quizSetId={}", request.quizSetId(), exception);
      failSafely(request, QuizSetFailureCode.GENERATION_FAILED);
    }
  }

  int cancel(List<String> quizSetIds) {
    return tasks.cancel(quizSetIds);
  }

  private List<ValidatedQuizQuestion> validate(
      QuizGenerationRequested request, List<QuizGenerationCandidate> candidates) {
    return validator.validateAll(candidates, request.selectedTypes(), request.learningMaterial()).stream()
        .limit(request.maxQuestionCount())
        .toList();
  }

  private void complete(
      QuizGenerationRequested request, List<ValidatedQuizQuestion> valid) {
    persistence.complete(
        request.userId(),
        request.quizSetId(),
        valid.stream().map(ValidatedQuizQuestion::candidate).toList(),
        request.maxQuestionCount());
  }

  private void failSafely(QuizGenerationRequested request, QuizSetFailureCode code) {
    try {
      persistence.failGeneration(request.userId(), request.quizSetId(), code);
    } catch (RuntimeException exception) {
      log.error("Could not finalize failed quiz generation quizSetId={}", request.quizSetId());
    }
  }

  private QuizGenerationWork work(
      QuizGenerationRequested request,
      QuizGenerationWork.Task task,
      int targetTotal,
      int minimum,
      Map<QuestionType, Integer> targets,
      List<ExcludedQuestion> excluded) {
    return new QuizGenerationWork(
        task,
        new QuizSpec(request.difficulty(), targetTotal, minimum, targets),
        request.generationPrompt(),
        request.learningMaterial(),
        excluded);
  }

  private Map<QuestionType, Integer> supplementTargets(
      List<QuestionType> selected,
      Map<QuestionType, Integer> preferred,
      List<ValidatedQuizQuestion> valid,
      int targetTotal) {
    Map<QuestionType, Long> counts = new EnumMap<>(QuestionType.class);
    valid.forEach(question -> counts.merge(question.candidate().type(), 1L, Long::sum));
    Map<QuestionType, Integer> result = new EnumMap<>(QuestionType.class);
    int assigned = 0;
    for (QuestionType type : selected) {
      int deficit = Math.max(0, preferred.getOrDefault(type, 0) - counts.getOrDefault(type, 0L).intValue());
      int accepted = Math.min(deficit, targetTotal - assigned);
      if (accepted > 0) result.put(type, accepted);
      assigned += accepted;
    }
    if (assigned < targetTotal) {
      for (var entry : policy.targetByType(selected, targetTotal - assigned).entrySet()) {
        result.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }
    }
    return result;
  }

  private List<ExcludedQuestion> exclusions(List<ValidatedQuizQuestion> valid) {
    return valid.stream()
        .map(
            question -> {
              QuizGenerationCandidate value = question.candidate();
              return new ExcludedQuestion(
                  value.type(), value.topic(), value.prompt(), value.sourceExcerpt());
            })
        .toList();
  }
}
