package com.openmd.server.auth.dto.model;

public record RotatedRefreshToken(long userId, IssuedRefreshToken refreshToken) {
}
