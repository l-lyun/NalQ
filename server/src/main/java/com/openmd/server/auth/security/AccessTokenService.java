package com.openmd.server.auth.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

public final class AccessTokenService {

	public static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(5);
	private final byte[] secret;
	private final Clock clock;

	private AccessTokenService(byte[] secret, Clock clock) {
		this.secret = secret;
		this.clock = clock;
	}

	public static AccessTokenService create(String base64Secret, Clock clock) {
		byte[] secret;
		try {
			secret = Base64.getDecoder().decode(base64Secret);
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Access token secret must be valid Base64", exception);
		}
		if (secret.length < 32) {
			throw new IllegalArgumentException("Access token secret must be at least 256 bits");
		}
		return new AccessTokenService(secret, clock);
	}

	public IssuedAccessToken issue(long userId, String sessionId) {
		try {
			Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
			Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_LIFETIME);
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(Long.toString(userId))
				.claim("sid", sessionId)
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(expiresAt))
				.build();
			JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build();
			SignedJWT jwt = new SignedJWT(header, claims);
			jwt.sign(new MACSigner(secret));
			return new IssuedAccessToken(jwt.serialize(), expiresAt);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to issue access token", exception);
		}
	}

	public AccessPrincipal verify(String token) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
				throw invalidCredential();
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Instant expiration = claims.getExpirationTime().toInstant();
			if (!expiration.isAfter(clock.instant())) {
				throw invalidCredential();
			}
			long userId = Long.parseLong(claims.getSubject());
			String sessionId = claims.getStringClaim("sid");
			if (sessionId == null || sessionId.isBlank()) {
				throw invalidCredential();
			}
			return new AccessPrincipal(userId, sessionId);
		} catch (BusinessException exception) {
			throw exception;
		} catch (Exception exception) {
			throw invalidCredential();
		}
	}

	private BusinessException invalidCredential() {
		return new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
	}
}
