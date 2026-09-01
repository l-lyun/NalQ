package com.openmd.server.integration.notion.error;

import com.openmd.server.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NotionErrorCode implements ErrorCode {
	CONNECTION_REQUIRED(HttpStatus.BAD_REQUEST, "NOTION_CONNECTION_REQUIRED", "Notion 연결이 필요합니다."),
	REAUTH_REQUIRED(HttpStatus.CONFLICT, "NOTION_REAUTH_REQUIRED", "Notion 재인증이 필요합니다."),
	WORKSPACE_MISMATCH(HttpStatus.CONFLICT, "NOTION_WORKSPACE_MISMATCH", "현재 연결된 워크스페이스와 다릅니다."),
	PAGE_NOT_ACCESSIBLE(HttpStatus.BAD_REQUEST, "NOTION_PAGE_NOT_ACCESSIBLE", "Notion 페이지에 접근할 수 없습니다."),
	CONTENT_INCOMPLETE(HttpStatus.BAD_REQUEST, "NOTION_CONTENT_INCOMPLETE", "Notion 내용을 완전하게 가져올 수 없습니다."),
	TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "NOTION_TEMPORARILY_UNAVAILABLE", "Notion을 일시적으로 사용할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	NotionErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override public HttpStatus status() { return status; }
	@Override public String code() { return code; }
	@Override public String message() { return message; }
}
