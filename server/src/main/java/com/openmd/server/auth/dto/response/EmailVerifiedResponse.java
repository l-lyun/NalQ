package com.openmd.server.auth.dto.response;

public record EmailVerifiedResponse(boolean emailVerified, String signUpToken, String nextAction) {
}
