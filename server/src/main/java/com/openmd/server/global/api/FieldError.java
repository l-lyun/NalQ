package com.openmd.server.global.api;

public record FieldError(
	String field,
	String reason
) {
}
