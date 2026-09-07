package com.openmd.server.push.config;

import com.openmd.server.push.service.PushDeliveryTransaction;
import com.openmd.server.push.service.PushDeliveryWorker;
import com.openmd.server.push.service.PushRetentionService;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "openmd.push.scheduler-enabled", havingValue = "true")
public class PushSchedulingConfiguration {

  @Bean("pushTaskScheduler")
  ThreadPoolTaskScheduler pushTaskScheduler() {
    return scheduler("push-scheduler-");
  }

  @Bean("taskScheduler")
  @ConditionalOnMissingBean(name = "taskScheduler")
  ThreadPoolTaskScheduler defaultTaskScheduler() {
    return scheduler("application-scheduler-");
  }

  @Bean
  PushScheduler pushScheduler(
      PushDeliveryWorker worker,
      PushDeliveryTransaction transactions,
      PushRetentionService retention,
      PushProperties properties,
      ObjectProvider<Clock> clock) {
    return new PushScheduler(
        worker,
        transactions,
        retention,
        properties.isDeliveryEnabled(),
        properties.getBatchSize(),
        clock.getIfAvailable(Clock::systemUTC));
  }

  private ThreadPoolTaskScheduler scheduler(String prefix) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix(prefix);
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    return scheduler;
  }
}
