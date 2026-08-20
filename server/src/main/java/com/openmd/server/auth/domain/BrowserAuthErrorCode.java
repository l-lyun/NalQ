package com.openmd.server.auth.domain;

import com.openmd.server.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BrowserAuthErrorCode implements ErrorCode {

	CSRF_REJECTED(HttpStatus.FORBIDDEN, "AUTH_009", "허용되지 않은 브라우저 인증 요청입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	BrowserAuthErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override public HttpStatus status() { return status; }
	@Override public String code() { return code; }
	@Override public String message() { return message; }
}
