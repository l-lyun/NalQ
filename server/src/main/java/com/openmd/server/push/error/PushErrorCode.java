package com.openmd.server.push.error;

import com.openmd.server.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PushErrorCode implements ErrorCode {
  REVISION_CONFLICT(HttpStatus.CONFLICT, "PUSH_REVISION_CONFLICT", "기기 상태가 변경되었습니다."),
  OPERATION_CONFLICT(HttpStatus.CONFLICT, "PUSH_OPERATION_CONFLICT", "같은 작업 식별자를 다른 요청에 사용할 수 없습니다."),
  OPERATION_EXPIRED(HttpStatus.CONFLICT, "PUSH_OPERATION_EXPIRED", "기기 변경 요청이 만료되었습니다."),
  TOKEN_CONFLICT(HttpStatus.CONFLICT, "PUSH_TOKEN_CONFLICT", "이 기기의 푸시 연결을 확인할 수 없습니다."),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "PUSH_RATE_LIMITED", "잠시 후 다시 시도해 주세요."),
  DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "COMMON_999", "서버 내부 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  PushErrorCode(HttpStatus status, String code, String message) {
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
