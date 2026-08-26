package com.openmd.server.notion.repository;

import com.openmd.server.notion.dto.model.NotionOAuthState;
import java.time.Duration;
import java.util.Optional;

public interface NotionOAuthStateStore {

	void save(String stateDigest, NotionOAuthState state, Duration ttl);

	Optional<NotionOAuthState> consume(String stateDigest);
}
