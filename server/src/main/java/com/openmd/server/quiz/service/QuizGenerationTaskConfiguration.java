package com.openmd.server.quiz.service;

import com.openmd.server.quiz.config.QuizGenerationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QuizGenerationProperties.class)
@EnableScheduling
class QuizGenerationTaskConfiguration {

  @Bean("quizGenerationTaskExecutor")
  ThreadPoolTaskExecutor quizGenerationTaskExecutor(QuizGenerationProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.workerCount());
    executor.setMaxPoolSize(properties.workerCount());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setThreadNamePrefix("quiz-generation-");
    executor.setWaitForTasksToCompleteOnShutdown(false);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    return executor;
  }

  @Bean
  QuizGenerationCapacity quizGenerationCapacity(QuizGenerationProperties properties) {
    return new QuizGenerationCapacity(properties.workerCount() + properties.queueCapacity());
  }
}
