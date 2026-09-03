package com.openmd.server.quiz.dto.request;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import com.openmd.server.quiz.dto.command.QuizGenerationConfig;
import java.util.List;

public record GenerateQuizRequest(
    List<String> selectedTypes,
    String difficulty,
    Integer maxQuestionCount,
    String generationPrompt) {
  public QuizGenerationConfig toCommand() {
    List<FieldError> fields = new java.util.ArrayList<>();
    List<QuestionType> parsedTypes = parseTypes(fields);
    QuizDifficulty parsedDifficulty = parseDifficulty(fields);
    String normalizedPrompt = normalizePrompt(fields);
    if (!fields.isEmpty()) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT, fields);
    }
    return new QuizGenerationConfig(parsedTypes, parsedDifficulty, maxQuestionCount, normalizedPrompt);
  }

  private String normalizePrompt(List<FieldError> fields) {
    if (generationPrompt == null) return null;
    String normalized = trimUnicodeWhitespace(generationPrompt);
    if (normalized.isEmpty()) return null;
    if (normalized.codePointCount(0, normalized.length()) > 300) {
      fields.add(new FieldError("generationPrompt", "generationPrompt는 300자 이하여야 합니다."));
      return null;
    }
    return normalized;
  }

  private String trimUnicodeWhitespace(String value) {
    int start = 0;
    int end = value.length();
    while (start < end) {
      int codePoint = value.codePointAt(start);
      if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) break;
      start += Character.charCount(codePoint);
    }
    while (start < end) {
      int codePoint = value.codePointBefore(end);
      if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) break;
      end -= Character.charCount(codePoint);
    }
    return value.substring(start, end);
  }

  private List<QuestionType> parseTypes(List<FieldError> fields) {
    if (selectedTypes == null) return null;
    try {
      return selectedTypes.stream().map(QuestionType::valueOf).toList();
    } catch (IllegalArgumentException | NullPointerException exception) {
      fields.add(new FieldError("selectedTypes", "알 수 없는 문제 유형이 포함되어 있습니다."));
      return List.of();
    }
  }

  private QuizDifficulty parseDifficulty(List<FieldError> fields) {
    if (difficulty == null) return null;
    try {
      return QuizDifficulty.valueOf(difficulty);
    } catch (IllegalArgumentException exception) {
      fields.add(new FieldError("difficulty", "알 수 없는 난이도입니다."));
      return null;
    }
  }
}
