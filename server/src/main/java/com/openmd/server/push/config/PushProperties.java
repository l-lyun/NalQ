package com.openmd.server.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openmd.push")
public class PushProperties {

  private boolean registrationEnabled;
  private boolean deliveryEnabled;

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
}
