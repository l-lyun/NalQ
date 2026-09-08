package com.openmd.server.push.dto.model;

public record PreparedPushDelivery(PushDeliveryAttempt attempt, PushMessage message) {}
