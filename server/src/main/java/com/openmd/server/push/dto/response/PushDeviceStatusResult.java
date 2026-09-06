package com.openmd.server.push.dto.response;

import com.openmd.server.push.domain.PushDeviceStatus;
import com.openmd.server.push.domain.PushPlatform;

public record PushDeviceStatusResult(
    long revision,
    boolean belongsToCurrentUser,
    String bindingId,
    PushDeviceStatus status,
    PushPlatform platform) {}
