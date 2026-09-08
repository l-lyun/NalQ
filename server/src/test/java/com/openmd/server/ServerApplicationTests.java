package com.openmd.server;

import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.character.repository.CharacterOwnedItemRepository;
import com.openmd.server.character.repository.CharacterProfileRepository;
import com.openmd.server.character.repository.QuizCoinRewardRepository;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
	"openmd.auth.enabled=false",
	"openmd.learning-material.enabled=false",
	"openmd.quiz.enabled=false",
	"openmd.home-visit.enabled=false",
	"spring.data.jpa.auditing.enabled=false",
	"spring.autoconfigure.exclude="
		+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
		+ "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
		+ "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
		+ "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
})
class ServerApplicationTests {

	@MockitoBean UserRepository users;
	@MockitoBean CharacterProfileRepository characterProfiles;
	@MockitoBean CharacterOwnedItemRepository characterOwnedItems;
	@MockitoBean QuizCoinRewardRepository quizCoinRewards;
	@MockitoBean QuizAttemptRepository quizAttempts;

	@Test
	void contextLoads() {
	}

}
