package com.openmd.server.integration.notion.client;

public interface NotionClient {
	String authorizationUrl(String state);
	NotionTokenGrant exchangeAuthorizationCode(String code);
	NotionTokenGrant refresh(String refreshToken);
	boolean revoke(String accessToken);
	boolean introspect(String accessToken);
	NotionPageSearch searchPages(String accessToken, String cursor, String query);
	NotionPage retrievePage(String accessToken, String pageId);
	NotionMarkdown retrieveMarkdown(String accessToken, String pageId);
}
