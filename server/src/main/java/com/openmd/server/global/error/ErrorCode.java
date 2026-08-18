package com.openmd.server.global.error;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

	HttpStatus status();

	String code();

	String message();
}
