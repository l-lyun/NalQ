package com.openmd.server.global.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openmd.server.global.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTests {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
	private final ServletWebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());

	@Test
	void preservesBadRequestForMissingRequestParameter() throws Exception {
		MissingServletRequestParameterException exception =
			new MissingServletRequestParameterException("query", "String");

		ResponseEntity<Object> response = handler.handleException(exception, webRequest);

		assertFailure(response, HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_INPUT);
	}

	@Test
	void preservesMethodNotAllowedStatus() throws Exception {
		HttpRequestMethodNotSupportedException exception =
			new HttpRequestMethodNotSupportedException("POST");

		ResponseEntity<Object> response = handler.handleException(exception, webRequest);

		assertFailure(response, HttpStatus.METHOD_NOT_ALLOWED, CommonErrorCode.INVALID_INPUT);
	}

	private void assertFailure(
		ResponseEntity<Object> response,
		HttpStatus expectedStatus,
		ErrorCode expectedErrorCode
	) {
		assertEquals(expectedStatus, response.getStatusCode());
		ApiResponse<?> body = assertInstanceOf(ApiResponse.class, response.getBody());
		assertFalse(body.success());
		assertEquals(expectedErrorCode.code(), body.error().code());
	}
}
