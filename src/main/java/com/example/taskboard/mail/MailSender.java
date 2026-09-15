package com.example.taskboard.mail;

// ===== インポート =====
// なし。Java の標準機能だけで書けるため、外から借りる部品は無い。

/**
 * メール送信の窓口。
 *
 * 呼び出す側は、このインターフェースだけを見る。
 * 実際に送るのか、ログに出すだけなのかは知らないまま使える。
 *
 * 中身は application.yml の mail.mock で入れ替わる。
 * true なら MockMailSender、false なら RealMailSender が渡される。
 * 呼び出す側のコードは1文字も変わらない。
 *
 * 引数を3つだけにしてあるのは、この形なら
 * どんな送り方でも実現できるため。
 * 添付や宛先の複数指定が必要になったときに、はじめて増やす。
 *
 * ProposalGenerator と同じ作りになっている。
 * 「窓口をインターフェースにして、中身を差し替える」は、
 * このアプリで繰り返し使っている形。
 */
public interface MailSender {

	/**
	 * メールを送る。
	 *
	 * 戻り値は無い。送れたかどうかを呼び出す側は受け取らない。
	 * 送信に失敗した場合は例外が投げられる。
	 *
	 * @param to      送り先のメールアドレス
	 * @param subject 件名
	 * @param body    本文
	 */
	void send(String to, String subject, String body);
}