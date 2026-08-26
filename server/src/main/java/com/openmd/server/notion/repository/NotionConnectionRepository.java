package com.openmd.server.notion.repository;

import com.openmd.server.notion.domain.NotionConnection;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotionConnectionRepository extends JpaRepository<NotionConnection, Long> {

	Optional<NotionConnection> findByUserId(long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select connection from NotionConnection connection where connection.userId = :userId")
	Optional<NotionConnection> findByUserIdForUpdate(@Param("userId") long userId);
}
