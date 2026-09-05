package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  void runningCancellationReleasesItsSlotOnlyAfterTheWorkActuallyStops() throws Exception {
    QuizGenerationCapacity capacity = new QuizGenerationCapacity(1);
    capacity.tryAcquire();
    QuizGenerationTaskRegistry registry = new QuizGenerationTaskRegistry(capacity);
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    AtomicReference<Thread> executorThread = new AtomicReference<>();

    registry.execute(
        command -> {
          Thread thread = new Thread(command);
          executorThread.set(thread);
          thread.start();
        },
        "quiz-set-1",
        () -> {
          started.countDown();
          while (finish.getCount() > 0) {
            try {
              finish.await();
            } catch (InterruptedException ignored) {
              // Simulate an HTTP client that does not stop immediately on interrupt.
            }
          }
        });
    started.await();

    assertEquals(1, registry.cancel(List.of("quiz-set-1")));
    assertEquals(0, capacity.availablePermits());

    finish.countDown();
    executorThread.get().join();
    assertEquals(1, capacity.availablePermits());
  }
}
