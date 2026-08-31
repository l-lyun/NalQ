package com.openmd.server.auth.repository;

import com.openmd.server.auth.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByNormalizedEmail(String normalizedEmail);

	boolean existsByNicknameIgnoreCase(String nickname);

	boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);
}
