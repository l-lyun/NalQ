package com.openmd.server.push.controller;

import com.openmd.server.global.api.ApiError;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.push.error.PushErrorCode;
import com.openmd.server.push.error.PushRateLimitedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PushDeviceController.class)
public class PushDeviceExceptionHandler {

  @ExceptionHandler(PushRateLimitedException.class)
  public ResponseEntity<ApiResponse<Void>> handleRateLimit(PushRateLimitedException exception) {
    PushErrorCode error = PushErrorCode.RATE_LIMITED;
    return ResponseEntity.status(error.status())
        .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
        .body(ApiResponse.failure(ApiError.of(error.code(), error.message())));
  }
}
