package com.openmd.server.quiz.service;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

final class QuizGenerationTaskRegistry {
  private final QuizGenerationCapacity capacity;
  private final ConcurrentMap<String, TrackedTask> tasks = new ConcurrentHashMap<>();

  QuizGenerationTaskRegistry(QuizGenerationCapacity capacity) {
    this.capacity = capacity;
  }

  void execute(Executor executor, String quizSetId, Runnable work) {
    AtomicBoolean released = new AtomicBoolean();
    FutureTask<Void> future = new FutureTask<>(() -> {
      try {
        work.run();
      } finally {
        complete(quizSetId, released);
      }
      return null;
    });
    TrackedTask tracked = new TrackedTask(future, released);
    if (tasks.putIfAbsent(quizSetId, tracked) != null) {
      release(released);
      throw new IllegalStateException("Quiz generation task is already tracked");
    }
    try {
      executor.execute(future);
    } catch (RuntimeException exception) {
      tasks.remove(quizSetId, tracked);
      future.cancel(true);
      release(released);
      throw exception;
    }
  }

  int cancel(Collection<String> quizSetIds) {
    int cancelled = 0;
    for (String quizSetId : quizSetIds) {
      TrackedTask tracked = tasks.remove(quizSetId);
      if (tracked == null) continue;
      tracked.future().cancel(true);
      release(tracked.released());
      cancelled++;
    }
    return cancelled;
  }

  boolean isTracked(String quizSetId) {
    return tasks.containsKey(quizSetId);
  }

  private void complete(String quizSetId, AtomicBoolean released) {
    tasks.computeIfPresent(
        quizSetId,
        (ignored, tracked) -> tracked.released() == released ? null : tracked);
    release(released);
  }

  private void release(AtomicBoolean released) {
    if (released.compareAndSet(false, true)) capacity.release();
  }

  private record TrackedTask(FutureTask<Void> future, AtomicBoolean released) {}
}
