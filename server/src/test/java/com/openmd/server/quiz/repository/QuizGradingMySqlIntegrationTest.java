package com.openmd.server.quiz.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.QuizGenerationCandidate;
import com.openmd.server.quiz.domain.QuizGenerationCandidate.BlankCandidate;
import com.openmd.server.quiz.domain.QuizGenerationCandidate.ChoiceCandidate;
import com.openmd.server.quiz.domain.entity.QuizQuestion;
import com.openmd.server.quiz.domain.entity.QuizQuestionChoice;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.GradingMethod;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.dto.command.QuizGenerationConfig;
import com.openmd.server.quiz.dto.request.BlankAnswerRequest;
import com.openmd.server.quiz.dto.request.QuizResponseRequest;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.service.EssayAssessmentService;
import com.openmd.server.quiz.service.QuizAttemptResultService;
import com.openmd.server.quiz.service.QuizAttemptSubmissionService;
import com.openmd.server.quiz.service.QuizGenerationPersistenceService;
import com.openmd.server.quiz.service.QuizGenerationService;
import com.openmd.server.quiz.service.QuizReviewService;
import com.openmd.server.quiz.service.ShortAnswerGradingService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
@SpringBootTest(
    properties = {
      "openmd.auth.enabled=false",
      "spring.jpa.open-in-view=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
    })
class QuizGradingMySqlIntegrationTest {
  private static final String FAIL_ANSWER = "__force_answer_insert_failure__";
  private static final AtomicInteger DIGEST_SEED = new AtomicInteger();

  @Container
  static final MySQLContainer MYSQL =
      new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("openmd")
          .withUsername("openmd")
          .withPassword("openmd")
          .withStartupTimeout(Duration.ofMinutes(2));

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired LearningMaterialRepository materials;
  @Autowired QuizSetRepository sets;
  @Autowired QuizQuestionRepository questions;
  @Autowired QuizQuestionChoiceRepository choices;
  @Autowired QuizFillInTheBlankRepository blanks;
  @Autowired QuizAttemptRepository attempts;
  @Autowired QuizAttemptQuestionRepository attemptQuestions;
  @Autowired QuizGenerationPersistenceService generation;
  @Autowired QuizGenerationService generationAcceptance;
  @Autowired QuizAttemptSubmissionService submissions;
  @Autowired QuizAttemptResultService results;
  @Autowired ShortAnswerGradingService gradings;
  @Autowired EssayAssessmentService essayAssessments;
  @Autowired QuizReviewService reviews;

  @BeforeEach
  void clear() {
    dropFailureCheck();
    jdbc.update("DELETE FROM quiz_submitted_answers");
    jdbc.update("DELETE FROM quiz_attempt_questions WHERE source_attempt_question_id IS NOT NULL");
    jdbc.update("DELETE FROM quiz_attempt_questions");
    jdbc.update("DELETE FROM quiz_attempts WHERE source_attempt_id IS NOT NULL");
    for (String table :
        List.of(
            "quiz_attempts",
            "quiz_fill_in_the_blank_answers",
            "quiz_fill_in_the_blanks",
            "quiz_essay_answer_guides",
            "quiz_short_answer_answers",
            "quiz_question_choices",
            "quiz_questions",
            "quiz_sets",
            "learning_materials",
            "users")) {
      jdbc.update("DELETE FROM " + table);
    }
  }

  @Test
  void acceptsAndFindsAnActiveGenerationWithoutPersistingRequestedConfig() {
    Fixture owner = fixture();
    Fixture other = fixture();
    QuizGenerationConfig config =
        new QuizGenerationConfig(
            List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.ESSAY), QuizDifficulty.NORMAL, 10);

    var accepted =
        generationAcceptance.accept(owner.userId(), Long.toString(owner.materialId()), config);

    assertEquals(QuizSetStatus.GENERATING, accepted.status());
    assertEquals(config.selectedTypes(), accepted.requestedConfig().selectedTypes());
    assertEquals(
        accepted.quizSetId(),
        generationAcceptance.active(owner.userId(), Long.toString(owner.materialId())).quizSetId());
    assertEquals(
        0,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema=DATABASE() AND table_name='quiz_sets'
              AND column_name IN ('selected_types','difficulty','max_question_count','requested_config')
            """,
            Integer.class));

    BusinessException active =
        assertThrows(
            BusinessException.class,
            () ->
                generationAcceptance.accept(
                    owner.userId(), Long.toString(owner.materialId()), config));
    assertEquals(QuizErrorCode.GENERATION_ACTIVE, active.getErrorCode());

    BusinessException foreign =
        assertThrows(
            BusinessException.class,
            () -> generationAcceptance.active(other.userId(), Long.toString(owner.materialId())));
    assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, foreign.getErrorCode());
  }

  @Test
  void rejectsInvalidGenerationConfigWithoutCreatingAQuizSet() {
    Fixture fixture = fixture();
    QuizGenerationConfig duplicateTypes =
        new QuizGenerationConfig(
            List.of(QuestionType.ESSAY, QuestionType.ESSAY), QuizDifficulty.NORMAL, 10);

    BusinessException invalid =
        assertThrows(
            BusinessException.class,
            () ->
                generationAcceptance.accept(
                    fixture.userId(), Long.toString(fixture.materialId()), duplicateTypes));

    assertEquals(CommonErrorCode.INVALID_INPUT, invalid.getErrorCode());
    assertEquals("selectedTypes", invalid.getFields().getFirst().field());
    assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_sets", Long.class));
  }

  @Test
  void validatesPersistsRenumbersAndGradesAllAutomaticTypes() {
    Fixture fixture = fixture();
    QuizSet set = sets.saveAndFlush(QuizSet.generating(fixture.userId(), fixture.materialId()));
    assertEquals(
        4,
        generation.complete(
            fixture.userId(),
            set.getPublicId(),
            List.of(multipleChoice(2), multipleChoice(3), fillBlank(), shortAnswer(), essay()),
            15));

    List<QuizQuestion> stored = questions.findAllByQuizSetIdOrderByNumber(set.getId());
    assertEquals(List.of(1, 2, 3, 4), stored.stream().map(QuizQuestion::getNumber).toList());
    QuizQuestion multipleChoice = stored.get(0);
    QuizQuestion blank = stored.get(1);
    QuizQuestion shortQuestion = stored.get(2);
    QuizQuestion essayQuestion = stored.get(3);
    String choiceId =
        choices.findAllByQuestionIdOrderById(multipleChoice.getId()).stream()
            .filter(QuizQuestionChoice::isCorrect)
            .findFirst()
            .orElseThrow()
            .getPublicId();
    String blankId =
        blanks.findAllByQuestionIdOrderByNumber(blank.getId()).getFirst().getPublicId();

    var submission =
        submissions.submit(
            fixture.userId(),
            set.getPublicId(),
            uuid(1),
            List.of(
                new QuizResponseRequest(multipleChoice.getPublicId(), choiceId, null, null),
                new QuizResponseRequest(
                    blank.getPublicId(),
                    null,
                    List.of(new BlankAnswerRequest(blankId, " fifo ")),
                    null),
                new QuizResponseRequest(shortQuestion.getPublicId(), null, null, "FiFo"),
                new QuizResponseRequest(essayQuestion.getPublicId(), null, null, "내 설명")));

    assertEquals(3, submission.attempt().automaticGrading().correctQuestionCount());
    assertEquals(
        List.of(essayQuestion.getPublicId()), submission.attempt().pendingEssayQuestionIds());
    assertEquals(
        0,
        essayAssessments
            .assessMain(fixture.userId(), uuid(1), essayQuestion.getPublicId(), "PARTIAL")
            .remainingSelfAssessmentCount());
    assertEquals(
        QuizAttemptStatus.COMPLETED,
        essayAssessments
            .assessMain(fixture.userId(), uuid(1), essayQuestion.getPublicId(), "PARTIAL")
            .status());
    assertThrows(
        BusinessException.class,
        () ->
            essayAssessments.assessMain(
                fixture.userId(), uuid(1), essayQuestion.getPublicId(), "CORRECT"));
    assertEquals(
        3,
        results.result(fixture.userId(), uuid(1)).summary().scoredGrading().correctQuestionCount());
    assertEquals(
        0L,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM quiz_questions WHERE question_number=8", Long.class));
  }

  @Test
  void sameUuidRetriesWithoutApplyingTheSecondBodyAndRejectsForeignReuse() {
    ReadyQuiz first = readyShortAnswer(fixture());
    Fixture otherOwner = fixture();
    ReadyQuiz second = readyShortAnswer(otherOwner);

    assertTrue(submissions.submit(first.userId(), first.setId(), uuid(2), List.of()).created());
    assertFalse(
        submissions
            .submit(
                first.userId(),
                first.setId(),
                uuid(2),
                List.of(new QuizResponseRequest(first.questionId(), null, null, "fifo")))
            .created());
    assertEquals(
        GradingOutcome.INCORRECT,
        results.result(first.userId(), uuid(2)).questionResults().getFirst().outcome());

    BusinessException reused =
        assertThrows(
            BusinessException.class,
            () -> submissions.submit(otherOwner.userId(), second.setId(), uuid(2), List.of()));
    assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, reused.getErrorCode());
    assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts", Long.class));
  }

  @Test
  void generationPersistsOnlyTheFirstValidCandidatesUpToTheRequestedMaximum() {
    Fixture fixture = fixture();
    QuizSet set = sets.saveAndFlush(QuizSet.generating(fixture.userId(), fixture.materialId()));

    int count =
        generation.complete(
            fixture.userId(),
            set.getPublicId(),
            List.of(multipleChoice(2), shortAnswer(), essay(), fillBlank()),
            2);

    assertEquals(2, count);
    assertEquals(
        List.of(QuestionType.SHORT_ANSWER, QuestionType.ESSAY),
        questions.findAllByQuizSetIdOrderByNumber(set.getId()).stream()
            .map(QuizQuestion::getType)
            .toList());
  }

  @Test
  void rejectsMalformedUuidNonReadySetAndCrossQuestionChoiceAndBlankIds() {
    ReadyQuiz owner = readyShortAnswer(fixture());
    BusinessException malformed =
        assertThrows(
            BusinessException.class,
            () -> submissions.submit(owner.userId(), owner.setId(), "not-a-uuid", List.of()));
    assertEquals(CommonErrorCode.INVALID_INPUT, malformed.getErrorCode());

    QuizSet originalSet = sets.findByPublicIdAndUserId(owner.setId(), owner.userId()).orElseThrow();
    QuizSet set =
        sets.saveAndFlush(QuizSet.generating(owner.userId(), originalSet.getLearningMaterialId()));
    BusinessException notReady =
        assertThrows(
            BusinessException.class,
            () -> submissions.submit(owner.userId(), set.getPublicId(), uuid(3), List.of()));
    assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, notReady.getErrorCode());

    ReadyQuiz left = readyMultipleChoice(fixture());
    ReadyQuiz right =
        readyMultipleChoice(
            new Fixture(left.userId(), materials.saveAndFlush(material(left.userId())).getId()));
    QuizQuestion foreignQuestion =
        questions
            .findByPublicIdAndQuizSetId(
                right.questionId(),
                sets.findByPublicIdAndUserId(right.setId(), right.userId()).orElseThrow().getId())
            .orElseThrow();
    String foreignChoice =
        choices.findAllByQuestionIdOrderById(foreignQuestion.getId()).getFirst().getPublicId();
    assertThrows(
        BusinessException.class,
        () ->
            submissions.submit(
                left.userId(),
                left.setId(),
                uuid(4),
                List.of(new QuizResponseRequest(left.questionId(), foreignChoice, null, null))));

    ReadyQuiz leftBlank = ready(fixture(), fillBlank());
    ReadyQuiz rightBlank =
        ready(
            new Fixture(
                leftBlank.userId(), materials.saveAndFlush(material(leftBlank.userId())).getId()),
            fillBlank());
    QuizSet rightBlankSet =
        sets.findByPublicIdAndUserId(rightBlank.setId(), rightBlank.userId()).orElseThrow();
    QuizQuestion rightBlankQuestion =
        questions
            .findByPublicIdAndQuizSetId(rightBlank.questionId(), rightBlankSet.getId())
            .orElseThrow();
    String foreignBlank =
        blanks
            .findAllByQuestionIdOrderByNumber(rightBlankQuestion.getId())
            .getFirst()
            .getPublicId();
    assertThrows(
        BusinessException.class,
        () ->
            submissions.submit(
                leftBlank.userId(),
                leftBlank.setId(),
                uuid(8),
                List.of(
                    new QuizResponseRequest(
                        leftBlank.questionId(),
                        null,
                        List.of(new BlankAnswerRequest(foreignBlank, "fifo")),
                        null))));
  }

  @Test
  void overrideKeepsAutomaticResultAndChangesOnlyCurrentFinalResult() {
    ReadyQuiz quiz = readyShortAnswer(fixture());
    submissions.submit(
        quiz.userId(),
        quiz.setId(),
        uuid(5),
        List.of(new QuizResponseRequest(quiz.questionId(), null, null, "fifo")));

    var changed = gradings.update(quiz.userId(), uuid(5), quiz.questionId(), "INCORRECT");
    var attempt = attempts.findByPublicId(uuid(5)).orElseThrow();
    var stored =
        attemptQuestions.findAllByAttemptIdOrderBySequenceNumber(attempt.getId()).getFirst();

    assertEquals(GradingOutcome.CORRECT, stored.getAutomaticGradingResult());
    assertEquals(GradingOutcome.INCORRECT, stored.getFinalGradingResult());
    assertEquals(GradingMethod.USER_OVERRIDE, stored.getGradingMethod());
    assertEquals(0, changed.summary().scoredGrading().correctQuestionCount());
    assertEquals(1, changed.summary().reviewQuestionCount());
  }

  @Test
  void reviewSnapshotIsStableRetryableAndResolutionDoesNotChangeOriginalOutcome() {
    ReadyQuiz quiz = readyShortAnswer(fixture());
    submissions.submit(
        quiz.userId(),
        quiz.setId(),
        uuid(6),
        List.of(new QuizResponseRequest(quiz.questionId(), null, null, "wrong")));
    var first = reviews.start(quiz.userId(), uuid(6)).reviewSession();
    var retry = reviews.start(quiz.userId(), uuid(6)).reviewSession();
    assertEquals(first.reviewSessionId(), retry.reviewSessionId());

    gradings.update(quiz.userId(), uuid(6), quiz.questionId(), "CORRECT");
    assertEquals(1, reviews.get(quiz.userId(), first.reviewSessionId()).reviewQuestionCount());
    submissions.submitReview(
        quiz.userId(),
        first.reviewSessionId(),
        List.of(new QuizResponseRequest(quiz.questionId(), null, null, "FIFO")));

    assertEquals(0, reviews.latest(quiz.userId()).reviewQuestionCount());
    assertEquals(
        GradingOutcome.CORRECT,
        results.result(quiz.userId(), uuid(6)).questionResults().getFirst().outcome());
    var source =
        attemptQuestions
            .findAllByAttemptIdOrderBySequenceNumber(
                attempts.findByPublicId(uuid(6)).orElseThrow().getId())
            .getFirst();
    assertEquals(GradingOutcome.INCORRECT, source.getAutomaticGradingResult());
    assertNotEquals(null, source.getReviewResolvedAt());
  }

  @Test
  void solvingReviewResultsAndReviewAttemptsAreExcludedFromMainBoundaries() {
    ReadyQuiz quiz = ready(fixture(), essay());
    submissions.submit(
        quiz.userId(),
        quiz.setId(),
        uuid(9),
        List.of(new QuizResponseRequest(quiz.questionId(), null, null, "내 설명")));
    essayAssessments.assessMain(quiz.userId(), uuid(9), quiz.questionId(), "INCORRECT");
    String reviewId = reviews.start(quiz.userId(), uuid(9)).reviewSession().reviewSessionId();

    BusinessException solving =
        assertThrows(BusinessException.class, () -> results.reviewResult(quiz.userId(), reviewId));
    assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, solving.getErrorCode());
    assertEquals(
        CommonErrorCode.RESOURCE_NOT_FOUND,
        assertThrows(BusinessException.class, () -> results.result(quiz.userId(), reviewId))
            .getErrorCode());

    submissions.submitReview(
        quiz.userId(),
        reviewId,
        List.of(new QuizResponseRequest(quiz.questionId(), null, null, "복습 설명")));
    assertNull(results.pending(quiz.userId(), quiz.setId()));
    assertEquals(
        QuizErrorCode.ATTEMPT_CONFLICT,
        assertThrows(
                BusinessException.class,
                () ->
                    essayAssessments.assessMain(
                        quiz.userId(), reviewId, quiz.questionId(), "CORRECT"))
            .getErrorCode());
    assertEquals(
        QuizAttemptStatus.COMPLETED,
        essayAssessments
            .assessReview(quiz.userId(), reviewId, quiz.questionId(), "CORRECT")
            .status());
  }

  @Test
  void rollsBackTheWholeAttemptWhenSubmittedAnswerPersistenceFails() {
    ReadyQuiz quiz = readyShortAnswer(fixture());
    jdbc.execute(
        "ALTER TABLE quiz_submitted_answers ADD CONSTRAINT test_fail_answer "
            + "CHECK (answer_value <> '"
            + FAIL_ANSWER
            + "')");
    try {
      assertThrows(
          DataIntegrityViolationException.class,
          () ->
              submissions.submit(
                  quiz.userId(),
                  quiz.setId(),
                  uuid(7),
                  List.of(new QuizResponseRequest(quiz.questionId(), null, null, FAIL_ANSWER))));
    } finally {
      dropFailureCheck();
    }
    assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts", Long.class));
    assertEquals(
        0L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempt_questions", Long.class));
    assertEquals(
        0L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_submitted_answers", Long.class));
  }

  private ReadyQuiz readyShortAnswer(Fixture fixture) {
    return ready(fixture, shortAnswer());
  }

  private ReadyQuiz readyMultipleChoice(Fixture fixture) {
    return ready(fixture, multipleChoice(3));
  }

  private ReadyQuiz ready(Fixture fixture, QuizGenerationCandidate candidate) {
    QuizSet set = sets.saveAndFlush(QuizSet.generating(fixture.userId(), fixture.materialId()));
    generation.complete(fixture.userId(), set.getPublicId(), List.of(candidate), 15);
    return new ReadyQuiz(
        fixture.userId(),
        set.getPublicId(),
        questions.findAllByQuizSetIdOrderByNumber(set.getId()).getFirst().getPublicId());
  }

  private QuizGenerationCandidate multipleChoice(int count) {
    List<ChoiceCandidate> values =
        java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> new ChoiceCandidate("보기 " + i, i == 1))
            .toList();
    return new QuizGenerationCandidate(
        8,
        QuestionType.MULTIPLE_CHOICE,
        "객관식",
        "정답?",
        "해설",
        "근거",
        values,
        List.of(),
        List.of(),
        null,
        List.of());
  }

  private QuizGenerationCandidate fillBlank() {
    return new QuizGenerationCandidate(
        9,
        QuestionType.FILL_IN_THE_BLANK,
        "빈칸",
        "큐는 [1]이다.",
        "해설",
        "근거",
        List.of(),
        List.of(),
        List.of(new BlankCandidate(1, List.of("FIFO"))),
        null,
        List.of());
  }

  private QuizGenerationCandidate shortAnswer() {
    return new QuizGenerationCandidate(
        10,
        QuestionType.SHORT_ANSWER,
        "단답",
        "처리 순서?",
        "해설",
        "근거",
        List.of(),
        List.of("fifo", "FIFO"),
        List.of(),
        null,
        List.of());
  }

  private QuizGenerationCandidate essay() {
    return new QuizGenerationCandidate(
        11,
        QuestionType.ESSAY,
        "서술",
        "설명하세요.",
        "해설",
        "근거",
        List.of(),
        List.of(),
        List.of(),
        "모범 답안",
        List.of("핵심"));
  }

  private Fixture fixture() {
    String email = UUID.randomUUID() + "@example.com";
    User user = User.pending(email, email, "hash");
    user.activate(Instant.parse("2026-08-20T00:00:00Z"));
    user = users.saveAndFlush(user);
    LearningMaterial material = materials.saveAndFlush(material(user.getId()));
    return new Fixture(user.getId(), material.getId());
  }

  private LearningMaterial material(long userId) {
    byte[] idempotency = new byte[32];
    byte[] fingerprint = new byte[32];
    int seed = DIGEST_SEED.incrementAndGet();
    idempotency[0] = (byte) seed;
    fingerprint[0] = (byte) (seed + 1);
    return LearningMaterial.create(userId, "자료", "내용", SourceType.PASTE, idempotency, fingerprint);
  }

  private String uuid(int suffix) {
    return "550e8400-e29b-41d4-a716-4466554400" + String.format("%02d", suffix);
  }

  private void dropFailureCheck() {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE table_schema=DATABASE() AND table_name='quiz_submitted_answers'
            AND constraint_name='test_fail_answer'
            """,
            Integer.class);
    if (count != null && count > 0) {
      jdbc.execute("ALTER TABLE quiz_submitted_answers DROP CHECK test_fail_answer");
    }
  }

  private record Fixture(long userId, long materialId) {}

  private record ReadyQuiz(long userId, String setId, String questionId) {}
}
