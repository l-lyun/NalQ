package com.openmd.server.notion.integration;

public final class NotionOAuthException extends RuntimeException {

	private final boolean reauthenticationRequired;

	public NotionOAuthException(boolean reauthenticationRequired) {
		super("Notion OAuth request failed");
		this.reauthenticationRequired = reauthenticationRequired;
	}

	public boolean isReauthenticationRequired() {
		return reauthenticationRequired;
	}
}
