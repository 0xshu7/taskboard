package com.example.taskboard.mail;

// ===== インポート =====
// 以下は Spring の部品。
// Value                 … application.yml の値をフィールドに読み込む
// ConditionalOnProperty … 設定の値によって、このクラスを使うかどうかを決める
// SimpleMailMessage     … 文字だけの簡単なメールを組み立てる入れ物
// JavaMailSender        … 実際に送信する道具。Spring が用意してくれる
// Component             … Spring に管理させるクラスです、という印

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 本物のメールを送るクラス。
 *
 * mail.mock=false のときだけ使われる。
 *
 * 送信の中身は Spring が用意する JavaMailSender に任せている。
 * 接続先やパスワードは application.yml の spring.mail.* に書いてあり、
 * その値を読んで JavaMailSender が組み立てられる。
 * このクラスがしているのは、宛先と文面を詰めて渡すことだけ。
 *
 * 送るのは文字だけのメール。
 * HTML や添付を使うなら SimpleMailMessage ではなく、
 * MimeMessage という別の組み立て方に変える必要がある。
 *
 * 呼び出す側（NotificationService）は、これが使われていることを知らない。
 * MailSender として受け取っているだけなので、
 * モックに切り替えてもコードは変わらない。
 */

// ===== アノテーション =====
// @Component … Spring に管理させるクラスです、という印。
//
// @ConditionalOnProperty … mail.mock が false のときだけ、このクラスを使う。
//   MockMailSender には havingValue="true" が付いているので、
//   どちらか一方だけが選ばれ、MailSender の中身が必ず1つに決まる。

@Component
@ConditionalOnProperty(name = "mail.mock", havingValue = "false")
public class RealMailSender implements MailSender {

	// ===== フィールド =====

	/**
	 * 実際に送信する道具。
	 *
	 * 自分で作らず、Spring から受け取る。
	 * 接続先やパスワードは application.yml に書いてあり、
	 * Spring がそれを読んで組み立てたものが渡される。
	 */
	private final JavaMailSender javaMailSender;

	/**
	 * 差出人のメールアドレス。
	 *
	 * 送信に使うアカウントと同じにする。
	 * 別のアドレスを差出人にすると、なりすましとみなされて拒否される。
	 *
	 * 同じ値が2か所に散らばらないよう、接続用の設定をそのまま読んでいる。
	 */
	@Value("${spring.mail.username}")
	private String from;

	// ===== コンストラクタ =====

	/**
	 * 必要な部品を受け取る。
	 * コンストラクタが1つだけなら、Spring が自動で値を渡す。
	 */
	public RealMailSender(JavaMailSender javaMailSender) {
		this.javaMailSender = javaMailSender;
	}

	// ===== メソッド =====

	/**
	 * メールを組み立てて送る。
	 *
	 * SimpleMailMessage は、文字だけのメールを組み立てる入れ物。
	 * 差出人・宛先・件名・本文を詰めて、JavaMailSender に渡すだけでよい。
	 *
	 * 送信に失敗すると例外が投げられる。
	 * ここでは受け止めていないので、呼び出した側へそのまま伝わる。
	 * NotificationService は、送信が終わってから通知日を記録する順序にしてあるため、
	 * 失敗した場合は「送った」と記録されずに済む。
	 *
	 * 送ったことをログに残しているが、本文は出していない。
	 * 本文には利用者のタスク名が含まれるため、ログに残さないほうがよい。
	 *
	 * @param to      送り先のメールアドレス
	 * @param subject 件名
	 * @param body    本文
	 */
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