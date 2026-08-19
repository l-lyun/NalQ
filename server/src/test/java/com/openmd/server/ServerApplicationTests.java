package com.openmd.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"openmd.auth.enabled=false",
	"spring.data.jpa.auditing.enabled=false",
	"spring.autoconfigure.exclude="
		+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
		+ "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
		+ "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
		+ "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
})
class ServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
