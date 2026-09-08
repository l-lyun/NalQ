package com.openmd.server.character.service;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.character.domain.CharacterProfile;
import com.openmd.server.character.domain.QuizCoinReward;
import com.openmd.server.character.repository.CharacterProfileRepository;
import com.openmd.server.character.repository.QuizCoinRewardRepository;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizCompletionRewardService {

  public static final int FIRST_COMPLETION_REWARD = 10;

  private final UserRepository users;
  private final CharacterProfileRepository profiles;
  private final QuizCoinRewardRepository rewards;
  private final QuizAttemptRepository attempts;

  public QuizCompletionRewardService(
      UserRepository users,
      CharacterProfileRepository profiles,
      QuizCoinRewardRepository rewards,
      QuizAttemptRepository attempts) {
    this.users = users;
    this.profiles = profiles;
    this.rewards = rewards;
    this.attempts = attempts;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockActiveAccount(long userId) {
    User user = users.findByIdForUpdate(userId).orElseThrow(this::notFound);
    if (user.getStatus() != UserStatus.ACTIVE) throw notFound();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void rewardFirstCompletion(
      long userId, long quizSetId, String completedAttemptPublicId) {
    lockActiveAccount(userId);
    if (rewards.existsByUserIdAndQuizSetId(userId, quizSetId)) return;
    if (attempts
        .findFirstPriorCompletionForUpdate(
            quizSetId,
            userId,
            QuizAttemptType.MAIN,
            QuizAttemptStatus.COMPLETED,
            completedAttemptPublicId)
        .isPresent()) return;

    CharacterProfile profile =
        profiles
            .findByUserId(userId)
            .orElseGet(() -> profiles.save(CharacterProfile.initial(userId)));
    profile.credit(FIRST_COMPLETION_REWARD);
    rewards.save(
        QuizCoinReward.grant(
            userId, quizSetId, completedAttemptPublicId, FIRST_COMPLETION_REWARD));
  }

  private BusinessException notFound() {
    return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
  }
}
