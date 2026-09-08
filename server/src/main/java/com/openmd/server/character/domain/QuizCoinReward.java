package com.openmd.server.character.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "quiz_coin_rewards",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_quiz_coin_rewards_user_set", columnNames = {"user_id", "quiz_set_id"}),
      @UniqueConstraint(name = "uk_quiz_coin_rewards_attempt", columnNames = "attempt_public_id")
    })
public class QuizCoinReward extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private long userId;

  @Column(name = "quiz_set_id", nullable = false, updatable = false)
  private long quizSetId;

  @Column(name = "attempt_public_id", nullable = false, updatable = false, length = 36)
  private String attemptPublicId;

  @Column(nullable = false, updatable = false)
  private int amount;

  protected QuizCoinReward() {}

  public static QuizCoinReward grant(
      long userId, long quizSetId, String attemptPublicId, int amount) {
    QuizCoinReward reward = new QuizCoinReward();
    reward.userId = userId;
    reward.quizSetId = quizSetId;
    reward.attemptPublicId = attemptPublicId;
    reward.amount = amount;
    return reward;
  }
}
