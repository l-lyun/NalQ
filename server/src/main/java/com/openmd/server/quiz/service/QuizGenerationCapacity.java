package com.openmd.server.quiz.service;

import java.util.concurrent.Semaphore;

public final class QuizGenerationCapacity {
  private final Semaphore slots;

  public QuizGenerationCapacity(int capacity) {
    slots = new Semaphore(capacity);
  }

  public boolean tryAcquire() {
    return slots.tryAcquire();
  }

  public void release() {
    slots.release();
  }

  int availablePermits() {
    return slots.availablePermits();
  }
}
