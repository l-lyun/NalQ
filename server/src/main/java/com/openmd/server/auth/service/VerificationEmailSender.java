package com.openmd.server.auth.service;

public interface VerificationEmailSender {

	void sendVerificationCode(String email, String code);
}
