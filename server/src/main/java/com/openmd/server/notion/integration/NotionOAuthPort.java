package com.openmd.server.notion.integration;

import com.openmd.server.notion.dto.model.NotionOAuthGrant;

public interface NotionOAuthPort {

	NotionOAuthGrant exchangeAuthorizationCode(String code);

	NotionOAuthGrant refresh(String refreshToken);

	void revoke(String accessToken);
}
