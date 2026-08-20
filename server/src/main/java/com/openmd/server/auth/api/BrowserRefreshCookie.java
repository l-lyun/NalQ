package com.openmd.server.auth.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;

public final class BrowserRefreshCookie {

	private final String name;
	private final boolean secure;
	private final String sameSite;
	private final String path;
	private final Clock clock;

	public BrowserRefreshCookie(String name, boolean secure, String sameSite, String path, Clock clock) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Browser refresh cookie name must not be blank");
		}
		if (path == null || path.isBlank()) {
			throw new IllegalArgumentException("Browser refresh cookie path must not be blank");
		}
		this.name = name;
		this.secure = secure;
		this.sameSite = sameSite;
		this.path = path;
		this.clock = clock;
	}

	public String read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public ResponseCookie issue(String token, Instant expiresAt) {
		Duration maxAge = Duration.between(clock.instant(), expiresAt);
		if (maxAge.isNegative()) {
			maxAge = Duration.ZERO;
		}
		return base(token)
			.maxAge(maxAge)
			.build();
	}

	public ResponseCookie expire() {
		return base("")
			.maxAge(Duration.ZERO)
			.build();
	}

	private ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(secure)
			.sameSite(sameSite)
			.path(path);
	}
}
