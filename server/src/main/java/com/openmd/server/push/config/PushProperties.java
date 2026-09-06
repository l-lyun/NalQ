package com.openmd.server.push.config;

import java.net.URI;
import java.time.Duration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "openmd.push")
@Validated
public class PushProperties {

  private boolean registrationEnabled;
  private boolean deliveryEnabled;
  private boolean schedulerEnabled;
  @Min(1)
  @Max(50)
  private int batchSize = 50;

  @Min(1)
  @Max(500)
  private int retentionBatchSize = 500;

  @NotNull private Duration leaseDuration = Duration.ofSeconds(60);
  @NotNull private URI expoApiBase = URI.create("https://exp.host/--/api/v2/push/");
  private String expoAccessToken = "";

  public boolean isRegistrationEnabled() {
    return registrationEnabled;
  }

  public void setRegistrationEnabled(boolean registrationEnabled) {
    this.registrationEnabled = registrationEnabled;
  }

  public boolean isDeliveryEnabled() {
    return deliveryEnabled;
  }

  public void setDeliveryEnabled(boolean deliveryEnabled) {
    this.deliveryEnabled = deliveryEnabled;
  }

  public boolean isSchedulerEnabled() {
    return schedulerEnabled;
  }

  public void setSchedulerEnabled(boolean schedulerEnabled) {
    this.schedulerEnabled = schedulerEnabled;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public int getRetentionBatchSize() {
    return retentionBatchSize;
  }

  public void setRetentionBatchSize(int retentionBatchSize) {
    this.retentionBatchSize = retentionBatchSize;
  }

  public Duration getLeaseDuration() {
    return leaseDuration;
  }

  public void setLeaseDuration(Duration leaseDuration) {
    this.leaseDuration = leaseDuration;
  }

  @AssertTrue(message = "lease duration must exceed the 10 second provider deadline")
  public boolean isLeaseLongerThanProviderDeadline() {
    return leaseDuration != null && leaseDuration.compareTo(Duration.ofSeconds(10)) > 0;
  }

  public URI getExpoApiBase() {
    return expoApiBase;
  }

  public void setExpoApiBase(URI expoApiBase) {
    this.expoApiBase = expoApiBase;
  }

  public String getExpoAccessToken() {
    return expoAccessToken;
  }

  public void setExpoAccessToken(String expoAccessToken) {
    this.expoAccessToken = expoAccessToken;
  }
}
