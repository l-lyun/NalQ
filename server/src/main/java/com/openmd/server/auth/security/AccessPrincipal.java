package com.openmd.server.auth.security;

public record AccessPrincipal(long userId, String sessionId) {
}
