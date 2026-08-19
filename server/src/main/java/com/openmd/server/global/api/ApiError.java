package com.openmd.server.global.api;

import java.util.List;

public record ApiError(
	String code,
	String message,
	List<FieldError> fields
) {

	public ApiError {
		fields = fields == null ? List.of() : List.copyOf(fields);
	}

	public static ApiError of(String code, String message) {
		return new ApiError(code, message, List.of());
	}

	public static ApiError of(String code, String message, List<FieldError> fields) {
		return new ApiError(code, message, fields);
	}
}
