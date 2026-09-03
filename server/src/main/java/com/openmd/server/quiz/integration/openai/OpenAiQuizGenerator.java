package com.openmd.server.quiz.integration.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmd.server.quiz.integration.openai.QuizGenerationResult.GenerationOutcome;
import com.openmd.server.quiz.integration.openai.QuizGenerationResult.InsufficiencyReason;
import com.openmd.server.quiz.service.QuizGeneratedBatch;
import com.openmd.server.quiz.service.QuizGenerationWork;
import com.openmd.server.quiz.service.QuizGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = {"openmd.quiz.enabled", "openmd.quiz.generation.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class OpenAiQuizGenerator implements QuizGenerator {
  private static final Logger log = LoggerFactory.getLogger(OpenAiQuizGenerator.class);
  private static final String USER_PREFIX = "다음 JSON 작업 데이터로 퀴즈를 생성하라.\n";

  private final ChatClient chatClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String systemPrompt;
  private final int networkRetry;
  private final com.openmd.server.quiz.config.QuizGenerationProperties properties;

  public OpenAiQuizGenerator(
      ChatClient.Builder builder,
      com.openmd.server.quiz.config.QuizGenerationProperties properties) {
    this.chatClient = builder.build();
    this.properties = properties;
    this.networkRetry = properties.networkRetry();
    this.systemPrompt = loadPrompt(properties.promptVersion());
  }

  @Override
  public QuizGeneratedBatch generate(QuizGenerationWork work) {
    String userPrompt;
    try {
      userPrompt = toUserPrompt(work);
    } catch (JsonProcessingException exception) {
      log.error("Could not serialize quiz generation payload");
      return QuizGeneratedBatch.failed();
    }

    for (int attempt = 0; attempt <= networkRetry; attempt++) {
      try {
        ResponseEntity<org.springframework.ai.chat.model.ChatResponse, QuizGenerationResult>
            response =
                chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(options(work.quizSpec().targetTotal()))
                    .call()
                    .responseEntity(
                        QuizGenerationResult.class,
                        spec -> spec.useProviderStructuredOutput());
        return mapped(response == null ? null : response.entity());
      } catch (RuntimeException exception) {
        if (attempt < networkRetry && transientFailure(exception)) continue;
        log.warn("OpenAI quiz generation attempt failed category={}", category(exception));
        return QuizGeneratedBatch.failed();
      }
    }
    return QuizGeneratedBatch.failed();
  }

  private OpenAiChatOptions.Builder options(int targetTotal) {
    int maxOutputTokens =
        targetTotal <= 5 ? 3_000 : targetTotal <= 10 ? 5_000 : targetTotal <= 15 ? 7_000 : 9_000;
    return OpenAiChatOptions.builder()
        .model(properties.model())
        .reasoningEffort(properties.reasoningEffort())
        .timeout(properties.timeout())
        .maxRetries(0)
        .maxCompletionTokens(maxOutputTokens)
        .store(false);
  }

  QuizGeneratedBatch mapped(QuizGenerationResult result) {
    if (result == null || result.outcome() == null || result.insufficiencyReason() == null
        || result.questions() == null) return QuizGeneratedBatch.failed();
    if (result.outcome() == GenerationOutcome.GENERATED) {
      return result.insufficiencyReason() == InsufficiencyReason.NONE && !result.questions().isEmpty()
          ? QuizGeneratedBatch.generated(result.questions())
          : QuizGeneratedBatch.failed();
    }
    return result.insufficiencyReason() != InsufficiencyReason.NONE && result.questions().isEmpty()
        ? QuizGeneratedBatch.sourceInsufficient()
        : QuizGeneratedBatch.failed();
  }

  String toUserPrompt(QuizGenerationWork work) throws JsonProcessingException {
    return USER_PREFIX + objectMapper.writeValueAsString(work);
  }

  private boolean transientFailure(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof ResourceAccessException) return true;
      if (current.getClass().getSimpleName().equals("TransientAiException")) return true;
      if (current instanceof HttpStatusCodeException http && transientStatus(http.getStatusCode())) {
        return true;
      }
    }
    return false;
  }

  private boolean transientStatus(HttpStatusCode status) {
    int value = status.value();
    return value == 408 || value == 409 || value == 429 || status.is5xxServerError();
  }

  private String category(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof ResourceAccessException) return "PROVIDER_CONNECTION";
      if (current instanceof HttpStatusCodeException http) {
        if (http.getStatusCode().value() == 429) return "PROVIDER_RATE_LIMIT";
        if (http.getStatusCode().is5xxServerError()) return "PROVIDER_SERVER_ERROR";
        return "PROVIDER_CONFIGURATION";
      }
    }
    return "PROVIDER_RESPONSE_INVALID";
  }

  private String loadPrompt(String version) {
    try {
      return new ClassPathResource("prompts/" + version + ".txt")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Quiz generation prompt resource is unavailable", exception);
    }
  }
}
