package com.openmd.server.integration.notion.client;

public class NotionClientException extends RuntimeException {
	private final NotionClientFailure failure;

	public NotionClientException(NotionClientFailure failure) {
		super(failure.name());
		this.failure = failure;
	}

	public NotionClientException(NotionClientFailure failure, Throwable cause) {
		super(failure.name(), cause);
		this.failure = failure;
	}

	public NotionClientFailure failure() { return failure; }
}
