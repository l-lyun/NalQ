package com.openmd.server.push.dto.request;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.openmd.server.push.domain.PushPermission;
import com.openmd.server.push.domain.PushPlatform;
import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.dto.command.RegisterPushDeviceCommand;
import com.openmd.server.push.dto.command.RevokePushDeviceCommand;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PushDeviceRequestRedactionTest {

  @Test
  void registrationRequestStringNeverContainsTheRawPushToken() {
    String token = "ExponentPushToken[secret-device-token]";
    PushDeviceRegistrationRequest request =
        new PushDeviceRegistrationRequest(
            "33333333-3333-4333-8333-333333333333",
            Instant.parse("2026-09-06T06:00:00Z"),
            0L,
            PushPlatform.IOS,
            PushProvider.EXPO,
            token,
            PushPermission.GRANTED);

    assertFalse(request.toString().contains(token));
  }

  @Test
  void internalCommandsNeverExposeRawCredentialsThroughRecordStrings() {
    String installationKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    String token = "ExponentPushToken[secret-device-token]";
    RegisterPushDeviceCommand registration =
        new RegisterPushDeviceCommand(
            "11111111-1111-4111-8111-111111111111",
            installationKey,
            "33333333-3333-4333-8333-333333333333",
            Instant.parse("2026-09-06T06:00:00Z"),
            0L,
            PushPlatform.IOS,
            PushProvider.EXPO,
            token,
            PushPermission.GRANTED);
    RevokePushDeviceCommand revocation =
        new RevokePushDeviceCommand(
            "11111111-1111-4111-8111-111111111111",
            installationKey,
            "44444444-4444-4444-8444-444444444444",
            Instant.parse("2026-09-06T06:00:00Z"),
            "55555555-5555-4555-8555-555555555555",
            1L);

    assertFalse(registration.toString().contains(installationKey));
    assertFalse(registration.toString().contains(token));
    assertFalse(revocation.toString().contains(installationKey));
  }
}
