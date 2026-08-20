package com.openmd.server.global.error;

import com.openmd.server.global.api.ApiError;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.global.api.FieldError;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		ApiError error = ApiError.of(errorCode.code(), errorCode.message(), exception.getFields());
		return ResponseEntity.status(errorCode.status()).body(ApiResponse.failure(error));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
		MethodArgumentNotValidException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		List<FieldError> fields = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
			.toList();

		ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
		ApiError error = ApiError.of(errorCode.code(), errorCode.message(), fields);
		return createMvcErrorResponse(error, headers, status);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
		HttpMessageNotReadableException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		return createMvcErrorResponse(CommonErrorCode.MALFORMED_REQUEST, headers, status);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
		Exception exception,
		Object body,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		ErrorCode errorCode = resolveMvcErrorCode(status);
		if (status.is5xxServerError()) {
			log.error("Spring MVC exception", exception);
		}
		return createMvcErrorResponse(errorCode, headers, status);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		log.error("Unhandled exception", exception);
		return createErrorResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
	}

	private ResponseEntity<ApiResponse<Void>> createErrorResponse(ErrorCode errorCode) {
		ApiError error = ApiError.of(errorCode.code(), errorCode.message());
		return ResponseEntity.status(errorCode.status()).body(ApiResponse.failure(error));
	}

	private ResponseEntity<Object> createMvcErrorResponse(
		ErrorCode errorCode,
		HttpHeaders headers,
		HttpStatusCode status
	) {
		ApiError error = ApiError.of(errorCode.code(), errorCode.message());
		return createMvcErrorResponse(error, headers, status);
	}

	private ResponseEntity<Object> createMvcErrorResponse(
		ApiError error,
		HttpHeaders headers,
		HttpStatusCode status
	) {
		return ResponseEntity.status(status)
			.headers(headers)
			.body(ApiResponse.failure(error));
	}

	private ErrorCode resolveMvcErrorCode(HttpStatusCode status) {
		if (status.value() == 404) {
			return CommonErrorCode.RESOURCE_NOT_FOUND;
		}
		if (status.is4xxClientError()) {
			return CommonErrorCode.INVALID_INPUT;
		}
		return CommonErrorCode.INTERNAL_SERVER_ERROR;
	}
}
