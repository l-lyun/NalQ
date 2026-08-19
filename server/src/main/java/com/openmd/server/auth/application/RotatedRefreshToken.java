package com.openmd.server.auth.application;

public record RotatedRefreshToken(long userId, IssuedRefreshToken refreshToken) {
}
