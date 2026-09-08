package com.openmd.server.character.repository;

import com.openmd.server.character.domain.QuizCoinReward;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizCoinRewardRepository extends JpaRepository<QuizCoinReward, Long> {
  boolean existsByUserIdAndQuizSetId(long userId, long quizSetId);

  void deleteAllByUserId(long userId);
}
