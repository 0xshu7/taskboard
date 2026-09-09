package com.example.taskboard.mail;



//メール送信の窓口。呼び出す側はこのインターフェースだけを見る
//実際に送るのは MockMailSender か、後で作る本物のクラス

public interface MailSender {

	void send(String to,String subject,String body);
	
}
