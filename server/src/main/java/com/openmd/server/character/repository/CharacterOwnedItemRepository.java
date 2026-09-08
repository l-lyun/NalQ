package com.openmd.server.character.repository;

import com.openmd.server.character.domain.CharacterOwnedItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterOwnedItemRepository extends JpaRepository<CharacterOwnedItem, Long> {
  boolean existsByUserIdAndItemId(long userId, String itemId);

  List<CharacterOwnedItem> findAllByUserIdOrderById(long userId);

  void deleteAllByUserId(long userId);
}
