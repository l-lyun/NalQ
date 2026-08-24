package com.openmd.server.auth.integration.mail;

import com.openmd.server.auth.service.VerificationEmailSender;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.global.error.BusinessException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SpringMailVerificationEmailSender implements VerificationEmailSender {

	private final JavaMailSender mailSender;
	private final String from;

	public SpringMailVerificationEmailSender(JavaMailSender mailSender, String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void sendVerificationCode(String email, String code) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("OpenMD 이메일 인증 코드");
		message.setText("OpenMD 이메일 인증 코드는 " + code + " 입니다. 10분 안에 입력해 주세요.");
		try {
			mailSender.send(message);
		} catch (MailException exception) {
			throw new BusinessException(AuthErrorCode.EMAIL_DELIVERY_FAILED);
		}
	}
}
