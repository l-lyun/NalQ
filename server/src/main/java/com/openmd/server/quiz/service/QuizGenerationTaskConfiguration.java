package com.openmd.server.quiz.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
class QuizGenerationTaskConfiguration {

  @Bean
  ThreadPoolTaskScheduler quizGenerationTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("quiz-generation-stub-");
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    return scheduler;
  }
}
