package com.openmd.server.push.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PushPropertiesTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner().withUserConfiguration(PushConfiguration.class);

  @Test
  void defaultAndBoundaryConfigurationStartsSuccessfully() {
    context
        .withPropertyValues(
            "openmd.push.batch-size=50",
            "openmd.push.retention-batch-size=500",
            "openmd.push.lease-duration=11s")
        .run(
            result -> {
              assertThat(result).hasNotFailed();
              PushProperties properties = result.getBean(PushProperties.class);
              assertThat(properties.getBatchSize()).isEqualTo(50);
              assertThat(properties.getRetentionBatchSize()).isEqualTo(500);
              assertThat(properties.getLeaseDuration()).isEqualTo(java.time.Duration.ofSeconds(11));
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "openmd.push.batch-size=0",
        "openmd.push.batch-size=51",
        "openmd.push.retention-batch-size=0",
        "openmd.push.retention-batch-size=501",
        "openmd.push.lease-duration=10s"
      })
  void rejectsConfigurationThatCouldBreakBoundedClaimsOrOutliveTheLease(String property) {
    context.withPropertyValues(property).run(result -> assertThat(result).hasFailed());
  }
}
