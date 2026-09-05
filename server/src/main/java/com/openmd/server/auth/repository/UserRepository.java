package com.openmd.server.auth.repository;

import com.openmd.server.auth.domain.User;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByNormalizedEmail(String normalizedEmail);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from User user where user.id = :userId")
	Optional<User> findByIdForUpdate(@Param("userId") Long userId);

	boolean existsByNicknameIgnoreCase(String nickname);

	boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);
}
