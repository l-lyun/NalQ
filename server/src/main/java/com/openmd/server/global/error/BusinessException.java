package com.openmd.server.global.error;

import com.openmd.server.global.api.FieldError;
import java.util.List;
import java.util.Objects;

public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	private final List<FieldError> fields;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, List.of());
	}

	public BusinessException(ErrorCode errorCode, List<FieldError> fields) {
		super(Objects.requireNonNull(errorCode, "errorCode must not be null").message());
		this.errorCode = errorCode;
		this.fields = List.copyOf(Objects.requireNonNull(fields, "fields must not be null"));
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public List<FieldError> getFields() {
		return fields;
	}
}
