package com.openmd.server.character.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.character.domain.CharacterProfile;
import com.openmd.server.character.domain.QuizCoinReward;
import com.openmd.server.character.repository.CharacterProfileRepository;
import com.openmd.server.character.repository.QuizCoinRewardRepository;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuizCompletionRewardServiceTest {

  private final UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
  private final CharacterProfileRepository profiles =
      org.mockito.Mockito.mock(CharacterProfileRepository.class);
  private final QuizCoinRewardRepository rewards =
      org.mockito.Mockito.mock(QuizCoinRewardRepository.class);
  private final QuizAttemptRepository attempts = org.mockito.Mockito.mock(QuizAttemptRepository.class);
  private final QuizCompletionRewardService service =
      new QuizCompletionRewardService(users, profiles, rewards, attempts);

  @BeforeEach
  void activeUser() {
    User user = org.mockito.Mockito.mock(User.class);
    when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
    when(profiles.save(any(CharacterProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void grantsTenCoinsForTheFirstMainCompletionOfASet() {
    CharacterProfile profile = CharacterProfile.initial(7L);
    when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));
    when(rewards.existsByUserIdAndQuizSetId(7L, 11L)).thenReturn(false);
    when(attempts.findFirstPriorCompletionForUpdate(
            11L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED, "attempt-new"))
        .thenReturn(Optional.empty());

    service.rewardFirstCompletion(7L, 11L, "attempt-new");

    assertEquals(10, profile.getBalance());
    verify(rewards).save(any(QuizCoinReward.class));
  }

  @Test
  void doesNotRewardWhenTheSetWasCompletedBeforeFeatureIntroduction() {
    when(rewards.existsByUserIdAndQuizSetId(7L, 11L)).thenReturn(false);
    when(attempts.findFirstPriorCompletionForUpdate(
            11L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED, "attempt-new"))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(com.openmd.server.quiz.domain.entity.QuizAttempt.class)));

    service.rewardFirstCompletion(7L, 11L, "attempt-new");

    verify(profiles, never()).save(any());
    verify(rewards, never()).save(any());
  }

  @Test
  void doesNotRewardARepeatedCompletionAfterARewardRecordExists() {
    when(rewards.existsByUserIdAndQuizSetId(7L, 11L)).thenReturn(true);

    service.rewardFirstCompletion(7L, 11L, "attempt-retry");

    verify(attempts, never())
        .findFirstPriorCompletionForUpdate(
            11L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED, "attempt-retry");
    verify(profiles, never()).save(any());
    verify(rewards, never()).save(any());
  }
}
