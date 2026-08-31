package com.openmd.server.auth.util;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import java.text.Normalizer;

public final class NicknameNormalizer {

	private static final String NICKNAME_REGEX = "[가-힣A-Za-z0-9]{2,10}";

	private NicknameNormalizer() {
	}

	public static String normalize(String input) {
		String nickname = input == null ? "" : Normalizer.normalize(input, Normalizer.Form.NFC);
		if (!nickname.matches(NICKNAME_REGEX)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT);
		}
		return nickname;
	}
}
