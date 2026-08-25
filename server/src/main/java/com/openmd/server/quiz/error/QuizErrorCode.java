package com.openmd.server.quiz.error;

import com.openmd.server.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum QuizErrorCode implements ErrorCode {
  ATTEMPT_CONFLICT(HttpStatus.CONFLICT, "ATTEMPT_001", "퀴즈 회차의 현재 상태와 요청이 충돌합니다."),
  REVIEW_UNAVAILABLE(HttpStatus.CONFLICT, "REVIEW_001", "복습할 문항이 없습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  QuizErrorCode(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String message() {
    return message;
  }
}
