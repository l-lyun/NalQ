package com.openmd.server.auth.application;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VerificationCodeDigest {

	private final byte[] secret;

	public VerificationCodeDigest(byte[] secret) {
		if (secret == null || secret.length < 32) {
			throw new IllegalArgumentException("Email verification HMAC secret must be at least 32 bytes");
		}
		this.secret = secret.clone();
	}

	public String create(long userId, String code) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			String value = "EMAIL_VERIFICATION:" + userId + ":" + code;
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to calculate verification digest", exception);
		}
	}
}
