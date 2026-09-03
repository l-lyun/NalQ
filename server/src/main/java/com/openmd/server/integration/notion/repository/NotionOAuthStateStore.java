package com.openmd.server.integration.notion.repository;

import com.openmd.server.integration.notion.dto.model.NotionOAuthState;
import java.time.Duration;
import java.util.Optional;

public interface NotionOAuthStateStore {
	void save(String rawState, NotionOAuthState state, Duration ttl);
	Optional<NotionOAuthState> find(String rawState);
	Optional<NotionOAuthState> consume(String rawState);
	void invalidateUser(long userId);
}
