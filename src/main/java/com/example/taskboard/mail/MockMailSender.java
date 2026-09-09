package com.example.taskboard.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// mail.mock=true のときだけ使われる、送ったふりをするクラス
// 実際には送らず、コンソールに内容を出す

@Component
@ConditionalOnProperty(name="mail.mock",havingValue="true")
public class MockMailSender implements MailSender{
	
	@Override
	public void send(String to,String subject,String body) {
		System.out.println("========== メール（モック） ==========");
		System.out.println("宛先  :"+ to);
		System.out.println("件名  :"+ subject);
		System.out.println("本文  :");
		System.out.println(body);
		System.out.println("=====================================");
		
	}

}
