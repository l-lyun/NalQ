package com.openmd.server.quiz.service;

public interface QuizGenerator {
  QuizGeneratedBatch generate(QuizGenerationWork work);
}
