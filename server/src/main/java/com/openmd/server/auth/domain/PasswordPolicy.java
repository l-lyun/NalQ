package com.openmd.server.auth.domain;

import java.util.regex.Pattern;

public final class PasswordPolicy {

	public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=\\S{8,64}$).+$";
	private static final Pattern PATTERN = Pattern.compile(REGEX);

	private PasswordPolicy() {
	}

	public static boolean isValid(String password) {
		return password != null && PATTERN.matcher(password).matches();
	}
}
