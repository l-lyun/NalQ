package com.openmd.server.auth.dto.model;

import java.time.Instant;

public record SignUpCredential(String displayEmail, String normalizedEmail, Instant verifiedAt) {
}
