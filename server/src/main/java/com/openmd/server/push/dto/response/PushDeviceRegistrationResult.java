package com.openmd.server.push.dto.response;

import com.openmd.server.push.domain.PushDeviceStatus;

public record PushDeviceRegistrationResult(
    String installationId,
    long revision,
    String bindingId,
    PushDeviceStatus status,
    long userId) {}
