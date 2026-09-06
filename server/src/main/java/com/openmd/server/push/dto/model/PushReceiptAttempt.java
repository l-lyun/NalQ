package com.openmd.server.push.dto.model;

public record PushReceiptAttempt(long deliveryId, String attemptId, String ticketId) {}
