package com.openmd.server.auth.application;

public interface VerificationEmailSender {

	void sendVerificationCode(String email, String code);
}
