package com.example.taskboard.mail;

// ===== インポート =====
// 以下は Spring の部品。
// ConditionalOnProperty … 設定の値によって、このクラスを使うかどうかを決める
// Component             … Spring に管理させるクラスです、という印

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 送ったふりをするクラス。実際には送らず、内容をコンソールに出す。
 *
 * mail.mock=true のときだけ使われる。
 *
 * 開発中に本物のメールが飛ぶと、確認のたびに受信箱が埋まる。
 * 送り先を間違えれば、無関係の人に届く。
 * 文面を試す段階では、ログに出るだけで十分なことが多い。
 *
 * 呼び出す側（NotificationService）は、これが使われていることを知らない。
 * MailSender として受け取っているだけなので、
 * 本物に切り替えてもコードは変わらない。
 */

// ===== アノテーション =====
// @Component … Spring に管理させるクラスです、という印。
//
// @ConditionalOnProperty … 設定の値が条件に合うときだけ、このクラスを使う。
//   name       … 見る設定の名前（application.yml の mail.mock）
//   havingValue … その値が何のときに使うか
//
//   RealMailSender には havingValue="false" が付いている。
//   どちらか一方だけが選ばれるので、MailSender の中身が必ず1つに決まる。
//   両方に同じ条件を書くと、どちらを使えばよいか決まらず、起動時に失敗する。

@Component
@ConditionalOnProperty(name = "mail.mock", havingValue = "true")
public class MockMailSender implements MailSender {

	// ===== メソッド =====

	/**
	 * 送らずに、内容をコンソールへ出す。
	 *
	 * @Override は「インターフェースで決められたメソッドを実装している」という印。
	 * 綴りや引数を間違えると、この印があることで気づける。
	 * 付けなくても動くが、付けておくと間違いが早く分かる。
	 *
	 * 区切り線を入れているのは、ログの中で見つけやすくするため。
	 * 起動時のログは量が多く、本文だけを出すと流れて見失う。
	 *
	 * @param to      送り先のメールアドレス
	 * @param subject 件名
	 * @param body    本文
	 */
	@Override
	public void send(String to, String subject, String body) {

		System.out.println("========== メール（モック） ==========");
		System.out.println("宛先  :" + to);
		System.out.println("件名  :" + subject);
		System.out.println("本文  :");
		System.out.println(body);
		System.out.println("=====================================");
	}
}