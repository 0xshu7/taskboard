package com.example.taskboard.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// mail.mock=false のときだけ使われる、本物のメールを送るクラス
@Component
@ConditionalOnProperty(name = "mail.mock", havingValue = "false")
public class RealMailSender implements MailSender {

	private final JavaMailSender javaMailSender;

	// 差出人。送信に使うアカウントと同じにする
	@Value("${spring.mail.username}")
	private String from;


	public RealMailSender(JavaMailSender javaMailSender) {
		this.javaMailSender = javaMailSender;
	}

	@Override
	public void send(String to, String subject, String body) {

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(body);

		javaMailSender.send(message);

		System.out.println("メール送信: " + to + " / " + subject);
	}
}