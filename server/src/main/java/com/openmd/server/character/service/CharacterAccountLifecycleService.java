package com.openmd.server.character.service;

import com.openmd.server.character.repository.CharacterOwnedItemRepository;
import com.openmd.server.character.repository.CharacterProfileRepository;
import com.openmd.server.character.repository.QuizCoinRewardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CharacterAccountLifecycleService implements CharacterAccountLifecycle {
  private final QuizCoinRewardRepository rewards;
  private final CharacterOwnedItemRepository ownedItems;
  private final CharacterProfileRepository profiles;

  public CharacterAccountLifecycleService(
      QuizCoinRewardRepository rewards,
      CharacterOwnedItemRepository ownedItems,
      CharacterProfileRepository profiles) {
    this.rewards = rewards;
    this.ownedItems = ownedItems;
    this.profiles = profiles;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleteForUser(long userId) {
    rewards.deleteAllByUserId(userId);
    ownedItems.deleteAllByUserId(userId);
    profiles.deleteAllByUserId(userId);
  }
}
