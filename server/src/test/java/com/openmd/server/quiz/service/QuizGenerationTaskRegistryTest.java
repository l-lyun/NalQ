package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;

class QuizGenerationTaskRegistryTest {

  @Test
  void cancellationInterruptsTheTrackedTaskAndReleasesItsSlotExactlyOnce() {
    QuizGenerationCapacity capacity = new QuizGenerationCapacity(1);
    capacity.tryAcquire();
    List<Runnable> queued = new ArrayList<>();
    QuizGenerationTaskRegistry registry = new QuizGenerationTaskRegistry(capacity);

    registry.execute(queued::add, "quiz-set-1", () -> {});

    assertEquals(1, registry.cancel(List.of("quiz-set-1")));
    assertTrue(((FutureTask<?>) queued.getFirst()).isCancelled());
    assertEquals(1, capacity.availablePermits());
    assertEquals(0, registry.cancel(List.of("quiz-set-1")));
    assertEquals(1, capacity.availablePermits());
  }

  @Test
  void normalCompletionRemovesTheTaskAndReleasesItsSlot() {
    QuizGenerationCapacity capacity = new QuizGenerationCapacity(1);
    capacity.tryAcquire();
    QuizGenerationTaskRegistry registry = new QuizGenerationTaskRegistry(capacity);

    registry.execute(Runnable::run, "quiz-set-1", () -> {});

    assertFalse(registry.isTracked("quiz-set-1"));
    assertEquals(1, capacity.availablePermits());
  }
}
