package com.openmd.server.learningmaterial.error;

import com.openmd.server.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum LearningMaterialErrorCode implements ErrorCode {

	CONTENT_TOO_LONG(HttpStatus.CONTENT_TOO_LARGE, "MATERIAL_002", "학습자료 본문은 20,000자를 초과할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	LearningMaterialErrorCode(HttpStatus status, String code, String message) {
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
