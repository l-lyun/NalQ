package com.openmd.server.character.repository;

import com.openmd.server.character.domain.CharacterProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterProfileRepository extends JpaRepository<CharacterProfile, Long> {
  Optional<CharacterProfile> findByUserId(long userId);

  void deleteAllByUserId(long userId);
}
