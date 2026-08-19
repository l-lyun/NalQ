package com.openmd.server.global.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class BaseEntityMappingTests {

	@Test
	void usesMySqlIdentityStrategyForId() throws NoSuchFieldException {
		Field id = BaseEntity.class.getDeclaredField("id");

		assertNotNull(id.getAnnotation(Id.class));
		assertEquals(
			GenerationType.IDENTITY,
			id.getAnnotation(GeneratedValue.class).strategy()
		);
	}

	@Test
	void wrapsTimeEntityAsMappedSuperclass() {
		assertTrue(BaseTimeEntity.class.isAssignableFrom(BaseEntity.class));
		assertNotNull(BaseTimeEntity.class.getAnnotation(MappedSuperclass.class));
		assertNotNull(BaseEntity.class.getAnnotation(MappedSuperclass.class));
	}
}
