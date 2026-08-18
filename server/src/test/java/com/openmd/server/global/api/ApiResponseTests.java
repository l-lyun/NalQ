package com.openmd.server.global.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiResponseTests {

	@Test
	void createsSuccessResponseWithData() {
		ApiResponse<String> response = ApiResponse.success("data");

		assertTrue(response.success());
		assertEquals("data", response.data());
		assertNull(response.error());
	}

	@Test
	void createsSuccessResponseWithoutData() {
		ApiResponse<Void> response = ApiResponse.successWithoutData();

		assertTrue(response.success());
		assertNull(response.data());
		assertNull(response.error());
	}

	@Test
	void createsFailureResponse() {
		ApiError error = ApiError.of("COMMON_001", "입력값이 올바르지 않습니다.");

		ApiResponse<Void> response = ApiResponse.failure(error);

		assertFalse(response.success());
		assertNull(response.data());
		assertEquals(error, response.error());
	}

	@Test
	void copiesFieldErrorListToKeepErrorImmutable() {
		List<FieldError> fields = new ArrayList<>();
		fields.add(new FieldError("email", "이메일 형식이 올바르지 않습니다."));

		ApiError error = ApiError.of("COMMON_001", "입력값이 올바르지 않습니다.", fields);
		fields.clear();

		assertEquals(1, error.fields().size());
		assertThrows(UnsupportedOperationException.class, () -> error.fields().clear());
	}
}
