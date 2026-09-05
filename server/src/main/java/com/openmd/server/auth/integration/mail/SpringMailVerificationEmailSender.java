package com.openmd.server.auth.integration.mail;

import com.openmd.server.auth.service.VerificationEmailSender;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.global.error.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpringMailVerificationEmailSender implements VerificationEmailSender {
	private static final String SUBJECT = "[NalQ] 이메일 인증 코드";
	private static final String SENDER_NAME = "NalQ";
	private static final Logger log = LoggerFactory.getLogger(SpringMailVerificationEmailSender.class);

	private final JavaMailSender mailSender;
	private final String from;

	public SpringMailVerificationEmailSender(JavaMailSender mailSender, String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void sendVerificationCode(String email, String code) {
		try {
			var message = mailSender.createMimeMessage();
			var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
			helper.setFrom(senderAddress());
			helper.setTo(email);
			helper.setSubject(SUBJECT);

			var plainPart = new MimeBodyPart();
			plainPart.setText(plainText(code), StandardCharsets.UTF_8.name());
			var htmlPart = new MimeBodyPart();
			htmlPart.setContent(htmlText(code), "text/html; charset=UTF-8");
			var alternative = new MimeMultipart("alternative");
			alternative.addBodyPart(plainPart);
			alternative.addBodyPart(htmlPart);
			message.setContent(alternative);
			message.saveChanges();
			mailSender.send(message);
		} catch (MailException | MessagingException | UnsupportedEncodingException exception) {
			log.warn("Verification email delivery failed cause={}", rootCauseType(exception));
			var failure = new BusinessException(AuthErrorCode.EMAIL_DELIVERY_FAILED);
			failure.addSuppressed(exception);
			throw failure;
		}
	}

	private static String rootCauseType(Throwable failure) {
		Throwable cause = failure;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause.getClass().getSimpleName();
	}

	private InternetAddress senderAddress() throws MessagingException, UnsupportedEncodingException {
		var sender = new InternetAddress(from, true);
		if (sender.getPersonal() == null || sender.getPersonal().isBlank()) {
			sender.setPersonal(SENDER_NAME, StandardCharsets.UTF_8.name());
		}
		return sender;
	}

	private static String plainText(String code) {
		return """
			NalQ

			이메일 인증을 완료해 주세요

			NalQ 회원가입을 위해 아래 인증 코드를 입력해 주세요.

			%s

			이 코드는 발급 후 10분 동안 유효합니다.
			본인이 요청하지 않았다면 이 메일을 무시해 주세요.
			""".formatted(code);
	}

	private static String htmlText(String code) {
		return """
			<!doctype html>
			<html lang="ko">
			<head>
			  <meta charset="UTF-8">
			  <meta name="viewport" content="width=device-width, initial-scale=1.0">
			  <title>NalQ 이메일 인증 코드</title>
			</head>
			<body style="margin:0;padding:0;background-color:#f5f7fa;color:#202124;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans KR',Arial,sans-serif;">
			  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background-color:#f5f7fa;">
			    <tr>
			      <td align="center" style="padding:40px 16px;">
			        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:560px;background-color:#ffffff;border:1px solid #e5e8eb;border-radius:16px;">
			          <tr>
			            <td style="padding:32px 32px 12px;">
			              <div style="font-size:24px;line-height:32px;font-weight:800;letter-spacing:-0.5px;color:#e14d00;">NalQ</div>
			            </td>
			          </tr>
			          <tr>
			            <td style="padding:8px 32px 32px;">
			              <h1 style="margin:0 0 12px;font-size:24px;line-height:34px;font-weight:700;letter-spacing:-0.4px;color:#202124;">이메일 인증을 완료해 주세요</h1>
			              <p style="margin:0 0 24px;font-size:15px;line-height:24px;color:#5f6368;">NalQ 회원가입을 위해 아래 인증 코드를 입력해 주세요.</p>
			              <div style="margin:0 0 24px;padding:20px 16px;border-radius:12px;background-color:#fff2ec;text-align:center;font-family:'SFMono-Regular',Consolas,'Liberation Mono',monospace;font-size:30px;line-height:40px;font-weight:700;letter-spacing:6px;color:#a83a00;">%s</div>
			              <p style="margin:0;font-size:14px;line-height:22px;color:#5f6368;">이 코드는 발급 후 <strong style="color:#202124;">10분</strong> 동안 유효합니다.</p>
			            </td>
			          </tr>
			          <tr>
			            <td style="padding:20px 32px;border-top:1px solid #edf0f2;font-size:12px;line-height:19px;color:#8b95a1;">본인이 요청하지 않았다면 이 메일을 무시해 주세요.</td>
			          </tr>
			        </table>
			      </td>
			    </tr>
			  </table>
			</body>
			</html>
			""".formatted(code);
	}
}
