package com.openmd.server.auth.integration.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.global.error.BusinessException;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SpringMailVerificationEmailSenderTest {

	private static final String FROM = "no-reply@nalq.test";
	private static final String TO = "learner@example.com";
	private static final String CODE = "A7K9M2";

	@Test
	void sendsUtf8PlainTextAndNalQHtmlAsMultipartAlternative() throws Exception {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		when(mailSender.createMimeMessage()).thenReturn(message);
		SpringMailVerificationEmailSender sender = new SpringMailVerificationEmailSender(mailSender, FROM);

		sender.sendVerificationCode(TO, CODE);

		verify(mailSender).send(message);
		assertEquals("[NalQ] 이메일 인증 코드", message.getSubject());
		assertEquals(TO, message.getRecipients(Message.RecipientType.TO)[0].toString());
		assertTrue(message.getFrom()[0].toString().contains("NalQ"));
		assertTrue(message.getContentType().toLowerCase().startsWith("multipart/alternative"));

		Multipart alternative = assertInstanceOf(Multipart.class, message.getContent());
		assertEquals(2, alternative.getCount());
		String plain = contentOf(alternative.getBodyPart(0));
		String html = contentOf(alternative.getBodyPart(1));
		assertTrue(alternative.getBodyPart(0).isMimeType("text/plain"));
		assertTrue(alternative.getBodyPart(1).isMimeType("text/html"));
		assertTrue(alternative.getBodyPart(0).getContentType().toLowerCase().contains("charset=utf-8"));
		assertTrue(alternative.getBodyPart(1).getContentType().toLowerCase().contains("charset=utf-8"));
		assertTrue(plain.contains("NalQ"));
		assertTrue(plain.contains("이메일 인증을 완료해 주세요"));
		assertTrue(plain.contains(CODE));
		assertTrue(plain.contains("10분"));
		assertTrue(plain.contains("요청하지 않았다면"));
		assertTrue(html.contains("NalQ"));
		assertTrue(html.contains("이메일 인증을 완료해 주세요"));
		assertTrue(html.contains(CODE));
		assertTrue(html.contains("10분"));
		assertTrue(html.contains("요청하지 않았다면"));
		assertTrue(html.contains("style=\""));
		assertFalse(html.contains("<img"));
		assertFalse(html.contains("<script"));
		assertFalse(html.contains("http://"));
		assertFalse(html.contains("https://"));
	}

	@Test
	void acceptsAConfiguredNalQDisplayAddress() throws Exception {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		when(mailSender.createMimeMessage()).thenReturn(message);
		SpringMailVerificationEmailSender sender = new SpringMailVerificationEmailSender(
			mailSender,
			"NalQ <nalq.service@gmail.com>"
		);

		sender.sendVerificationCode(TO, CODE);

		InternetAddress configuredFrom = assertInstanceOf(InternetAddress.class, message.getFrom()[0]);
		assertEquals("nalq.service@gmail.com", configuredFrom.getAddress());
		assertEquals("NalQ", configuredFrom.getPersonal());
	}

	@Test
	void deliversTheMultipartMessageThroughAnEmbeddedSmtpServer() throws Exception {
		GreenMail smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
		smtp.start();
		try {
			JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
			mailSender.setHost("127.0.0.1");
			mailSender.setPort(smtp.getSmtp().getPort());
			mailSender.setDefaultEncoding("UTF-8");
			SpringMailVerificationEmailSender sender = new SpringMailVerificationEmailSender(mailSender, FROM);

			sender.sendVerificationCode(TO, CODE);

			assertTrue(smtp.waitForIncomingEmail(5_000, 1));
			MimeMessage received = smtp.getReceivedMessages()[0];
			assertEquals("[NalQ] 이메일 인증 코드", received.getSubject());
			Multipart alternative = assertInstanceOf(Multipart.class, received.getContent());
			assertTrue(contentOf(alternative.getBodyPart(0)).contains(CODE));
			assertTrue(contentOf(alternative.getBodyPart(1)).contains(CODE));
		} finally {
			smtp.stop();
		}
	}

	@Test
	void convertsMailTransportFailureToAuth008() {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		when(mailSender.createMimeMessage()).thenReturn(message);
		doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(MimeMessage.class));
		SpringMailVerificationEmailSender sender = new SpringMailVerificationEmailSender(mailSender, FROM);

		BusinessException exception = assertThrows(BusinessException.class,
			() -> sender.sendVerificationCode(TO, CODE));

		assertEquals(AuthErrorCode.EMAIL_DELIVERY_FAILED, exception.getErrorCode());
		assertEquals(1, exception.getSuppressed().length);
		assertInstanceOf(MailSendException.class, exception.getSuppressed()[0]);
	}

	@Test
	void convertsMimeConstructionFailureToAuth008() {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		MimeMessage message = new FailingSubjectMimeMessage();
		when(mailSender.createMimeMessage()).thenReturn(message);
		SpringMailVerificationEmailSender sender = new SpringMailVerificationEmailSender(mailSender, FROM);

		BusinessException exception = assertThrows(BusinessException.class,
			() -> sender.sendVerificationCode(TO, CODE));

		assertEquals(AuthErrorCode.EMAIL_DELIVERY_FAILED, exception.getErrorCode());
	}

	private static String contentOf(BodyPart part) throws Exception {
		return part.getContent().toString();
	}

	private static final class FailingSubjectMimeMessage extends MimeMessage {

		private FailingSubjectMimeMessage() {
			super(Session.getInstance(new Properties()));
		}

		@Override
		public void setSubject(String subject, String charset) throws MessagingException {
			throw new MessagingException("cannot encode subject");
		}
	}
}
